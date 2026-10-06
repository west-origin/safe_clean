package kr.ansimclean.api.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 휴대폰 인증번호 1건 (phone_verifications). 코드 원문은 저장하지 않고 해시만 둔다.
 * 상태 전이는 이 클래스의 메서드로만 (CLAUDE.md §11-6).
 * 한 행의 흐름: issue(발급) → countFailure(틀림, 여러 번 가능) → succeed(맞힘, token 저장) → consume(가입·재설정에 사용).
 * 만들고 바꾸는 곳은 OtpService 뿐이다 (AuthService 는 peek 결과에서 번호·역할만 읽는다).
 * 만든 지 1일이 지난 행은 AuthCleaner 가 매일 새벽 지운다.
 * consume 은 이 객체의 값만 바꾼다 — 두 요청이 같은 토큰을 동시에 쓰지 못하게 막는 것은
 * PhoneVerificationRepository.consumeIfUnused(조건부 UPDATE)다.
 */
@Entity
@Table(name = "phone_verifications")
public class PhoneVerification {
    /** 인증번호를 틀릴 수 있는 횟수 */
    public static final int MAX_ATTEMPTS = 5;

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(columnDefinition = "text", nullable = false)
    private String phone; // "+8210…" 형식(E.164)

    @Column(columnDefinition = "text", nullable = false)
    private String role; // "customer" | "manager" — 인증을 요청한 앱

    @Column(name = "code_hash", columnDefinition = "text", nullable = false)
    private String codeHash; // 인증번호 + 솔트의 해시 (OtpService.hash). 원문은 저장하지 않는다

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt; // 인증번호 만료 시각 (발급 + 5분)

    @Column(nullable = false)
    private int attempts; // 틀린 횟수

    @Column(name = "verified_at")
    private Instant verifiedAt; // 맞힌 시각. 토큰 유효 시간(15분)은 여기서부터 센다. 맞히기 전에는 null

    @Column(columnDefinition = "text", unique = true)
    private String token; // 맞힌 뒤 발급한 불투명 토큰(verificationToken). 맞히기 전에는 null

    @Column(name = "consumed_at")
    private Instant consumedAt; // 가입·재설정에 쓴 시각. 채워지면 다시 쓸 수 없다

    @Column(name = "created_at", nullable = false)
    private Instant createdAt; // 발급 시각 — 60초·시간당 발송 한도와 1일 뒤 정리의 기준

    protected PhoneVerification() {} // JPA 가 DB 행을 읽어 객체를 만들 때 쓰는 빈 생성자. 코드에서는 issue 로만 만든다

    /** 새 발급 기록을 만든다 (아직 저장 전 — OtpService.issue 가 save 한다). 틀린 횟수 0 */
    public static PhoneVerification issue(String phone, String role, String codeHash, Instant now, Instant expiresAt) {
        PhoneVerification v = new PhoneVerification();
        v.id = UUID.randomUUID();
        v.phone = phone;
        v.role = role;
        v.codeHash = codeHash;
        v.attempts = 0;
        v.createdAt = now;
        v.expiresAt = expiresAt;
        return v;
    }

    // 읽기 전용 getter — 값을 바꾸는 것은 아래 메서드(countFailure·succeed·consume)뿐이다
    public UUID getId() { return id; }
    public String getPhone() { return phone; }
    public String getRole() { return role; }
    public String getCodeHash() { return codeHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public int getAttempts() { return attempts; }
    public String getToken() { return token; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public Instant getConsumedAt() { return consumedAt; }

    /** 인증번호가 만료됐나 (만료 시각과 같은 순간도 만료로 본다) */
    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** 틀린 횟수가 한도(5회)에 닿았나 — 닿으면 맞는 번호를 넣어도 확인되지 않는다 (OtpService.verify) */
    public boolean isUsedUp() {
        return attempts >= MAX_ATTEMPTS;
    }

    /** 틀린 입력 1회 기록. 한도에 닿으면 더 못 쓴다 */
    public void countFailure() {
        attempts++;
    }

    /** 검증 성공 → 가입에 쓸 불투명 토큰 발급 */
    public void succeed(String token, Instant now) {
        this.verifiedAt = now;
        this.token = token;
    }

    /** 가입에 사용 (재사용 금지) */
    public void consume(Instant now) {
        this.consumedAt = now;
    }
}
