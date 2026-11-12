package kr.ansimclean.api.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.supabase.SupabaseAuthClient;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.common.web.RateLimiter;
import kr.ansimclean.api.profile.FirstCleaningService;
import kr.ansimclean.api.profile.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 고객 휴대폰 인증 규칙 (Supabase phone_change 위의 서버 정책). Supabase·DB 는 가짜.
 * 번호는 개발용 테스트 번호 010-0000-0001 (docs/auth/phone-otp.md).
 */
class CustomerPhoneServiceTest {
    static final UUID USER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final String LOCAL = "00000001";
    static final String E164 = "+821000000001";
    static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");

    SupabaseAuthClient supabase = mock(SupabaseAuthClient.class);
    CustomerPhoneStore store = mock(CustomerPhoneStore.class);
    TransactionTemplate tx = mock(TransactionTemplate.class);
    ProfileService profiles = mock(ProfileService.class);
    FirstCleaningService first = mock(FirstCleaningService.class);
    RateLimiter limiter = mock(RateLimiter.class);
    CustomerPhoneService service = new CustomerPhoneService(supabase, store, tx, profiles, first, limiter,
            new AppClock(Clock.fixed(NOW, ZoneOffset.UTC)));

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // 트랜잭션 묶음은 그냥 바로 실행
        doAnswer(inv -> {
            ((Consumer<TransactionStatus>) inv.getArgument(0)).accept(null);
            return null;
        }).when(tx).executeWithoutResult(any());
        when(store.confirmedPhone(any(), any())).thenReturn(Optional.empty());
        when(store.challenge(USER)).thenReturn(Optional.empty());
        when(first.eligibility(USER)).thenReturn(new FirstCleaningService.Eligibility(true, true, "eligible", false));
    }

    @Test
    void 요청은_기록을_먼저_남기고_사용자_토큰으로_Supabase_에_보낸다() {
        CustomerPhoneService.OtpSent sent = service.request(USER, "jwt", LOCAL, "1.2.3.4");
        assertThat(sent).isEqualTo(new CustomerPhoneService.OtpSent(180, 60, false));
        var order = org.mockito.Mockito.inOrder(store, supabase);
        order.verify(store).startChallenge(USER, E164, NOW); // Hook 이 이 기록을 보고 발송을 허용한다
        order.verify(supabase).requestPhoneChange("jwt", E164);
        verify(limiter).check("customer-sms-user", USER.toString(), CustomerPhoneService.REQUESTS_PER_HOUR);
        verify(limiter).check("customer-sms-phone", E164, CustomerPhoneService.REQUESTS_PER_HOUR);
    }

    @Test
    void 재요청이_60초_안이면_Supabase_를_부르지_않고_OTP_RATE_LIMITED() {
        when(store.challenge(USER)).thenReturn(Optional.of(new CustomerPhoneStore.Challenge(E164, NOW.minusSeconds(30), 0)));
        assertThatThrownBy(() -> service.request(USER, "jwt", LOCAL, null))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.OTP_RATE_LIMITED);
        verify(supabase, never()).requestPhoneChange(anyString(), anyString());
    }

    @Test
    void 재요청은_60초가_지나면_다시_보낸다() {
        when(store.challenge(USER)).thenReturn(Optional.of(new CustomerPhoneStore.Challenge(E164, NOW.minusSeconds(61), 2)));
        service.request(USER, "jwt", LOCAL, null);
        verify(store).startChallenge(USER, E164, NOW); // 틀린 횟수도 0 으로 다시
        verify(supabase).requestPhoneChange("jwt", E164);
    }

    @Test
    void 발송에_실패하면_방금_기록을_지운다() {
        doThrow(new ApiException(ErrorCode.UPSTREAM)).when(supabase).requestPhoneChange("jwt", E164);
        assertThatThrownBy(() -> service.request(USER, "jwt", LOCAL, null)).isInstanceOf(ApiException.class);
        verify(store).dropChallenge(USER, NOW);
    }

    @Test
    void 이미_인증한_고객은_다시_요청할_수_없다() {
        when(store.hasProof(USER)).thenReturn(true);
        assertThatThrownBy(() -> service.request(USER, "jwt", LOCAL, null))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.VALIDATION);
        verify(supabase, never()).requestPhoneChange(anyString(), anyString());
    }

    @Test
    void Supabase_에서_이미_확인된_번호는_문자_없이_기록만() {
        when(store.confirmedPhone(USER, Instant.EPOCH)).thenReturn(Optional.of(E164));
        assertThat(service.request(USER, "jwt", LOCAL, null).verified()).isTrue();
        verify(store).saveProof(USER, E164, NOW);
        verify(supabase, never()).requestPhoneChange(anyString(), anyString());
    }

    @Test
    void 확인_성공은_새_세션을_끊고_auth_users_를_근거로_기록한다() {
        Instant requested = NOW.minusSeconds(20);
        when(store.challenge(USER)).thenReturn(Optional.of(new CustomerPhoneStore.Challenge(E164, requested, 0)));
        when(supabase.verifyPhoneChange(E164, "123456")).thenReturn(new SupabaseAuthClient.PhoneChangeResult(true, USER, "extra-session"));
        when(store.confirmedPhone(USER, requested)).thenReturn(Optional.of(E164));

        FirstCleaningService.Eligibility res = service.verify(USER, LOCAL, "123456", "1.2.3.4");

        assertThat(res.eligible()).isTrue();
        verify(supabase).logout("extra-session");
        verify(profiles).lockLive(USER);
        verify(store).saveProof(USER, E164, NOW);
        verify(store).dropChallenge(USER);
    }

    @Test
    void 틀리면_횟수를_세고_5번째에_요청을_지운다() {
        when(store.challenge(USER)).thenReturn(Optional.of(new CustomerPhoneStore.Challenge(E164, NOW.minusSeconds(20), 3)));
        when(supabase.verifyPhoneChange(E164, "000000")).thenReturn(new SupabaseAuthClient.PhoneChangeResult(false, null, null));
        when(store.countFailure(USER)).thenReturn(4);
        assertThatThrownBy(() -> service.verify(USER, LOCAL, "000000", null))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.OTP_INVALID);
        verify(store, never()).dropChallenge(USER);

        when(store.countFailure(USER)).thenReturn(5);
        assertThatThrownBy(() -> service.verify(USER, LOCAL, "000000", null))
                .hasMessageContaining("너무 여러 번");
        verify(store).dropChallenge(USER);
        verify(store, never()).saveProof(any(), any(), any());
    }

    @Test
    void 이미_5번_틀린_요청은_Supabase_에_묻지_않는다() {
        when(store.challenge(USER)).thenReturn(Optional.of(new CustomerPhoneStore.Challenge(E164, NOW.minusSeconds(20), 5)));
        assertThatThrownBy(() -> service.verify(USER, LOCAL, "123456", null)).hasMessageContaining("너무 여러 번");
        verify(supabase, never()).verifyPhoneChange(anyString(), anyString());
    }

    @Test
    void 요청_뒤_3분이_지나면_OTP_EXPIRED() {
        when(store.challenge(USER)).thenReturn(Optional.of(new CustomerPhoneStore.Challenge(E164, NOW.minus(Duration.ofMinutes(3)).minusSeconds(1), 0)));
        assertThatThrownBy(() -> service.verify(USER, LOCAL, "123456", null))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.OTP_EXPIRED);
        verify(supabase, never()).verifyPhoneChange(anyString(), anyString());
    }

    @Test
    void 요청하지_않았거나_다른_번호면_VALIDATION() {
        assertThatThrownBy(() -> service.verify(USER, LOCAL, "123456", null))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.VALIDATION);
        when(store.challenge(USER)).thenReturn(Optional.of(new CustomerPhoneStore.Challenge("+821000000002", NOW, 0)));
        assertThatThrownBy(() -> service.verify(USER, LOCAL, "123456", null))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.VALIDATION);
        verify(supabase, never()).verifyPhoneChange(anyString(), anyString());
    }

    @Test
    void Supabase_성공이어도_내_계정에_번호가_없으면_기록하지_않는다() {
        Instant requested = NOW.minusSeconds(20);
        when(store.challenge(USER)).thenReturn(Optional.of(new CustomerPhoneStore.Challenge(E164, requested, 0)));
        when(supabase.verifyPhoneChange(E164, "123456")).thenReturn(new SupabaseAuthClient.PhoneChangeResult(true, UUID.randomUUID(), "s"));
        when(store.countFailure(USER)).thenReturn(1);
        assertThatThrownBy(() -> service.verify(USER, LOCAL, "123456", null))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.OTP_INVALID);
        verify(supabase).logout("s");
        verify(store, never()).saveProof(any(), any(), any());
        verify(limiter).check(eq("customer-sms-verify"), eq(USER.toString()), any(Integer.class));
    }
}
