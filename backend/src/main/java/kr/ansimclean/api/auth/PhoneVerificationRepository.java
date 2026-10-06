package kr.ansimclean.api.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * phone_verifications 테이블(PhoneVerification) 읽기·쓰기. 쓰는 곳은 auth 패키지 안이다 (예: OtpService, AuthCleaner 의 오래된 기록 삭제).
 * 여기의 쓰기 메서드(consumeIfUnused·deleteBy…)와 lockPhone 은 트랜잭션 안에서만 불러야 한다 — 부르는 쪽이 @Transactional 이나 tx 로 연다.
 */
public interface PhoneVerificationRepository extends JpaRepository<PhoneVerification, UUID> {
    /** 같은 번호·역할의 가장 최근 발급 건 (검증은 최신 것으로만) */
    Optional<PhoneVerification> findFirstByPhoneAndRoleOrderByCreatedAtDesc(String phone, String role);

    /** 토큰으로 인증 건 찾기 (OtpService.peek) */
    Optional<PhoneVerification> findByToken(String token);

    /** 조건부 소비(compare-and-set): 두 요청이 peek 를 통과해도 영향 행이 1인 한 요청만 이긴다 (감사 H-01) */
    @Modifying(flushAutomatically = true) // flushAutomatically: 실행 전에 메모리의 변경을 DB 에 먼저 쓴다
    @Query("update PhoneVerification v set v.consumedAt = :now where v.token = :token and v.role = :role and v.consumedAt is null and v.verifiedAt > :oldest")
    int consumeIfUnused(@Param("token") String token, @Param("role") String role, // 결과 = 바뀐 행 수 (1 이면 이 요청이 사용, 0 이면 이미 썼거나 15분이 지나는 등 조건에 안 맞음)
                        @Param("now") Instant now, @Param("oldest") Instant oldest);

    /** 속도 제한: 최근 n분 안에 이 번호로 몇 번 보냈는지 */
    long countByPhoneAndCreatedAtAfter(String phone, Instant after);

    /** 이 시각보다 먼저 만든 기록을 지우고 지운 수를 돌려준다 (AuthCleaner 가 매일, 트랜잭션 안에서 부른다) */
    long deleteByCreatedAtBefore(Instant before);

    /** 같은 번호의 발송 요청을 트랜잭션 안에서 줄 세운다 (Postgres advisory lock — 커밋·롤백 때 풀린다) */
    @Query(value = "select pg_advisory_xact_lock(hashtext(:phone))", nativeQuery = true) // hashtext 로 번호 문자열을 정수 잠금 키로 바꾼다
    Object lockPhone(@Param("phone") String phone); // 돌려받는 값은 쓰지 않는다 — 잠금을 거는 것이 목적
}
