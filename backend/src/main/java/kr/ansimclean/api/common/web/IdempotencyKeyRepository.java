package kr.ansimclean.api.common.web;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * idempotency_keys 테이블 읽기·쓰기. Idempotent 만 쓴다 (다른 기능은 Idempotent.run 을 부른다).
 */
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, IdempotencyKey.Id2> {
    // 돌려주는 int 는 바뀐 행 수(1 = 새로 넣음, 0 = 이미 있음)
    // 같은 키로 먼저 insert 한 트랜잭션이 아직 끝나지 않았으면, Postgres 는 그 트랜잭션이 끝날 때까지 이 insert 를 기다리게 한다
    // (앞이 커밋하면 0, 앞이 롤백하면 1 — Idempotent 의 '주의' 참고)
    /** 먼저 온 요청 하나만 통과시킨다. 이미 있으면 0행 (native: on conflict do nothing) */
    @Modifying
    @Query(value = """
            insert into idempotency_keys (key, user_id, request_hash, status, created_at)
            values (:key, :userId, :hash, 'processing', :now)
            on conflict (key, user_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("key") String key, @Param("userId") UUID userId, @Param("hash") String hash, @Param("now") Instant now);

    /** 그 키의 행을 잠그고 읽는다 (PESSIMISTIC_WRITE = SQL 의 FOR UPDATE, CODE_GUIDE.md 3-4). 없으면 빈 Optional */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from IdempotencyKey k where k.key = :key and k.userId = :userId")
    Optional<IdempotencyKey> findForUpdate(@Param("key") String key, @Param("userId") UUID userId);

    /** created_at 이 before 보다 이른 행을 전부 지우고 지운 건수를 돌려준다 (Idempotent.purgeOlderThan 이 부른다) */
    long deleteByCreatedAtBefore(Instant before);
}
