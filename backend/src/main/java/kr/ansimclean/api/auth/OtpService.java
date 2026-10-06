package kr.ansimclean.api.auth;

import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.format.KoreanFormat;
import kr.ansimclean.api.common.sms.SmsSender;
import kr.ansimclean.api.common.time.AppClock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 휴대폰 인증번호 — Supabase 가 아니라 우리가 만들고 검증한다 (BACKEND_PLAN.md §7.1).
 * 6자리 난수 → 해시만 저장 → 국내 문자 API 로 발송 → 검증 성공 시 가입에 쓸 불투명 토큰(15분) 발급.
 * 부르는 곳: AuthService (가입과 비밀번호 재설정 둘 다 이 흐름을 쓴다).
 * 한 건의 흐름: request(발송) → verify(확인, 토큰 발급) → peek(토큰 확인만) → consume(토큰 한 번 사용).
 * 기록은 phone_verifications 테이블(PhoneVerification)에 남고, 1일이 지난 기록은 AuthCleaner 가 매일 새벽 지운다.
 */
@Service
public class OtpService {
    public static final Duration CODE_TTL = Duration.ofMinutes(5); // 인증번호 유효 시간
    public static final Duration TOKEN_TTL = Duration.ofMinutes(15); // 확인 뒤 받은 verificationToken 의 유효 시간 — 이 안에 가입·재설정을 끝내야 한다
    /** 같은 번호로 다시 보낼 수 있을 때까지 */
    public static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    /** 같은 번호의 시간당 발송 한도 */
    public static final int MAX_PER_HOUR = 5;

    private static final SecureRandom RANDOM = new SecureRandom(); // 암호용 난수 — 인증번호·토큰을 예측할 수 없게

    private final PhoneVerificationRepository repository;
    private final SmsSender sms;
    private final AppClock clock;
    private final TransactionTemplate tx;
    private final String salt; // 해시에 섞는 서버 비밀 문자열. 6자리 숫자는 100만 가지뿐이라 솔트 없는 해시는 전부 대입해 되돌릴 수 있다

    // @Value 는 설정값 app.otp.salt(환경변수 OTP_SALT)를 넣어 준다 — 비어 있으면 서버가 뜨지 않게 막는다
    // (솔트가 바뀌면 그 전에 보낸 인증번호는 해시가 달라 확인되지 않는다 — 유효 시간이 5분이라 영향은 그 사이뿐이다)
    public OtpService(PhoneVerificationRepository repository, SmsSender sms, AppClock clock, TransactionTemplate tx, @Value("${app.otp.salt}") String salt) {
        if (salt == null || salt.isBlank()) throw new IllegalStateException("OTP_SALT 가 비어 있어요 — backend/.env 에 32자 이상 임의 문자열을 넣어주세요 (docs/runbook.md)");
        this.repository = repository;
        this.sms = sms;
        this.clock = clock;
        this.tx = tx;
        this.salt = salt;
    }

    /**
     * 인증번호 발송. 한도 검사·저장은 짧은 트랜잭션(번호별 advisory lock 으로 동시 요청도 한도를 지킨다),
     * 문자 발송은 트랜잭션 **밖**(CLAUDE.md §11-1). 발송이 실패하면 기록을 지우고 UPSTREAM 으로 드러낸다.
     */
    public Duration request(String e164, String role) {
        String code = newCode();
        UUID id = tx.execute(status -> issue(e164, role, hash(code))); // 한도 검사 + 저장만 짧은 트랜잭션으로. 커밋된 뒤 문자를 보낸다
        try {
            sms.send(e164, "[안심클린] 인증번호 " + code + " (" + CODE_TTL.toMinutes() + "분 안에 입력해주세요)");
        } catch (SmsSender.SmsException e) {
            // 보내지 못한 기록은 지운다 — 남겨 두면 60초·시간당 발송 한도에 헛되이 잡힌다
            tx.executeWithoutResult(status -> repository.deleteById(id));
            throw new ApiException(ErrorCode.UPSTREAM, "인증번호를 보내지 못했어요. 잠시 후 다시 시도해주세요");
        }
        return CODE_TTL;
    }

    /** 트랜잭션 안: 같은 번호의 요청을 줄 세우고(advisory lock) 60초·시간당 한도를 검사한 뒤 저장 */
    UUID issue(String e164, String role, String codeHash) {
        repository.lockPhone(e164);
        Instant now = clock.now();
        long recent = repository.countByPhoneAndCreatedAtAfter(e164, now.minus(RESEND_INTERVAL));
        if (recent > 0) throw new ApiException(ErrorCode.OTP_RATE_LIMITED, "잠시 후 다시 요청해주세요");
        if (repository.countByPhoneAndCreatedAtAfter(e164, now.minus(Duration.ofHours(1))) >= MAX_PER_HOUR)
            throw new ApiException(ErrorCode.OTP_RATE_LIMITED, "인증 요청이 너무 많아요. 잠시 후 다시 시도해주세요");
        return repository.save(PhoneVerification.issue(e164, role, codeHash, now, now.plus(CODE_TTL))).getId();
    }

    /**
     * 인증번호 확인. 성공하면 가입에 쓸 토큰을 돌려준다.
     * 틀리면 시도 횟수를 올리고(한도에 닿으면 폐기), 만료·한도 초과는 각각 다른 코드로 알린다.
     * ApiException 은 업무 결과이므로 롤백하지 않는다 — 오답 횟수가 남아야 '5회 틀리면 폐기' 가 동작한다.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public String verify(String e164, String role, String code) {
        repository.lockPhone(e164); // 같은 번호의 동시 오답이 attempts 를 서로 덮지 않게
        Instant now = clock.now();
        // 가장 최근에 받은 인증번호만 본다 — 그 전에 받은 번호로는 확인되지 않는다
        PhoneVerification v = repository.findFirstByPhoneAndRoleOrderByCreatedAtDesc(e164, role)
                .orElseThrow(() -> new ApiException(ErrorCode.OTP_EXPIRED));
        if (v.isExpired(now)) throw new ApiException(ErrorCode.OTP_EXPIRED);
        if (v.getVerifiedAt() != null) throw new ApiException(ErrorCode.OTP_EXPIRED, "이미 확인한 인증번호예요. 다시 받아주세요"); // 성공한 코드로 다시 verify 해 토큰을 덮어쓰지 못하게
        if (v.isUsedUp()) throw new ApiException(ErrorCode.OTP_INVALID, "인증번호를 너무 여러 번 틀렸어요. 다시 받아주세요");
        // 받은 번호를 같은 방식으로 해시해 저장된 해시와 비교한다 (원문은 저장돼 있지 않다)
        if (!constantTimeEquals(v.getCodeHash(), hash(code))) {
            v.countFailure(); // 아래에서 오류를 던져도 noRollbackFor 라 이 증가는 커밋된다
            throw new ApiException(ErrorCode.OTP_INVALID);
        }
        String token = newToken();
        v.succeed(token, now);
        return token;
    }

    /**
     * 가입에서 토큰을 쓴다. 유효하면 그 인증 건을 돌려주고 소비 처리한다.
     * 없는·만료된·이미 쓴 토큰은 전부 같은 오류(추측을 돕지 않는다).
     * 비밀번호 재설정(AuthService.resetPassword)도 같은 방식으로 쓴다. 이 메서드가 커밋된 뒤에야 Supabase 를 바꾸므로,
     * 뒤의 외부 호출이 실패해도 토큰은 되살아나지 않는다 — 사용자는 새 인증번호를 받아야 한다.
     */
    @Transactional
    public PhoneVerification consume(String token, String role) {
        PhoneVerification v = peek(token); // 같은 클래스 안에서 부른 peek 는 이 메서드의 트랜잭션 안에서 돈다
        if (!v.getRole().equals(role)) throw new ApiException(ErrorCode.OTP_EXPIRED, "휴대폰 인증을 다시 해주세요");
        Instant now = clock.now();
        // 조건부 UPDATE: 아직 안 썼고 확인한 지 15분 안인 토큰일 때만 1행이 바뀐다. 동시에 두 요청이 와도 하나만 1 을 받는다
        if (repository.consumeIfUnused(token, role, now, now.minus(TOKEN_TTL)) != 1)
            throw new ApiException(ErrorCode.OTP_EXPIRED, "휴대폰 인증을 다시 해주세요");
        v.consume(now); // 위 UPDATE 쿼리는 메모리의 v 를 바꾸지 않으므로, 돌려줄 객체에도 사용 시각을 적는다
        return v;
    }

    /** 토큰이 아직 쓸 수 있는지만 본다(소비하지 않는다). 가입·재설정은 이것으로 번호·역할을 알아낸 뒤, 외부(Supabase) 변경 **전에** consume 한다 (감사 H-01) */
    @Transactional(readOnly = true)
    public PhoneVerification peek(String token) {
        Instant now = clock.now();
        PhoneVerification v = repository.findByToken(token).orElseThrow(() -> new ApiException(ErrorCode.OTP_EXPIRED, "휴대폰 인증을 다시 해주세요"));
        // 쓸 수 있는 토큰 = 확인을 마쳤고, 아직 쓰지 않았고, 확인한 지 15분이 안 됐다
        boolean usable = v.getVerifiedAt() != null && v.getConsumedAt() == null && now.isBefore(v.getVerifiedAt().plus(TOKEN_TTL));
        if (!usable) throw new ApiException(ErrorCode.OTP_EXPIRED, "휴대폰 인증을 다시 해주세요");
        return v;
    }

    /** 앱이 보낸 "12345678" → "+821012345678". 형식이 틀리면 VALIDATION */
    public static String toE164(String local) {
        try {
            return KoreanFormat.toE164(local);
        } catch (IllegalArgumentException e) {
            throw ApiException.validation(e.getMessage());
        }
    }

    /** 000000~999999 중 하나. 앞자리가 0 이어도 6자리로 채운다 */
    private static String newCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    /** 32바이트 난수 → URL 에 넣어도 안전한 Base64 문자열(43자). 앱이 가입·재설정 요청에 verificationToken 으로 보낸다 */
    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 인증번호 + 솔트의 SHA-256 (16진 문자열). DB 에는 이 값만 저장한다 */
    String hash(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((code + salt).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            // 자바 표준이 SHA-256 지원을 요구하므로 실제로는 거의 오지 않는다. 오면 내부 오류 문구만 내보낸다
            throw new ApiException(ErrorCode.INTERNAL, ErrorCode.INTERNAL.defaultMessage());
        }
    }

    /** 두 해시를 비교하는 데 걸리는 시간이 내용과 상관없게 비교한다 — 응답 시간으로 맞은 부분을 짐작하지 못하게 */
    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
