package kr.ansimclean.api.offer;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * offers 테이블을 읽고 쓰는 곳 (CODE_GUIDE.md 3-1). OfferService 만 쓴다.
 * Query 표시가 붙은 것은 JPQL 이다 — 테이블·칸 이름이 아니라 엔티티 이름 Offer·필드 이름(bookingId 등)으로 쓴다.
 * 잠금 순서(매니저 → 예약 → 오퍼)를 지키려고, 잠그기 전에는 id 만 읽는 메서드(findBookingId 등)를 먼저 쓰고 오퍼 행은 마지막에 findForUpdate 로 잠근다.
 */
public interface OfferRepository extends JpaRepository<Offer, UUID> {
    // PESSIMISTIC_WRITE = select … for update (CODE_GUIDE.md 3-4). 수락·거절·만료가 같은 콜에 동시에 와도 하나씩 차례로 처리되고,
    // 뒤에 온 쪽은 바뀐 최신 상태를 본다 (그래서 expire()·isPending() 검사를 잠근 뒤에 한다)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Offer o where o.id = :id")
    Optional<Offer> findForUpdate(UUID id);

    /** 내 콜만 찾는다 — 남의 콜 id 면 비어 있어 NOT_FOUND 가 된다 (다른 매니저 콜의 존재를 드러내지 않는다) */
    Optional<Offer> findByIdAndManagerId(UUID id, UUID managerId);

    /** 잠그기 전에 예약 id 만 (잠금 순서 매니저 → 예약 → 오퍼를 지키려고 오퍼 행은 마지막에 잠근다) */
    @Query("select o.bookingId from Offer o where o.id = :id and o.managerId = :managerId")
    Optional<UUID> findBookingId(UUID id, UUID managerId);

    /** 매니저 구분 없이 콜의 예약 id 만 (만료 스케줄러 expireDue 가 예약을 먼저 잠그려고 쓴다) */
    @Query("select o.bookingId from Offer o where o.id = :id")
    Optional<UUID> findBookingIdById(UUID id);

    /** 만료 스케줄러: 엔티티를 먼저 올리지 않고 id 만 (그 뒤 findForUpdate 로 최신 상태를 잠근다) */
    @Query("select o.id from Offer o where o.status = 'pending' and o.expiresAt < :now order by o.bookingId, o.id") // 예약 잠금 순서 = booking id 오름차순 (cancelWhole 과 같다 — 데드락 방지)
    List<UUID> findExpiredPendingIds(Instant now);

    /** 대기 콜을 가진 매니저 id (중복 없이) — 정지·미승인·탈퇴 매니저의 콜 닫기(OfferService.closeBlockedOffers)가 먼저 본다 */
    @Query("select distinct o.managerId from Offer o where o.status = 'pending'")
    List<UUID> findPendingManagerIds();

    /** 이 매니저들의 대기 콜 id — 예약 잠금 순서 = booking id 오름차순 (만료 스케줄러와 같다) */
    @Query("select o.id from Offer o where o.status = 'pending' and o.managerId in :managerIds order by o.bookingId, o.id")
    List<UUID> findPendingIdsByManagerIds(Collection<UUID> managerIds);

    /** 매니저의 콜 목록: pending, 만료 임박순 */
    List<Offer> findByManagerIdAndStatusOrderByExpiresAt(UUID managerId, String status);

    /** 한 예약에 보낸 콜 전부 (모든 회차·모든 상태) */
    List<Offer> findByBookingId(UUID bookingId);

    /** 한 예약의 특정 상태 콜 — 주로 대기(pending) 콜을 한꺼번에 닫을 때 */
    List<Offer> findByBookingIdAndStatus(UUID bookingId, String status);

    /** 이 회차에 아직 답을 기다리는 콜이 몇 개 남았나 — 0 이면 다음 회차로 (OfferService.afterClosed) */
    long countByBookingIdAndRoundAndStatus(UUID bookingId, int round, String status);

    /** 상태 + 만료 시각으로 찾기. 지금은 부르는 곳이 없다 (만료 스케줄러는 엔티티를 올리지 않으려고 findExpiredPendingIds 를 쓴다) */
    List<Offer> findByStatusAndExpiresAtBefore(String status, Instant now);
}
