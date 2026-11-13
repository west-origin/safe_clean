package kr.ansimclean.api.job;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * manager_cancel_requests 읽기·쓰기 (V068). JobService 만 쓴다 — 다른 기능(offer)은 JobService 의 메서드를 부른다(CLAUDE.md §11-1).
 * 잠금 순서: 예약 → 요청 (승인·거절·자동 종료 모두 예약 행을 먼저 잠근다)
 */
public interface JobCancelRequestRepository extends JpaRepository<JobCancelRequest, UUID> {
    /** select … for update — 두 어드민이 같은 요청을 동시에 승인·거절해도 하나씩 처리된다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from JobCancelRequest r where r.id = :id")
    Optional<JobCancelRequest> findForUpdate(UUID id);

    /**
     * 잠그기 전에 예약 id 만 (엔티티를 올리지 않는다). 엔티티를 먼저 올려 두면 뒤의 findForUpdate 가 같은 객체를 돌려줘
     * 다른 어드민이 막 승인한 요청도 옛 상태(pending)로 보인다 — 콜 수락의 OfferRepository.findBookingIdById 와 같은 이유
     */
    @Query("select r.bookingId from JobCancelRequest r where r.id = :id")
    Optional<UUID> findBookingIdById(UUID id);

    /** 이 예약의 대기 요청 (부분 유니크 인덱스라 많아야 하나) */
    Optional<JobCancelRequest> findFirstByBookingIdAndStatus(UUID bookingId, String status);

    /** 이 예약의 요청 중 가장 최근 것 (어드민 대기 목록의 '취소한 매니저·요청 시각') */
    Optional<JobCancelRequest> findFirstByBookingIdOrderByRequestedAtDesc(UUID bookingId);

    /** 어드민 '취소 요청' 목록 — 대기 중만, 오래된 순 */
    List<JobCancelRequest> findByStatusOrderByRequestedAt(String status);

    /** 탈퇴 + 5년 파기: 이 매니저가 적은 상세를 비운다 (사유 key·시각은 남긴다) */
    @Modifying
    @Query("update JobCancelRequest r set r.detail = null where r.managerId = :managerId")
    int scrubDetails(UUID managerId);
}
