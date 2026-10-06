package kr.ansimclean.api.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * manager_penalties 테이블을 읽고 쓰는 곳. ManagerPenaltyService 만 쓴다.
 * nativeQuery 가 없는 Query 는 JPQL 이다 — 테이블·칸 이름 대신 엔티티 ManagerPenalty 와 그 필드 이름(expiresAt 등)으로 쓴다.
 */
public interface ManagerPenaltyRepository extends JpaRepository<ManagerPenalty, UUID> {
    /** 한 매니저의 원장 전부, 최신순 — 쓰임 예: 이번 달 무료 취소 횟수 세기, 패널티 내역 화면 (누적 점수는 오래된 순으로 다시 정렬해 쓴다) */
    List<ManagerPenalty> findByManagerIdOrderByCreatedAtDesc(UUID managerId);

    /** 지금 유효한 양수 벌점 중 가장 늦은 만료 — 회복 행(-1)이 상쇄할 벌점보다 오래 남지 않게 (전체 재점검 P2). 없으면 null */
    @Query("select max(p.expiresAt) from ManagerPenalty p where p.managerId = :managerId and p.status = 'active' and p.expiresAt > :now and p.points > 0")
    Instant latestActivePositiveExpiry(UUID managerId, Instant now);

    /**
     * 같은 매니저의 패널티 기록을 트랜잭션 안에서 줄 세운다 — 동시에 두 취소가 들어오면 둘 다 '이번 달 무료 1회 썼음' 을 읽어 3회째도 0점이 되거나,
     * 둘이 함께 넘은 정지 문턱을 놓쳤다 (자체 점검 #24). 매니저 행 잠금 대신 전용 키 — 콜 수락(매니저 → 예약)과 순서가 엇갈려 교착하지 않게
     * pg_advisory_xact_lock = Postgres 의 '번호표 잠금'. 같은 숫자 키로 잠그려는 트랜잭션은 앞의 것이 커밋·롤백할 때까지 기다리고,
     * 트랜잭션이 끝나면 저절로 풀린다. hashtext 로 'penalty:매니저id' 문자열을 숫자 키로 바꾼다. 돌려받는 값은 쓰지 않는다(잠금만).
     * 부르는 곳: ManagerPenaltyService.record·waive — 횟수·점수·행 상태를 읽기 전에 불러야 한다(읽은 뒤에 잠그면 막지 못한다)
     */
    @Query(value = "select pg_advisory_xact_lock(hashtext('penalty:' || cast(:managerId as text)))", nativeQuery = true)
    Object lockManager(UUID managerId);

    /**
     * 원장 한 행의 매니저 id 만 읽는다 — 운영자 면제가 잠글 매니저를 알아낼 때. 행(엔티티)을 올리지 않으므로, 잠근 뒤의 findForUpdate 가
     * 잠그기 전의 옛 사본이 아니라 DB 의 지금 행을 읽는다 (합친 38건 검토 R5). 없는 id 면 빈 값
     */
    @Query("select p.managerId from ManagerPenalty p where p.id = :id")
    java.util.Optional<UUID> findManagerIdById(UUID id);

    /**
     * 원장 한 행을 SELECT … FOR UPDATE 로 읽는다 (PESSIMISTIC_WRITE) — 운영자 면제가 매니저 잠금 뒤에 부른다.
     * 같은 행을 다른 트랜잭션이 바꾸거나 지우는 중이면(앞선 면제·탈퇴의 forget) 그 커밋을 기다린 뒤의 행을 읽는다 — 지워졌으면 빈 값 (합친 38건 검토 R5)
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ManagerPenalty p where p.id = :id")
    java.util.Optional<ManagerPenalty> findForUpdate(UUID id);

    /** 탈퇴 때와 탈퇴 + 5년 파기 때 지운다 (사유·상세는 자유 입력일 수 있다) */
    long deleteByManagerId(UUID managerId); // ManagerPenaltyService.forget 이 부른다 — 매니저 탈퇴 때(AuthService)와 5년 파기 때(DeletedAccountPurger)

    /** 만료 처리 대상 */
    List<ManagerPenalty> findByStatusAndExpiresAtBefore(String status, Instant now); // expireOld 가 status=active 로 부른다

    /** 회복 판정: 최근 N개월 안에 생긴 행이 있는가 (회복 행 포함 — 회복도 3개월에 한 번만) */
    boolean existsByManagerIdAndCreatedAtAfter(UUID managerId, Instant since);

    /** 점수가 남아 있는 매니저 (회복 스케줄러가 훑는다) */
    @Query("select distinct p.managerId from ManagerPenalty p where p.status = 'active' and p.expiresAt > :now and p.points > 0")
    List<UUID> managersWithActivePoints(Instant now);

    /**
     * 이 매니저가 이 정기청소 시리즈에서 취소·미방문한 적이 있는가 (다음 회차 우선 배정에서 뺀다). 지각·회복 행은 categories 로 거른다 (자체 점검 #7).
     * 상태(적용 중·면제·만료)는 보지 않는다 — 면제·만료돼도 '취소한 회차' 라는 사실은 남는다 (문제 목록 27번, 아래 managersByBookingAndCategory 와 같게)
     */
    boolean existsByManagerIdAndSeriesIdAndCategoryIn(UUID managerId, UUID seriesId, java.util.Collection<String> categories);

    /** 이 예약의 방문을 취소한 매니저 — 면제·만료돼도 '취소한 방문' 이라는 사실은 남는다 (자체 점검 #6) */
    @Query("select distinct p.managerId from ManagerPenalty p where p.bookingId = :bookingId and p.category in :categories")
    List<UUID> managersByBookingAndCategory(UUID bookingId, java.util.Collection<String> categories);
}
