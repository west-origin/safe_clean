package kr.ansimclean.api.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.sms.SmsSender;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.common.web.RateLimiter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Supabase Send SMS Hook: Standard Webhooks 서명, 허용 목록(우리 서버가 시작한 요청만), 문자 본문 */
class SmsHookServiceTest {
    static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");
    static final UUID USER = UUID.fromString("33333333-3333-3333-3333-333333333333");
    static final String SECRET = "v1,whsec_" + Base64.getEncoder().encodeToString("test-hook-secret-0123456789".getBytes());

    CustomerPhoneStore store = mock(CustomerPhoneStore.class);
    SmsSender sms = mock(SmsSender.class);
    RateLimiter limiter = mock(RateLimiter.class);
    SmsHookService service = new SmsHookService(SECRET, store, sms, limiter, new AppClock(Clock.fixed(NOW, ZoneOffset.UTC)));

    String body(String phoneField) {
        return "{\"user\":{\"id\":\"" + USER + "\",\"phone\":\"\"," + phoneField + "},\"sms\":{\"otp\":\"654321\"}}";
    }

    String sig(String id, long ts, String body) {
        return "v1," + Base64.getEncoder().encodeToString(service.sign(id + "." + ts + "." + body));
    }

    @Test
    void 우리가_시작한_요청이면_새_번호로_문자를_보낸다() {
        when(store.challenge(USER)).thenReturn(Optional.of(new CustomerPhoneStore.Challenge("+821012345678", NOW.minusSeconds(1), 0)));
        String b = body("\"new_phone\":\"821012345678\"");
        long ts = NOW.getEpochSecond();
        SmsHookService.Result r = service.handle("msg_1", String.valueOf(ts), "v1,AAAA " + sig("msg_1", ts, b), b);
        assertThat(r.status()).isEqualTo(200);
        verify(sms).send("+821012345678", "[안심클린] 인증번호 654321 (3분 안에 입력해주세요)");
    }

    @Test
    void 서명이_틀리거나_오래되면_401_이고_보내지_않는다() {
        String b = body("\"new_phone\":\"821012345678\"");
        long ts = NOW.getEpochSecond();
        assertThat(service.handle("msg_1", String.valueOf(ts), "v1,AAAA", b).status()).isEqualTo(401);
        long old = NOW.minusSeconds(600).getEpochSecond();
        assertThat(service.handle("msg_1", String.valueOf(old), sig("msg_1", old, b), b).status()).isEqualTo(401);
        // 본문을 바꾸면 같은 서명이 통하지 않는다
        assertThat(service.handle("msg_1", String.valueOf(ts), sig("msg_1", ts, b), b.replace("654321", "000000")).status()).isEqualTo(401);
        verify(sms, never()).send(anyString(), anyString());
    }

    @Test
    void 우리_서버가_시작하지_않은_발송은_403() {
        // Supabase /otp 를 공개 키로 직접 부른 경우 — 기록이 없다
        when(store.challenge(USER)).thenReturn(Optional.empty());
        String b = body("\"new_phone\":\"821012345678\"");
        long ts = NOW.getEpochSecond();
        assertThat(service.handle("m", String.valueOf(ts), sig("m", ts, b), b).status()).isEqualTo(403);
        // 기록은 있지만 다른 번호
        when(store.challenge(USER)).thenReturn(Optional.of(new CustomerPhoneStore.Challenge("+821099999999", NOW, 0)));
        assertThat(service.handle("m", String.valueOf(ts), sig("m", ts, b), b).status()).isEqualTo(403);
        verify(sms, never()).send(anyString(), anyString());
    }

    @Test
    void 번호별_한도와_발송_실패는_Supabase_형식의_오류() {
        when(store.challenge(USER)).thenReturn(Optional.of(new CustomerPhoneStore.Challenge("+821012345678", NOW, 0)));
        String b = body("\"phone_change\":\"821012345678\"");
        long ts = NOW.getEpochSecond();
        doThrow(new SmsSender.SmsException("down", null)).when(sms).send(anyString(), anyString());
        assertThat(service.handle("m", String.valueOf(ts), sig("m", ts, b), b).status()).isEqualTo(502);
        doThrow(new ApiException(ErrorCode.RATE_LIMITED)).when(limiter).check("sms-hook-phone", "+821012345678", CustomerPhoneService.REQUESTS_PER_HOUR);
        assertThat(service.handle("m", String.valueOf(ts), sig("m", ts, b), b).status()).isEqualTo(429);
    }

    @Test
    void 비밀이_없으면_Hook_은_꺼져_있다() {
        assertThat(new SmsHookService("", store, sms, limiter, AppClock.system()).enabled()).isFalse();
        assertThat(service.enabled()).isTrue();
    }
}
