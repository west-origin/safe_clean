package kr.ansimclean.api.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.supabase.SupabaseAuthClient;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.common.web.RateLimiter;
import kr.ansimclean.api.profile.FirstCleaningService;
import kr.ansimclean.api.profile.ProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 고객 휴대폰 인증 (첫 1회 청소 혜택용). 로그인 수단이 아니다 — 카카오·구글로 로그인한 계정에 번호를 확인해 붙일 뿐이다.
 *
 * 인증번호 생성·발송·검증은 Supabase Auth 기본 기능(phone_change)이 한다. 우리 서버가 만드는 OTP 는 없다 (2026-11-10 사용자 요청).
 *   요청: PUT /auth/v1/user {phone} (사용자 토큰) → Supabase 가 6자리 생성 → 개발: 테스트 번호는 고정 인증번호(발송 없음)
 *                                                          → 운영: Send SMS Hook → SmsHookService → SmsSender(Solapi)
 *   확인: POST /auth/v1/verify {type: phone_change} → Supabase 가 auth.users.phone·phone_confirmed_at 을 채운다
 * 서버가 따로 하는 일: 고객·번호·IP 별 요청 한도, 60초 재발송 간격, 틀린 횟수(5번) 제한, 확인된 번호를 customer_phone_proofs 에 기록.
 * 인증번호 만료 3분·6자리는 Supabase 설정(sms_otp_exp=180, sms_otp_length=6)과 아래 상수가 같아야 한다 (docs/auth/phone-otp.md).
 */
@Service
public class CustomerPhoneService {
    private static final Logger log = LoggerFactory.getLogger(CustomerPhoneService.class);
    /** 인증번호 유효 시간 — Supabase sms_otp_exp(180초)와 같다 */
    public static final Duration CODE_TTL = Duration.ofMinutes(3);
    /** 같은 고객이 다시 요청할 수 있을 때까지 — Supabase sms_max_frequency(60초)와 같다 */
    public static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    /** 이만큼 틀리면 그 인증번호는 버리고 새로 받아야 한다 */
    public static final int MAX_FAILURES = 5;
    /** 같은 고객·같은 번호의 시간당 발송 요청 */
    public static final int REQUESTS_PER_HOUR = 5;

    /** 발송 응답. verified=true 면 이미 Supabase 에서 확인된 번호라 문자 없이 인증을 마쳤다 */
    public record OtpSent(long expiresIn, long resendAfter, boolean verified) {}

    private final SupabaseAuthClient supabase;
    private final CustomerPhoneStore store;
    private final TransactionTemplate tx;
    private final ProfileService profiles;
    private final FirstCleaningService first;
    private final RateLimiter limiter;
    private final AppClock clock;

    public CustomerPhoneService(SupabaseAuthClient supabase, CustomerPhoneStore store, TransactionTemplate tx, ProfileService profiles,
            FirstCleaningService first, RateLimiter limiter, AppClock clock) {
        this.supabase = supabase;
        this.store = store;
        this.tx = tx;
        this.profiles = profiles;
        this.first = first;
        this.limiter = limiter;
        this.clock = clock;
    }

    public FirstCleaningService.Eligibility eligibility(UUID userId) {
        return first.eligibility(userId);
    }

    /** 인증번호 요청(재발송도 같은 호출). accessToken = 이 요청의 사용자 JWT 원문 */
    public OtpSent request(UUID userId, String accessToken, String localPhone, String ip) {
        String phone = OtpService.toE164(localPhone);
        limiter.check("customer-sms-user", userId.toString(), REQUESTS_PER_HOUR);
        limiter.check("customer-sms-phone", phone, REQUESTS_PER_HOUR); // 여러 계정이 한 사람 번호로 문자를 쏟지 못하게
        if (ip != null) limiter.check("otp-ip", ip, RateLimiter.OTP_PER_IP_PER_HOUR);
        limiter.check("otp-global", "all", RateLimiter.OTP_GLOBAL_PER_HOUR); // 문자 비용 차단기
        // 처음 인증한 번호는 여기서 바꾸지 않는다 — 번호 변경은 별도 지원 절차 (docs/auth/social-login-rollout.md)
        if (store.hasProof(userId)) throw ApiException.validation("이미 휴대폰 인증을 완료했어요");
        Instant now = clock.now();
        // 이전 시도에서 Supabase 확인은 끝났는데 우리 기록만 실패했던 경우 — 문자 없이 마무리한다
        if (store.confirmedPhone(userId, Instant.EPOCH).filter(phone::equals).isPresent()) {
            saveProof(userId, phone, now);
            return new OtpSent(0, 0, true);
        }
        store.challenge(userId).filter(c -> c.requestedAt().isAfter(now.minus(RESEND_INTERVAL))).ifPresent(c -> {
            throw new ApiException(ErrorCode.OTP_RATE_LIMITED, "잠시 후 다시 요청해주세요");
        });
        // 기록을 먼저 남긴다 — 실제 발송 Hook(SmsHookService)이 이 기록이 있는 요청에만 문자를 보낸다
        store.startChallenge(userId, phone, now);
        try {
            supabase.requestPhoneChange(accessToken, phone);
        } catch (ApiException e) {
            store.dropChallenge(userId, now); // 보내지 못한 요청은 지운다
            throw e;
        }
        return new OtpSent(CODE_TTL.toSeconds(), RESEND_INTERVAL.toSeconds(), false);
    }

    /** 인증번호 확인. 성공하면 번호를 기록하고 첫 1회 청소 자격을 돌려준다 */
    public FirstCleaningService.Eligibility verify(UUID userId, String localPhone, String code, String ip) {
        String phone = OtpService.toE164(localPhone);
        limiter.check("customer-sms-verify", userId.toString(), RateLimiter.VERIFY_PER_HOUR);
        if (ip != null) limiter.check("verify-ip", ip, RateLimiter.VERIFY_PER_IP_PER_HOUR);
        CustomerPhoneStore.Challenge c = store.challenge(userId)
                .orElseThrow(() -> ApiException.validation("인증번호를 먼저 요청해주세요"));
        if (!c.phone().equals(phone)) throw ApiException.validation("인증을 요청한 번호를 확인해주세요");
        if (c.failedAttempts() >= MAX_FAILURES) throw tooManyFailures();
        if (clock.now().isAfter(c.requestedAt().plus(CODE_TTL))) throw new ApiException(ErrorCode.OTP_EXPIRED);

        SupabaseAuthClient.PhoneChangeResult result = supabase.verifyPhoneChange(phone, code);
        // 확인에 성공하면 Supabase 가 새 세션을 하나 만들어 준다. 쓰지 않으므로 바로 끊는다
        if (result.accessToken() != null) supabase.logout(result.accessToken());
        // 최종 근거는 응답이 아니라 auth.users: 이 계정에, 이 번호가, 이번 요청 뒤에 확인됐는가
        boolean confirmed = store.confirmedPhone(userId, c.requestedAt()).filter(phone::equals).isPresent();
        if (!confirmed) {
            if (result.ok()) log.warn("휴대폰 인증 응답은 성공인데 계정에 번호가 없다 user={} supabaseUser={}", userId, result.userId());
            int failures = store.countFailure(userId);
            if (failures >= MAX_FAILURES) {
                store.dropChallenge(userId);
                throw tooManyFailures();
            }
            throw new ApiException(ErrorCode.OTP_INVALID);
        }
        saveProof(userId, phone, clock.now());
        return first.eligibility(userId);
    }

    private void saveProof(UUID userId, String phone, Instant now) {
        tx.executeWithoutResult(status -> {
            profiles.lockLive(userId); // 탈퇴와 줄을 선다
            store.saveProof(userId, phone, now);
            store.dropChallenge(userId);
        });
    }

    private static ApiException tooManyFailures() {
        return new ApiException(ErrorCode.OTP_INVALID, "인증번호를 너무 여러 번 틀렸어요. 다시 받아주세요");
    }
}
