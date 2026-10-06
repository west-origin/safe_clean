package kr.ansimclean.api.auth;

import kr.ansimclean.api.auth.dto.AuthResponse;
import kr.ansimclean.api.auth.dto.LoginRequest;
import kr.ansimclean.api.auth.dto.SignupRequest;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.supabase.SupabaseAuthClient;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.profile.Profile;
import kr.ansimclean.api.profile.ProfileService;
import kr.ansimclean.api.profile.dto.UserResponse;
import kr.ansimclean.api.schedule.AvailabilityService;
import kr.ansimclean.api.support.TermsService;
import kr.ansimclean.api.common.web.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 스프링 없이 AuthService 를 new 로 만든다. mock 은 정하지 않은 호출에 null·false·0·빈 값을 돌려주고, 여러 테스트가 그 기본값에 기댄다
// (예: profiles.isDeleted=false, guard.blockReason=null = 막지 않음).
// 예외: RateLimiter 는 진짜 객체(고정 시계)라 한도가 실제로 쌓인다. TransactionTemplate 은 가짜 트랜잭션 관리자라 콜백을 그냥 실행한다(롤백 없음).
// never() = 한 번도 없었는지, inOrder = 호출 순서까지 확인한다.
/** 가입·로그인·탈퇴의 순서와 보상 (BACKEND_PLAN.md §5.1·§7.3). 협력자는 RateLimiter 말고 전부 mock */
class AuthServiceTest {
    static final String PHONE = "+821012345678"; // LoginRequest 의 "12345678" 이 toE164 로 바뀐 값
    static final UUID USER = UUID.randomUUID();
    static final SupabaseAuthClient.Session SESSION = new SupabaseAuthClient.Session("a", "r", 3600, USER); // Supabase 로그인 결과 가짜 (access "a", refresh "r")

    OtpService otp = mock(OtpService.class);
    SupabaseAuthClient supabase = mock(SupabaseAuthClient.class);
    SupabaseUserLookup lookup = mock(SupabaseUserLookup.class);
    ProfileService profiles = mock(ProfileService.class);
    kr.ansimclean.api.push.PushService push = mock(kr.ansimclean.api.push.PushService.class);
    TermsService terms = mock(TermsService.class);
    kr.ansimclean.api.support.SupportService support = mock(kr.ansimclean.api.support.SupportService.class);
    kr.ansimclean.api.profile.ManagerPenaltyService penalties = mock(kr.ansimclean.api.profile.ManagerPenaltyService.class);
    AvailabilityService availability = mock(AvailabilityService.class);
    RateLimiter rateLimiter = new RateLimiter(new AppClock(Clock.fixed(Instant.parse("2026-09-15T05:00:00Z"), AppClock.ZONE)));
    AccountDeletionGuard guard = mock(AccountDeletionGuard.class); // 탈퇴 가드 하나만 둔다 (blockReason 기본값 null = 막지 않음)
    OutboxService outbox = mock(OutboxService.class);
    kr.ansimclean.api.profile.SessionRevocationService revocations = mock(kr.ansimclean.api.profile.SessionRevocationService.class);
    AuthService service;

    /** session_id claim 만 든 가짜 access JWT (서명 없음 — Session.sessionId() 는 payload 만 읽는다) */
    static String jwtWithSession(String sid) {
        java.util.Base64.Encoder b64 = java.util.Base64.getUrlEncoder().withoutPadding();
        return b64.encodeToString("{\"alg\":\"none\"}".getBytes()) + "." + b64.encodeToString(("{\"session_id\":\"" + sid + "\"}").getBytes()) + ".sig";
    }

    /**
     * 매 테스트 전(@BeforeEach) 기본값: 토큰 "tok" = 고객 번호 PHONE 의 인증 건(peek·consume 통과), USER = 살아 있는 고객 프로필,
     * 로그인(passwordGrant)은 SESSION. 각 테스트가 필요한 것만 when 으로 덮어쓴다
     */
    @BeforeEach
    void setUp() {
        service = new AuthService(otp, supabase, lookup, profiles, terms, availability, rateLimiter,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), outbox, List.of(guard), push, revocations, support, penalties);
        // 만료 검사는 OtpService(여기서는 mock)가 하므로 시각 값은 의미가 없다
        PhoneVerification v = PhoneVerification.issue(PHONE, "customer", "hash", Instant.EPOCH, Instant.MAX);
        when(otp.peek("tok")).thenReturn(v);
        when(otp.consume("tok", "customer")).thenReturn(v);
        Profile p = Profile.create(USER, "customer", "김하늘", "하늘", PHONE);
        when(profiles.requireLive(USER)).thenReturn(p);
        when(profiles.findLive(USER)).thenReturn(Optional.of(p));
        when(profiles.userResponse(p)).thenReturn(new UserResponse(USER, "customer", "김하늘", false, "address"));
        when(supabase.passwordGrant(eq(PHONE), anyString())).thenReturn(SESSION);
    }

    @Test
    void 가입은_토큰_확인_토큰_소비_사용자_생성_프로필_저장_로그인_순서다() {
        when(lookup.findIdByPhone(PHONE)).thenReturn(Optional.empty());
        when(supabase.createUser(PHONE, "password1")).thenReturn(USER);

        AuthResponse res = service.signup(new SignupRequest("tok", " 김하늘 ", "하늘", "password1")); // 이름 앞뒤 공백은 저장 전에 잘린다

        // 토큰 소비(consume)가 Supabase 사용자 생성보다 먼저여야 같은 토큰의 두 요청이 사용자를 두 번 만들지 못한다
        InOrder order = inOrder(otp, supabase, profiles);
        order.verify(otp).peek("tok");
        order.verify(otp).consume("tok", "customer");
        order.verify(supabase).createUser(PHONE, "password1");
        order.verify(profiles).createForSignup(USER, "customer", "김하늘", "하늘", PHONE);
        order.verify(supabase).passwordGrant(PHONE, "password1");
        assertThat(res.accessToken()).isEqualTo("a");
        assertThat(res.user().signupStep()).isEqualTo("address");
    }

    @Test
    void 이전_시도의_Supabase_사용자가_남아_있으면_비밀번호만_바꿔_재사용한다() {
        // 번호로 찾은 사용자가 있고 profiles.isDeleted 는 mock 기본값 false — 탈퇴 잔재가 아니라 가입 도중 실패한 잔재
        when(lookup.findIdByPhone(PHONE)).thenReturn(Optional.of(USER));
        service.signup(new SignupRequest("tok", "김하늘", "하늘", "password1"));
        verify(supabase).updatePassword(USER, "password1");
        verify(supabase, never()).createUser(any(), any());
    }

    @Test
    void 탈퇴_잔재_Supabase_사용자는_지우고_새_id_로_가입한다() {
        // 탈퇴 때 Supabase 삭제가 실패해 옛 사용자가 남은 경우 — 옛 id 를 다시 쓰면 익명화된 프로필·예약 이력이 되살아난다
        UUID fresh = UUID.randomUUID();
        when(lookup.findIdByPhone(PHONE)).thenReturn(Optional.of(USER));
        when(profiles.isDeleted(USER)).thenReturn(true);
        when(supabase.createUser(PHONE, "password1")).thenReturn(fresh);
        Profile p = Profile.create(fresh, "customer", "김하늘", "하늘", PHONE);
        when(profiles.requireLive(fresh)).thenReturn(p);
        when(profiles.userResponse(p)).thenReturn(new UserResponse(fresh, "customer", "김하늘", false, "address"));
        when(supabase.passwordGrant(eq(PHONE), anyString())).thenReturn(new SupabaseAuthClient.Session("a", "r", 3600, fresh));

        service.signup(new SignupRequest("tok", "김하늘", "하늘", "password1"));

        verify(supabase).deleteUser(USER);
        verify(supabase, never()).updatePassword(any(), any());
        verify(profiles).createForSignup(fresh, "customer", "김하늘", "하늘", PHONE);
    }

    @Test
    void 프로필_저장이_실패하면_방금_만든_Supabase_사용자를_지운다() {
        // 준비: 새로 만든 사용자 + DB 저장 실패 → 확인: 원래 오류가 그대로 나오고, 되돌리기(보상)로 그 사용자를 지운다
        when(lookup.findIdByPhone(PHONE)).thenReturn(Optional.empty());
        when(supabase.createUser(PHONE, "password1")).thenReturn(USER);
        when(profiles.createForSignup(any(), any(), any(), any(), any())).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> service.signup(new SignupRequest("tok", "김하늘", "하늘", "password1"))).hasMessage("db down");
        verify(supabase).deleteUser(USER);
    }

    @Test
    void 비밀번호가_8자_미만이면_PASSWORD_TOO_SHORT_이고_아무것도_하지_않는다() {
        // assertThatThrownBy(…).extracting(…): 던져진 예외에서 ErrorCode 를 꺼내 비교한다
        assertThatThrownBy(() -> service.signup(new SignupRequest("tok", "김하늘", "하늘", "short")))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.PASSWORD_TOO_SHORT);
        verify(otp, never()).peek(any()); // 길이 검사가 토큰을 보기 전에 끝나 토큰이 소비되지 않는다
        verify(otp, never()).consume(any(), any());
    }

    @Test
    void 매니저_토큰이면_닉네임_없이_저장한다() {
        // 역할은 요청이 아니라 인증 건(토큰)이 안다 — "mtok" 을 매니저 인증 건으로 두고, 닉네임을 보내도 null 로 저장되는지 본다
        PhoneVerification v = PhoneVerification.issue(PHONE, "manager", "hash", Instant.EPOCH, Instant.MAX);
        when(otp.peek("mtok")).thenReturn(v);
        when(otp.consume("mtok", "manager")).thenReturn(v);
        when(lookup.findIdByPhone(PHONE)).thenReturn(Optional.empty());
        when(supabase.createUser(PHONE, "password1")).thenReturn(USER);
        service.signup(new SignupRequest("mtok", "김매니저", "무시됨", "password1"));
        verify(profiles).createForSignup(USER, "manager", "김매니저", null, PHONE);
    }

    @Test
    void 로그인은_역할이_다르면_LOGIN_FAILED_로_구분하지_않는다() {
        // setUp 의 USER 는 고객 — 매니저 앱으로 로그인하면 번호·비밀번호 오류와 같은 LOGIN_FAILED, 고객 앱이면 성공
        assertThatThrownBy(() -> service.login(new LoginRequest("12345678", "password1", "manager"), null))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.LOGIN_FAILED);
        AuthResponse ok = service.login(new LoginRequest("12345678", "password1", "customer"), null);
        assertThat(ok.user().role()).isEqualTo("customer");
    }

    @Test
    void 로그인_시도가_시간당_10회를_넘으면_잠긴다() { // RateLimiter 는 성공·실패를 가리지 않고 시도를 센다
        // 틀린 비밀번호로 한도(10회)만큼 실패한 뒤 → 맞는 비밀번호도 한도에 걸린다 (시계가 고정이라 1시간 창이 지나지 않는다)
        when(supabase.passwordGrant(eq(PHONE), eq("wrong"))).thenThrow(new ApiException(ErrorCode.LOGIN_FAILED));
        for (int i = 0; i < RateLimiter.LOGIN_PER_HOUR; i++) {
            assertThatThrownBy(() -> service.login(new LoginRequest("12345678", "wrong", "customer"), null))
                    .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.LOGIN_FAILED);
        }
        assertThatThrownBy(() -> service.login(new LoginRequest("12345678", "password1", "customer"), null))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.RATE_LIMITED); // 로그인 한도는 OTP 코드가 아니다
    }

    // 첫 조회는 탈퇴(deleteAccount 의 lockLive)와 같은 행 잠금(FOR UPDATE)이다 — 잠그지 않고 읽으면 겹친 탈퇴가 익명화를 커밋한 뒤
    // 옛 값으로 이름·번호·탈퇴 표시를 되돌려 쓸 수 있었다 (문제 목록 28번)
    @Test
    void 가입_완료는_약관_기록_후_매니저면_기본_근무시간을_만든다() {
        // setUp 의 고객 프로필 대신 아직 가입을 끝내지 않은 매니저 프로필을 돌려주게 바꾼다
        Profile m = Profile.create(USER, "manager", "김매니저", null, PHONE);
        when(profiles.lockLive(USER)).thenReturn(m);
        when(profiles.completeSignup(USER, false)).thenReturn(m);
        when(profiles.userResponse(m)).thenReturn(new UserResponse(USER, "manager", "김매니저", true, null));

        var res = service.completeSignup(USER, List.of("service", "privacy", "location", "recording"));
        InOrder order = inOrder(terms, profiles, availability);
        order.verify(profiles).lockLive(USER); // 탈퇴와 줄을 선 뒤에야 기록을 쓴다
        order.verify(terms).recordAgreements(USER, "manager", List.of("service", "privacy", "location", "recording"));
        order.verify(profiles).completeSignup(USER, false); // 마케팅 동의 없음 → 토글 꺼짐
        order.verify(availability).createDefaultHours(USER);
        assertThat(res.profileComplete()).isTrue();
    }

    // 탈퇴와 겹침을 단위로 흉내 낸다: 탈퇴가 먼저 잠그고 익명화를 커밋하면, 기다리던 가입 완료의 잠금 조회는 살아 있는 행을 찾지 못해 NOT_FOUND 다
    // (Postgres 는 기다린 뒤 조건 'deleted_at is null' 을 새 값으로 다시 본다). 그러면 약관 동의·완료 표시·알림 설정·기본 근무시간을 하나도 쓰지 않는다 —
    // 예전에는 잠그지 않은 조회라 여기서 멈추지 않고 탈퇴한 계정을 되살렸다 (문제 목록 28번)
    @Test
    void 가입_완료가_탈퇴_뒤에_잠금을_얻으면_아무것도_쓰지_않는다() {
        when(profiles.lockLive(USER)).thenThrow(ApiException.notFound());
        assertThatThrownBy(() -> service.completeSignup(USER, List.of("service", "privacy", "marketing")))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.NOT_FOUND);
        verify(terms, never()).recordAgreements(any(), any(), any());
        verify(profiles, never()).completeSignup(any(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(availability, never()).createDefaultHours(any());
    }

    @Test
    void 토큰_갱신은_탈퇴한_사용자면_UNAUTHENTICATED_아니면_새_세션과_사용자() {
        // "a2" 는 JWT 모양이 아니라 sessionId() 가 null — revocations.isRevoked 는 mock 기본값 false 라 통과한다.
        // 끝부분은 로그아웃 확인: 서버의 끊은 세션 목록 기록(revoke)과 Supabase 로그아웃이 둘 다 불리는지 본다 (순서는 확인하지 않는다)
        when(supabase.refresh("r1")).thenReturn(new SupabaseAuthClient.Session("a2", "r2", 3600, USER));
        AuthResponse res = service.refresh("r1");
        assertThat(res.accessToken()).isEqualTo("a2");
        assertThat(res.refreshToken()).isEqualTo("r2");
        assertThat(res.user().id()).isEqualTo(USER);

        when(profiles.findLive(USER)).thenReturn(Optional.empty()); // 익명화된 프로필 — 토큰이 살아 있어도 로그인 상태로 두지 않는다
        assertThatThrownBy(() -> service.refresh("r1")).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.UNAUTHENTICATED);
        service.logout(USER, "sid-1", "a2");
        verify(revocations).revoke(USER, List.of("sid-1")); // upstream 이 실패해도 서버가 먼저 끊는다 (감사 M-03)
        verify(supabase).logout("a2");
    }

    @Test
    void 끊은_세션의_refresh_는_Supabase_가_새_토큰을_줘도_UNAUTHENTICATED() { // 감사 M-03
        // Supabase 가 session_id=sid-gone 인 새 토큰을 줬지만, 그 세션은 서버가 이미 끊은 목록에 있다
        String access = jwtWithSession("sid-gone");
        when(supabase.refresh("r1")).thenReturn(new SupabaseAuthClient.Session(access, "r2", 3600, USER));
        when(revocations.isRevoked("sid-gone")).thenReturn(true);
        assertThatThrownBy(() -> service.refresh("r1")).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void 매니저가_탈퇴하면_근무시간_근무_불가_패널티도_그_자리에서_지운다() { // 동의 '보유 기간: 매니저 탈퇴 시까지'(V039) 대로 (자체 점검 후속 검토)
        Profile m = Profile.create(USER, "manager", "김매니저", null, PHONE);
        when(profiles.requireLive(USER)).thenReturn(m);
        service.deleteAccount(USER, "password1"); // 비밀번호 확인은 setUp 의 passwordGrant 가 통과시키고, 가드는 기본값 null(막지 않음)
        verify(profiles).anonymize(USER);
        verify(availability).forget(USER);
        verify(penalties).forget(USER);
        verify(support).retainAfterDeletion(USER); // 1:1 문의만 3년
    }

    @Test
    void 탈퇴는_비밀번호_확인_후_가드가_막으면_INVALID_STATE_아니면_익명화하고_Supabase_사용자를_지운다() {
        // 1회차: 가드가 사유를 돌려줘 막힌다 → 그 사유가 오류 문구로 나가고 익명화는 없다. 2회차: 가드 통과 → 익명화·정리·아웃박스·Supabase 삭제
        when(guard.blockReason(USER, "customer")).thenReturn("진행 중인 예약을 먼저 취소해주세요");
        assertThatThrownBy(() -> service.deleteAccount(USER, "password1"))
                .isInstanceOf(ApiException.class).hasMessage("진행 중인 예약을 먼저 취소해주세요");
        verify(profiles, never()).anonymize(any());
        verify(profiles).lockLive(USER); // 가드 검사는 프로필을 잠근 뒤(익명화와 같은 트랜잭션) — 검사 뒤 수락·예약이 끼어들지 못하게

        when(guard.blockReason(USER, "customer")).thenReturn(null);
        service.deleteAccount(USER, "password1");
        verify(profiles).anonymize(USER);
        verify(push).forget(USER); // 탈퇴한 기기로 푸시가 가지 않게
        verify(support).retainAfterDeletion(USER); // 1:1 문의는 3년 뒤 지운다 (자체 점검 #31)
        verify(availability, never()).forget(any()); // 고객은 근무시간이 없다
        verify(penalties, never()).forget(any());
        verify(outbox).publish(new kr.ansimclean.api.common.events.AccountDeleted(USER)); // 삭제 실패 시 아웃박스가 재시도
        verify(supabase).deleteUser(USER);
    }

    @org.junit.jupiter.api.Test
    void 토큰_갱신과_가입은_IP_단위_한도가_있다() {
        // AuthService 와 같은 버킷 이름("refresh-ip"·"signup-ip")으로 한 IP 의 한도를 미리 다 채워 둔 뒤 → 그 IP 의 다음 요청이 RATE_LIMITED
        for (int i = 0; i < RateLimiter.REFRESH_PER_IP_PER_HOUR; i++) rateLimiter.check("refresh-ip", "9.9.9.9", RateLimiter.REFRESH_PER_IP_PER_HOUR);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.refresh("rt", "9.9.9.9")).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.RATE_LIMITED);
        for (int i = 0; i < RateLimiter.SIGNUP_PER_IP_PER_HOUR; i++) rateLimiter.check("signup-ip", "8.8.8.8", RateLimiter.SIGNUP_PER_IP_PER_HOUR);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.signup(new SignupRequest("tok", "이름", null, "password1"), "8.8.8.8")).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.RATE_LIMITED);
    }

    @org.junit.jupiter.api.Test
    void 비밀번호_재설정은_가입된_번호에만_인증번호를_보낸다() {
        // 준비: PHONE 은 살아 있는 고객 계정, 발송하면 유효 시간 5분 → 고객 앱 요청은 보내고 매니저 앱 요청은 PHONE_NOT_FOUND
        Profile p = Profile.create(USER, "customer", "김하늘", "하늘", PHONE);
        when(profiles.findLiveByPhone(PHONE)).thenReturn(Optional.of(p));
        when(otp.request(PHONE, "customer")).thenReturn(java.time.Duration.ofMinutes(5));

        assertThat(service.requestPasswordOtp("12345678", "customer", "1.1.1.1").sent()).isTrue();

        // 매니저 앱에서 고객 번호로 요청하면 없는 번호와 같게 막는다
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.requestPasswordOtp("12345678", "manager", "1.1.1.1"))
                .isInstanceOf(ApiException.class).extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.PHONE_NOT_FOUND);
    }

    @org.junit.jupiter.api.Test
    void 비밀번호_재설정은_비밀번호를_바꾸고_토큰을_소비한_뒤_다른_기기_세션을_끊는다() {
        Profile p = Profile.create(USER, "customer", "김하늘", "하늘", PHONE);
        when(profiles.findLiveByPhone(PHONE)).thenReturn(Optional.of(p));
        // fresh = 재설정 뒤 새로 받는 세션의 토큰 (session_id=sid-new). 변경 전 세션 목록에는 옛 세션과 이 세션이 다 있다고 둔다
        String fresh = jwtWithSession("sid-new");
        when(supabase.passwordGrant(PHONE, "newpassword1")).thenReturn(new SupabaseAuthClient.Session(fresh, "r", 3600, USER));
        when(lookup.sessionIds(USER)).thenReturn(List.of("sid-old", "sid-new"));

        AuthResponse res = service.resetPassword("tok", "newpassword1", "1.1.1.1");

        InOrder order = inOrder(otp, lookup, supabase);
        order.verify(otp).peek("tok");
        order.verify(otp).consume("tok", "customer");
        order.verify(lookup).sessionIds(USER); // 비밀번호 변경 전에 — Supabase 가 변경 때 세션 행을 지운다
        order.verify(supabase).updatePassword(p.getId(), "newpassword1");
        order.verify(supabase).passwordGrant(PHONE, "newpassword1");
        order.verify(supabase).logoutOtherSessions(fresh); // 새 세션을 받은 뒤라야 이 세션은 살아남는다
        verify(revocations).revoke(USER, List.of("sid-old", "sid-new")); // 비밀번호 변경 직후 그때 있던 세션 전부 — 새 세션은 그 뒤에 생긴다 (재점검 A1)
        verify(push).forget(USER); // 끊긴 기기의 푸시 토큰도 지운다 (자체 점검 #29)
        assertThat(res.accessToken()).isEqualTo(fresh);
    }

    @org.junit.jupiter.api.Test
    void 재설정_비밀번호도_8자_미만이면_막는다() {
        // 세 번째 인자 null = IP 없음 (IP 한도 검사를 건너뛴다)
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.resetPassword("tok", "short", null))
                .isInstanceOf(ApiException.class).extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.PASSWORD_TOO_SHORT);
        verify(supabase, never()).updatePassword(any(), anyString());
    }

    @Test
    void 조회는_통과했어도_소비에_지면_비밀번호를_바꾸지_않는다() { // 같은 토큰 두 요청 (감사 H-01)
        // peek 는 setUp 대로 통과하지만, 다른 요청이 먼저 소비해 consume 이 OTP_EXPIRED 를 던지는 상황
        Profile p = Profile.create(USER, "customer", "name", null, PHONE);
        when(profiles.findLiveByPhone(PHONE)).thenReturn(Optional.of(p));
        when(otp.consume("tok", "customer")).thenThrow(new ApiException(ErrorCode.OTP_EXPIRED));
        assertThatThrownBy(() -> service.resetPassword("tok", "newpassword1", null))
                .isInstanceOf(ApiException.class);
        verify(supabase, never()).updatePassword(any(), anyString());
    }

    @Test
    void 가입도_소비에_지면_Supabase_사용자를_만들거나_바꾸거나_지우지_않는다() {
        // 번호로 찾은 잔재 사용자가 있어도, consume 에서 멈추므로 그 뒤의 Supabase 호출이 하나도 없어야 한다
        when(lookup.findIdByPhone(PHONE)).thenReturn(Optional.of(USER));
        when(otp.consume("tok", "customer")).thenThrow(new ApiException(ErrorCode.OTP_EXPIRED));
        assertThatThrownBy(() -> service.signup(new SignupRequest("tok", "name", null, "password1")))
                .isInstanceOf(ApiException.class);
        verify(supabase, never()).updatePassword(any(), anyString());
        verify(supabase, never()).createUser(any(), any());
        verify(supabase, never()).deleteUser(any());
    }

    @Test
    void 재설정_뒤_새_세션_발급이_실패해도_옛_세션은_이미_끊겼다() { // 전체 재점검 A1
        Profile p = Profile.create(USER, "customer", "김하늘", "하늘", PHONE);
        when(profiles.findLiveByPhone(PHONE)).thenReturn(Optional.of(p));
        // 비밀번호는 바뀌었는데 새 세션 발급(passwordGrant)이 Supabase 오류로 실패하는 상황
        when(lookup.sessionIds(USER)).thenReturn(List.of("sid-old"));
        when(supabase.passwordGrant(PHONE, "newpassword1")).thenThrow(new ApiException(ErrorCode.UPSTREAM));
        assertThatThrownBy(() -> service.resetPassword("tok", "newpassword1", null))
                .isInstanceOf(ApiException.class).extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.PASSWORD_CHANGED); // '다시 시도' 가 아니라 로그인 안내 (자체 점검 #22)
        verify(revocations).revoke(USER, List.of("sid-old"));
    }

    @Test
    void 역할이_다른_로그인과_탈퇴_비밀번호_확인은_만든_세션을_바로_끊는다() { // 전체 재점검 A2
        // 매니저 계정으로 고객 앱 로그인 → LOGIN_FAILED 와 함께, 방금 받은 세션(access "a")을 Supabase 에서 끊는다.
        // 이름의 '탈퇴 비밀번호 확인' 쪽(deleteAccount 의 logout)은 이 테스트에서 확인하지 않는다
        Profile manager = Profile.create(USER, "manager", "김매니저", null, PHONE);
        when(profiles.findLive(USER)).thenReturn(Optional.of(manager));
        assertThatThrownBy(() -> service.login(new LoginRequest("12345678", "password1", "customer"), null))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.LOGIN_FAILED);
        verify(supabase).logout("a");
    }
}
