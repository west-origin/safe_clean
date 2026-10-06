package kr.ansimclean.api.common.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * outbox_events 테이블을 읽고 쓰는 저장소.
 * OutboxService 가 save 로 행을 넣고, OutboxDispatcher 가 findDueIds 로 고른 뒤 lockDue 로 한 건씩 잠근다.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    /** 보낼 차례가 된 것들의 id (잠그지 않는다 — 이벤트마다 lockDue 로 따로 잠근다, 감사 M-08) */
    // @Query: JPQL(테이블·컬럼 대신 엔티티·필드 이름으로 쓰는 쿼리). 보낼 시각이 이른 것부터, Limit 만큼만 (디스패처의 BATCH)
    @Query("select e.id from OutboxEvent e where e.status = 'pending' and e.nextAttemptAt <= :now order by e.nextAttemptAt")
    List<UUID> findDueIds(@Param("now") Instant now, Limit limit);

    /** 이 이벤트가 아직 보낼 차례면 잠근다. 다른 인스턴스·스레드가 쥐고 있으면 건너뛴다(SKIP LOCKED) — 중복 발송 방지 */
    // @Lock(PESSIMISTIC_WRITE): SELECT … FOR UPDATE 로 행을 잠근다. 트랜잭션이 끝날 때까지 다른 쪽은 이 행을 잠글 수 없다.
    // lock.timeout = -2 는 Hibernate 에서 SKIP LOCKED(잠긴 행은 기다리지 않고 없는 셈 친다)를 뜻한다.
    // status·nextAttemptAt 조건을 다시 거는 이유: findDueIds 로 고른 뒤 잠그기 전에 다른 쪽이 이미 보냈을 수 있다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select e from OutboxEvent e where e.id = :id and e.status = 'pending' and e.nextAttemptAt <= :now")
    Optional<OutboxEvent> lockDue(@Param("id") UUID id, @Param("now") Instant now);
}
