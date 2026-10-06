package kr.ansimclean.api.auth;

import kr.ansimclean.api.auth.dto.AuthResponse;
import kr.ansimclean.api.auth.dto.CompleteSignupResponse;
import kr.ansimclean.api.auth.dto.LoginRequest;
import kr.ansimclean.api.auth.dto.OtpResponse;
import kr.ansimclean.api.auth.dto.SignupRequest;
import kr.ansimclean.api.auth.dto.VerifyResponse;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.events.AccountDeleted;
import kr.ansimclean.api.common.events.AccountMediaDeleted;
import kr.ansimclean.api.common.format.KoreanFormat;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.security.Roles;
import kr.ansimclean.api.common.supabase.SupabaseAuthClient;
import kr.ansimclean.api.profile.Profile;
import kr.ansimclean.api.profile.ProfileService;
import kr.ansimclean.api.push.PushService;
import kr.ansimclean.api.profile.dto.UserResponse;
import kr.ansimclean.api.schedule.AvailabilityService;
import kr.ansimclean.api.support.TermsService;
import kr.ansimclean.api.common.web.RateLimiter;
import kr.ansimclean.api.profile.SessionRevocationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/**
 * 가입·로그인·탈퇴 (BACKEND_PLAN.md §5.1·§7). Supabase 는 휴대폰+비밀번호·JWT·refresh 만 맡고,
 * 인증번호·프로필·약관·권한은 우리 DB 다.
 *
 * 트랜잭션 주의: Supabase 호출은 롤백할 수 없으므로 DB 트랜잭션 **밖**에서 부르고, 실패하면 보상한다(§5.1 signup 절차).
 *
 * 부르는 곳: AuthController(/api/v1/auth/**).
 * 클래스 전체에 트랜잭션이 걸려 있지 않다 — DB 묶음이 필요한 곳만 tx(TransactionTemplate, 코드로 여는 트랜잭션)나
 * 메서드의 @Transactional 로 연다. 그래서 메서드 안에서 "DB 커밋 → Supabase 호출" 순서를 직접 정할 수 있다.
 * 시도 횟수 한도는 RateLimiter(메모리)가 IP·번호·사용자·서비스 전체 단위로 센다. 넘으면 429. 메모리라 서버를 다시 켜면 0 부터 센다.
 */
@Service
public class AuthService {
    public static final int PASSWORD_MIN = 8; // 비밀번호 최소 길이 (짧으면 PASSWORD_TOO_SHORT)
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final OtpService otp;
    private final SupabaseAuthClient supabase;
    private final SupabaseUserLookup userLookup;
    private final ProfileService profiles;
    private final TermsService terms;
    private final AvailabilityService availability;
    private final RateLimiter rateLimiter;
    private final TransactionTemplate tx;
    private final OutboxService outbox;
    private final List<AccountDeletionGuard> deletionGuards; // 탈퇴를 막는 조건들 — 스프링이 구현체(예: 예약·정산)를 모두 모아 넣는다
    private final PushService push;
    private final SessionRevocationService revocations;
    private final kr.ansimclean.api.support.SupportService support;
    private final kr.ansimclean.api.profile.ManagerPenaltyService penalties;

    public AuthService(OtpService otp, SupabaseAuthClient supabase, SupabaseUserLookup userLookup, ProfileService profiles, TermsService terms,
                       AvailabilityService availability, RateLimiter rateLimiter, TransactionTemplate tx, OutboxService outbox, List<AccountDeletionGuard> deletionGuards,
                       PushService push, SessionRevocationService revocations, kr.ansimclean.api.support.SupportService support,
                       kr.ansimclean.api.profile.ManagerPenaltyService penalties) {
        this.support = support;
        this.penalties = penalties;
        this.revocations = revocations;
        this.otp = otp;
        this.supabase = supabase;
        this.userLookup = userLookup;
        this.profiles = profiles;
        this.terms = terms;
        this.availability = availability;
        this.rateLimiter = rateLimiter;
        this.tx = tx;
        this.outbox = outbox;
        this.deletionGuards = deletionGuards;
        this.push = push;
    }

    /**
     * 인증번호 요청. 이미 살아 있는 계정의 번호면 역할과 무관하게 PHONE_TAKEN (휴대폰 하나 = 계정 하나).
     * clientIp 는 IP 단위 한도용(null 이면 건너뛴다) — 번호 단위 한도만으로는 임의 번호로 문자를 무한히 보낼 수 있다.
     */
    public OtpResponse requestOtp(String localPhone, String role, String clientIp) {
        String phone = OtpService.toE164(localPhone);
        if (clientIp != null) rateLimiter.check("otp-ip", clientIp, RateLimiter.OTP_PER_IP_PER_HOUR);
        rateLimiter.check("otp-global", "all", RateLimiter.OTP_GLOBAL_PER_HOUR); // 서비스 전체의 시간당 발송 한도 — 문자 비용 차단기
        if (profiles.phoneInUse(phone)) throw new ApiException(ErrorCode.PHONE_TAKEN);
        // 응답: 보냈음, 인증번호 유효 시간(초), 다시 요청할 수 있을 때까지(초). 번호별 60초·시간당 한도는 otp.request 안에서 검사한다
        return new OtpResponse(true, otp.request(phone, role).toSeconds(), OtpService.RESEND_INTERVAL.toSeconds());
    }

    /** clientIp 없이 부르는 형태 — IP 단위 한도를 건너뛴다 */
    public VerifyResponse verifyOtp(String localPhone, String role, String code) {
        return verifyOtp(localPhone, role, code, null);
    }

    /**
     * 인증번호 확인 → 가입·비밀번호 재설정에 쓸 verificationToken(15분)을 돌려준다.
     * 한도: IP 별·번호별 시간당 시도 수. 맞히면 번호별 시도 수를 지운다 — 예전 실패 때문에 다음 확인이 막히지 않게.
     */
    public VerifyResponse verifyOtp(String localPhone, String role, String code, String clientIp) {
        String phone = OtpService.toE164(localPhone);
        if (clientIp != null) rateLimiter.check("verify-ip", clientIp, RateLimiter.VERIFY_PER_IP_PER_HOUR);
        rateLimiter.check("verify", phone, RateLimiter.VERIFY_PER_HOUR);
        String token = otp.verify(phone, role, code);
        rateLimiter.reset("verify", phone);
        return new VerifyResponse(token, OtpService.TOKEN_TTL.toSeconds());
    }

    /**
     * 비밀번호 재설정 1단계 — 인증번호 발송. 가입에 쓰는 요청과 반대로 **이미 있는 계정**의 번호여야 한다.
     * 번호가 없거나 역할이 다르면 PHONE_NOT_FOUND (번호 존재 여부는 가입 흐름에서 이미 드러나므로 여기서 감추지 않는다 — 대신 문구로 안내).
     */
    public OtpResponse requestPasswordOtp(String localPhone, String role, String clientIp) {
        String phone = OtpService.toE164(localPhone);
        if (clientIp != null) rateLimiter.check("otp-ip", clientIp, RateLimiter.OTP_PER_IP_PER_HOUR);
        rateLimiter.check("otp-global", "all", RateLimiter.OTP_GLOBAL_PER_HOUR);
        Profile p = profiles.findLiveByPhone(phone).filter(profile -> profile.getRole().equals(role))
                .orElseThrow(() -> new ApiException(ErrorCode.PHONE_NOT_FOUND));
        return new OtpResponse(true, otp.request(p.getPhone(), role).toSeconds(), OtpService.RESEND_INTERVAL.toSeconds());
    }

    /**
     * 비밀번호 재설정 2단계 — /auth/phone/verify 로 받은 토큰 + 새 비밀번호.
     * 토큰 소비를 먼저 커밋하고 Supabase 비밀번호를 바꾼 뒤 새 세션을 돌려준다(바로 로그인).
     * 다른 기기의 세션은 전부 끊는다 — 비밀번호를 바꾼 이유가 '남이 쓰고 있다' 일 수 있다.
     * 순서: 옛 세션 목록은 비밀번호를 바꾸기 전에 읽고, 바꾼 직후(새 세션 발급 전) 서버의 끊은 세션 목록에 적는다 —
     * 새 세션 발급이 실패해도 옛 토큰이 통과하지 않게. Supabase 쪽 폐기(scope=others)만 새 세션을 받은 뒤에 한다.
     */
    public AuthResponse resetPassword(String verificationToken, String newPassword, String clientIp) {
        // 재설정 전용 한도 값은 없어 가입과 같은 값(SIGNUP_PER_IP_PER_HOUR)을 쓴다. 버킷 이름("reset-ip")이 달라 횟수는 따로 센다
        if (clientIp != null) rateLimiter.check("reset-ip", clientIp, RateLimiter.SIGNUP_PER_IP_PER_HOUR);
        if (newPassword.length() < PASSWORD_MIN) throw new ApiException(ErrorCode.PASSWORD_TOO_SHORT);
        // peek: 아직 소비하지 않고 토큰으로 번호·역할만 알아낸다 (못 쓰는 토큰이면 OTP_EXPIRED)
        PhoneVerification verification = otp.peek(verificationToken);
        String phone = verification.getPhone();
        String role = verification.getRole();
        Profile p = profiles.findLiveByPhone(phone).filter(profile -> profile.getRole().equals(role))
                .orElseThrow(() -> new ApiException(ErrorCode.PHONE_NOT_FOUND));

        // 외부 변경 전에 단독 트랜잭션으로 소비를 커밋한다. 응답 유실/외부 실패도 토큰을 되살리지 않는다.
        otp.consume(verificationToken, role);
        // 비밀번호를 바꾸기 **전에** 지금 세션을 적어 둔다 — Supabase 는 비밀번호 변경 때 세션 행을 지우므로 뒤에 읽으면 비어 있고,
        // 지워진 세션의 access 토큰은 만료(1시간)까지 계속 통과한다 (감사 M-03, dev 실측 2026-09-24)
        List<String> before = userLookup.sessionIds(p.getId());
        supabase.updatePassword(p.getId(), newPassword);
        // 바꾼 **직후** 옛 세션을 끊는다 — 아래 새 세션 발급이 실패해도 옛 access·refresh 토큰이 통과하지 않게 (감사 M-03, 전체 재점검 A1). 새 세션은 before 에 없다
        revocations.revoke(p.getId(), before);
        push.forget(p.getId()); // 끊긴 기기로 알림이 계속 가지 않게 — 이 기기는 새 세션을 받은 뒤 앱이 다시 등록한다 (자체 점검 #29)
        rateLimiter.reset("login", phone); // 비밀번호를 잊어 여러 번 틀렸을 수 있다 — 새 비밀번호로는 바로 들어갈 수 있게
        AuthResponse res;
        try {
            res = session(supabase.passwordGrant(phone, newPassword), role);
        } catch (ApiException e) {
            // 비밀번호는 이미 바뀌었고 인증 토큰은 소비됐다 — '다시 시도' 하면 '휴대폰 인증을 다시' 가 나왔다. 가입 (d) 처럼 로그인으로 안내한다 (자체 점검 #22)
            log.warn("비밀번호 재설정 뒤 세션 발급 실패 {}: {}", p.getId(), e.getMessage());
            throw new ApiException(ErrorCode.PASSWORD_CHANGED);
        }
        // 위 revoke 는 우리 서버의 끊은 세션 목록, 이것은 Supabase 쪽 폐기 — 방금 받은 새 세션만 남긴다
        supabase.logoutOtherSessions(res.accessToken());
        return res;
    }

    /**
     * 가입 1단계 (토큰 없음). 순서:
     *  (0) 인증 토큰 확인 · 비밀번호 검사 · 토큰 원자적 소비(별도 커밋)
     *  (a) 같은 번호의 Supabase 사용자가 남아 있으면(이전 시도 잔재) 비밀번호만 바꿔 재사용 — 단, 탈퇴 잔재(profiles 에 deleted_at)면 지우고 새로 만든다
     *      (옛 id 를 다시 쓰면 익명화된 프로필·예약 이력이 새 가입자에게 되살아난다), (b) 없으면 생성
     *  (c) profiles 저장. 외부 실패/DB 실패 뒤에는 새 휴대폰 인증으로 다시 시도한다(소비 토큰 재사용 금지)
     *  (d) 로그인해서 토큰 발급
     */
    public AuthResponse signup(SignupRequest req) {
        return signup(req, null); // IP 한도 없이 (AuthServiceTest 가 이 형태를 쓴다)
    }

    /** @param clientIp IP 단위 한도용 (null 이면 건너뛴다) */
    public AuthResponse signup(SignupRequest req, String clientIp) {
        if (clientIp != null) rateLimiter.check("signup-ip", clientIp, RateLimiter.SIGNUP_PER_IP_PER_HOUR);
        if (req.password().length() < PASSWORD_MIN) throw new ApiException(ErrorCode.PASSWORD_TOO_SHORT);
        PhoneVerification verification = otp.peek(req.verificationToken());                     // (0)
        String phone = verification.getPhone();
        String role = verification.getRole();
        // 이 번호를 쓰는 살아 있는 계정이 이미 있으면 토큰을 소비하기 전에 멈춘다
        if (profiles.phoneInUse(phone)) throw new ApiException(ErrorCode.PHONE_TAKEN);

        otp.consume(req.verificationToken(), role); // 외부 사용자 변경 전에 한 요청만 통과. 실패 시 새 SMS 인증 필요
        // auth.users 에 이 번호의 Supabase 사용자가 남아 있는지 (없으면 null)
        UUID userId = userLookup.findIdByPhone(phone).orElse(null);
        if (userId != null && profiles.isDeleted(userId)) {
            supabase.deleteUser(userId); // 탈퇴 때 못 지운 잔재 — 실패하면 UPSTREAM 으로 멈춘다(옛 id 재사용 금지)
            userId = null;
        }
        // 이번 요청에서 새로 만들었는가 — (c) 가 실패하면 새로 만든 사용자만 지운다(재사용한 잔재는 다음 시도에서 다시 쓴다)
        boolean created = false;
        if (userId != null) {
            supabase.updatePassword(userId, req.password());                 // (a)
        } else {
            userId = supabase.createUser(phone, req.password());              // (b)
            created = true;
        }
        try {
            String nickname = role.equals(Roles.CUSTOMER) ? req.nickname() : null; // 닉네임은 고객만 — 매니저가 보내도 버린다
            UUID id = userId; // 람다(status -> …) 안에서는 다시 대입되는 변수(userId)를 쓸 수 없어 복사해 둔다
            tx.executeWithoutResult(status -> {                                // (c)
                profiles.createForSignup(id, role, req.name().trim(), nickname, phone);
            });
        } catch (RuntimeException e) {
            // (c) 실패: 이번에 만든 Supabase 사용자를 지워 되돌린다(보상). 원래 오류는 그대로 다시 던진다
            if (created) {
                try {
                    supabase.deleteUser(userId);
                } catch (RuntimeException cleanup) {
                    log.error("가입 보상 실패 — Supabase 사용자 {} 가 남았다 (다음 시도에서 재사용된다)", userId, cleanup);
                }
            }
            throw e;
        }
        try {
            return session(supabase.passwordGrant(phone, req.password()), role); // (d)
        } catch (ApiException e) {
            // (c) 는 커밋됐고 인증 토큰은 소비됐다 — 같은 요청을 다시 보내도 OTP_EXPIRED 가 되므로, 가입은 끝났으니 로그인하라고 안내한다 (검수 3차)
            log.warn("가입 뒤 로그인 토큰 발급 실패 {}: {}", userId, e.getMessage());
            throw new ApiException(ErrorCode.PHONE_TAKEN, "가입은 완료됐어요. 로그인해서 이어서 진행해주세요");
        }
    }

    /** 약관 동의 기록 + 가입 완료 표시 + 기본 설정. 매니저는 manager_profiles(미승인)와 기본 근무시간 */
    // 한 묶음이라 뒤 단계(완료 표시·기본 근무시간)가 실패하면 앞의 동의 기록도 되돌려진다 — 그래야 재시도 때
    // 아래 '이미 완료' 검사에 걸리지 않고 처음부터 다시 한다. 필수 약관이 빠지면 recordAgreements 가 VALIDATION 을 던진다.
    // 첫 조회는 탈퇴(deleteAccount)와 같은 행 잠금(lockLive, FOR UPDATE)이다 — 가입 중 계정의 탈퇴와 겹치면 둘이 줄을 선다 (문제 목록 28번):
    // 가입 완료가 먼저면 탈퇴가 기다렸다가 방금 만든 알림 설정·근무시간까지 지우고, 탈퇴가 먼저면 여기서 NOT_FOUND 로 끝나 아무것도 쓰지 않는다.
    // 예전에는 잠그지 않고 읽어, 탈퇴 뒤에 커밋되면서 익명화한 이름·번호·탈퇴 표시를 옛 값으로 되돌렸다. 같은 계정의 가입 완료 두 번도 이 잠금으로 줄을 선다
    @Transactional
    public CompleteSignupResponse completeSignup(UUID userId, List<String> agreedTermIds) {
        Profile p = profiles.lockLive(userId);
        // 이미 완료한 계정이 다시 부르면(재시도) 기록을 다시 쓰지 않고 지금 상태만 돌려준다
        if (!p.isSignupComplete()) {
            terms.recordAgreements(userId, p.getRole(), agreedTermIds);
            // 선택 약관 "marketing" 에 동의했을 때만 마케팅 알림을 켠 채로 시작한다
            p = profiles.completeSignup(userId, agreedTermIds != null && agreedTermIds.contains("marketing"));
            if (p.getRole().equals(Roles.MANAGER)) availability.createDefaultHours(userId);
        }
        return new CompleteSignupResponse(true, profiles.userResponse(p));
    }

    /** 로그인. 번호·비밀번호·역할 중 무엇이 틀렸는지 구분하지 않는다. 가입 중 계정도 성공하고 signupStep 으로 이어간다 */
    public AuthResponse login(LoginRequest req, String clientIp) {
        String phone = OtpService.toE164(req.phone());
        if (clientIp != null) rateLimiter.check("login-ip", clientIp, RateLimiter.LOGIN_PER_IP_PER_HOUR);
        rateLimiter.check("login", phone, RateLimiter.LOGIN_PER_HOUR);
        SupabaseAuthClient.Session session = supabase.passwordGrant(phone, req.password()); // 번호·비밀번호가 틀리면 여기서 LOGIN_FAILED
        Profile p = profiles.findLive(session.userId()).orElse(null);
        // 프로필이 없거나 탈퇴한 계정, 또는 다른 앱의 계정(고객 번호로 매니저 앱 로그인 등)이면 같은 LOGIN_FAILED
        if (p == null || !p.getRole().equals(req.role())) {
            supabase.logout(session.accessToken()); // 쓰지 않을 세션을 남기지 않는다 (전체 재점검 A2, 실패해도 조용히)
            throw new ApiException(ErrorCode.LOGIN_FAILED);
        }
        rateLimiter.reset("login", phone);
        return new AuthResponse(session.accessToken(), session.refreshToken(), session.expiresIn(), profiles.userResponse(p));
    }

    /** IP 한도 없이 부르는 형태 (AuthServiceTest 가 쓴다) */
    public AuthResponse refresh(String refreshToken) {
        return refresh(refreshToken, null);
    }

    /**
     * 토큰 갱신: Supabase 에서 새 access·refresh 토큰을 받는다. Supabase 가 거부한 토큰, 우리가 끊은 세션, 탈퇴한 계정이면 UNAUTHENTICATED(401) —
     * 앱 http.js 는 refresh 가 401 이면 세션을 지운다(다시 로그인). Supabase 쪽 한도·장애는 RATE_LIMITED(429)·UPSTREAM(502) 으로 나가
     * 앱이 세션을 지우지 않는다 — 여기서 그런 오류를 401 로 바꾸면 일시 장애로 사용자가 로그아웃된다.
     */
    public AuthResponse refresh(String refreshToken, String clientIp) {
        if (clientIp != null) rateLimiter.check("refresh-ip", clientIp, RateLimiter.REFRESH_PER_IP_PER_HOUR);
        SupabaseAuthClient.Session session = supabase.refresh(refreshToken);
        if (revocations.isRevoked(session.sessionId())) throw new ApiException(ErrorCode.UNAUTHENTICATED); // 끊은 세션의 refresh — upstream 폐기가 실패했어도 여기서 막힌다 (감사 M-03)
        Profile p = profiles.findLive(session.userId()).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        return new AuthResponse(session.accessToken(), session.refreshToken(), session.expiresIn(), profiles.userResponse(p));
    }

    /**
     * 로그아웃. 이 세션을 서버 목록에 먼저 적어 access 토큰(만료 전)과 refresh 로 받을 토큰을 바로 거부하고, Supabase 세션도 폐기한다.
     * upstream 폐기가 실패해도 서버 목록이 막으므로 앱에는 성공으로 답한다 (감사 M-03). sessionId 가 없으면(옛 토큰 형식) upstream 만
     */
    public void logout(UUID userId, String sessionId, String accessToken) {
        if (sessionId != null) revocations.revoke(userId, List.of(sessionId));
        supabase.logout(accessToken);
    }

    /** GET /auth/me: 지금 사용자 정보 (가입 도중이면 signupStep 에 다음 화면) */
    public UserResponse me(UUID userId) {
        return profiles.userResponse(profiles.requireLive(userId));
    }

    /**
     * 탈퇴. 비밀번호 재확인 → 진행 중 예약 등 가드 → 익명화(DB) + AccountDeleted 아웃박스 → Supabase 사용자 삭제.
     * Supabase 삭제는 외부 효과라 아웃박스(SupabaseUserDeletionHandler)가 재시도한다(§11-1). 여기서도 한 번 바로 시도한다(성공하면 핸들러는 404 로 조용히 끝난다).
     */
    public void deleteAccount(UUID userId, String password) {
        rateLimiter.check("delete", userId.toString(), RateLimiter.DELETE_PER_USER_PER_HOUR); // 로그인된 세션으로 비밀번호를 무한히 맞춰 보지 못하게
        Profile p = profiles.requireLive(userId);
        SupabaseAuthClient.Session check = supabase.passwordGrant(p.getPhone(), password); // 틀리면 LOGIN_FAILED
        supabase.logout(check.accessToken()); // 비밀번호 확인용으로 생긴 세션은 바로 끊는다 — 가드에 막혀도 남지 않게 (전체 재점검 A2)
        // 아래 묶음은 트랜잭션 하나: 가드에 막히거나 중간에 실패하면 익명화·아웃박스 기록까지 전부 되돌린다
        tx.executeWithoutResult(status -> {
            profiles.lockLive(userId);                 // 가드 검사와 익명화를 같은 트랜잭션·잠금 안에서 (검사 뒤 수락이 끼어들지 못하게)
            if (p.getRole().equals(Roles.MANAGER)) profiles.lockManagerIfExists(userId); // 콜 수락(lockManager)과 직렬화. 가입 미완료 매니저는 행이 없다
            for (AccountDeletionGuard guard : deletionGuards) {
                String reason = guard.blockReason(userId, p.getRole());
                if (reason != null) throw new ApiException(ErrorCode.INVALID_STATE, reason);
            }
            profiles.anonymize(userId);
            push.forget(userId); // 탈퇴한 기기로 푸시가 가지 않게 (§5.1 deleteAccount)
            support.retainAfterDeletion(userId); // 1:1 문의는 분쟁처리 기록으로 3년 뒤 지운다 (사용자 결정 2026-09-27, 자체 점검 #31)
            if (p.getRole().equals(Roles.MANAGER)) {
                // 근무시간·근무 불가(사유 자유 입력)·패널티 원장도 탈퇴 때 지운다 — 매니저 동의 '보유 기간: 매니저 탈퇴 시까지'(V039) 가 사실이 되게.
                // 예전에는 5년 파기까지 남아 동의 문구와 달랐다 (자체 점검 후속 검토). 맡은 작업·미지급 정산이 없어야 여기까지 온다(가드)
                availability.forget(userId);
                penalties.forget(userId);
            }
            outbox.publish(new AccountDeleted(userId)); // 커밋되면 SupabaseUserDeletionHandler 가 Supabase 사용자 삭제를 (다시) 시도한다
            outbox.publish(new AccountMediaDeleted(userId)); // 녹화본·수리 사진·바디캠 Ingress 정리 (live 패키지 핸들러)
        });
        // 트랜잭션이 커밋된 뒤(탈퇴 확정) 한 번 바로 지워 본다. 실패해도 탈퇴는 성공으로 답한다
        try {
            supabase.deleteUser(userId);
        } catch (RuntimeException e) {
            log.warn("탈퇴 후 Supabase 사용자 {} 삭제 실패 — 아웃박스가 다시 시도한다", userId);
        }
        log.info("탈퇴 {}", KoreanFormat.maskPhone(p.getPhone())); // 로그에는 번호를 가려서 남긴다
    }

    /** Supabase 로그인 결과 + 우리 프로필로 앱에 줄 응답(AuthResponse)을 만든다. role 인자는 받지만 지금은 쓰지 않는다 */
    private AuthResponse session(SupabaseAuthClient.Session s, String role) {
        Profile p = profiles.requireLive(s.userId());
        return new AuthResponse(s.accessToken(), s.refreshToken(), s.expiresIn(), profiles.userResponse(p));
    }

}
