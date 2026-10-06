package kr.ansimclean.api.booking;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * bookings 테이블 읽기·쓰기 (CODE_GUIDE.md 3-1). @Query 의 기본은 JPQL(테이블 대신 엔티티·필드 이름)이다.
 * booking 패키지 안에서만 쓴다(예: BookingService, StaleBookingCleaner) — 다른 기능은 BookingService 를 거친다(CLAUDE.md §11-1).
 */
public interface BookingRepository extends JpaRepository<Booking, UUID> {
    /** 고객 자신의 예약 한 건 — 남의 예약이면 빈 값이라 서비스가 NOT_FOUND 로 답한다 (BookingService.require) */
    Optional<Booking> findByIdAndCustomerId(UUID id, UUID customerId);

    /** 고객의 예약 전부, 시작 시각 최근 순 (예약 탭 목록, 탈퇴한 고객의 녹화본 정리) */
    List<Booking> findByCustomerIdOrderByStartsAtDesc(UUID customerId);

    /** 홈 '다가오는 방문': 열린 예약만 (감사 M-09) */
    List<Booking> findByCustomerIdAndStatusInOrderByStartsAt(UUID customerId, Collection<BookingStatus> statuses);

    /** 한 정기청소의 회차 전부(취소된 회차 포함), 방문일 순 — 예: 예약 상세의 회차 목록, 카드 제목의 'n/전체회' */
    List<Booking> findBySeriesIdOrderByServiceDate(UUID seriesId);

    /** 취소 전에 소유권·시리즈만 확인 — 엔티티를 올리지 않는다 (올려 두면 findForUpdate 가 잠금 뒤에도 옛 상태를 돌려줘 그 사이 수락된 회차를 매니저 없이 취소한다, 검수 3차) */
    // 필요한 칸만 담는 가벼운 결과 모양(프로젝션). getId·getSeriesId 의 이름이 아래 select 의 as 별칭과 맞아야 값이 들어간다
    interface SeriesRef {
        UUID getId();
        UUID getSeriesId();
    }
    // :id·:customerId = 같은 이름의 메서드 인자가 들어가는 자리. 남의 예약이면 빈 값 (BookingService.seriesIdOf 가 NOT_FOUND)
    @Query("select b.id as id, b.seriesId as seriesId from Booking b where b.id = :id and b.customerId = :customerId")
    Optional<SeriesRef> findSeriesRef(UUID id, UUID customerId);

    /** 잠그기 전 시리즈 id 만 — 매니저 완료가 시리즈 → 예약 순서로 잠그게 (자체 점검 #8). 1회 예약이면 빈 값 */
    @Query("select b.seriesId from Booking b where b.id = :id")
    Optional<UUID> findSeriesIdById(UUID id);

    /** 잠그기 전 id 만 (엔티티를 미리 읽어 두면 findForUpdate 가 옛 상태를 그대로 돌려준다) */
    // 부르는 곳: BookingSeriesService.cancelWhole (정기청소 전체 취소 — 받은 id 순서대로 회차를 하나씩 잠근다)
    @Query("select b.id from Booking b where b.seriesId = :seriesId order by b.id") // 잠금 순서 = id 오름차순 (OfferExpirer 와 같다 — 데드락 방지)
    List<UUID> findIdsBySeriesId(UUID seriesId);

    /** 정기 회차 매칭 대상 id (SeriesDispatcher) — 잠그고 읽기 위해 id 만 */
    // 실제로 부르는 곳은 BookingSeriesService.dispatchDue(SeriesDispatcher 가 매일 부른다). between 은 양 끝 날짜를 포함한다
    @Query("select b.id from Booking b where b.seriesId is not null and b.status = :status and b.serviceDate between :from and :to order by b.id") // 잠금 순서 = id 오름차순 (cancelWhole·OfferExpirer 와 같다)
    List<UUID> findSeriesVisitIdsDue(BookingStatus status, LocalDate from, LocalDate to);

    /** 같은 시리즈에서 그 날짜 이전에 매니저가 붙었던 회차의 매니저 (최근 순) — 엔티티를 읽지 않는다 */
    // 부르는 곳: BookingSeriesService.previousManager, BookingService.reschedule — 첫 값을 우선 콜 대상으로 쓴다.
    // JPQL 안에서 enum 값은 전체 클래스 이름(kr.ansimclean…BookingStatus.CANCELLED)으로 쓴다
    @Query("select b.managerId from Booking b where b.seriesId = :seriesId and b.serviceDate < :before and b.managerId is not null and b.status <> kr.ansimclean.api.booking.BookingStatus.CANCELLED order by b.serviceDate desc")
    List<UUID> findPreviousManagerIds(UUID seriesId, LocalDate before);

    /** 고객에게 이 상태들 중 하나인 예약이 있나 (있음/없음만). 탈퇴 가드가 OPEN(끝나지 않음)·ACTIVE(진행 중)로 부른다 */
    boolean existsByCustomerIdAndStatusIn(UUID customerId, Collection<BookingStatus> statuses);

    /** 매니저에게 이 상태들 중 하나인 예약이 있나 — 매니저 탈퇴 가드(BookingService.hasOpenJobs, OPEN) */
    boolean existsByManagerIdAndStatusIn(UUID managerId, Collection<BookingStatus> statuses);

    /** 정기청소 회차 중 아직 매칭을 시작하지 않은 것 (service_date ≤ date) */
    // 지금 서버 코드에서 이 메서드를 부르는 곳은 없다 — 매일 매칭은 위 findSeriesVisitIdsDue(id 만 받아 잠그고 읽기)를 쓴다
    List<Booking> findBySeriesIdIsNotNullAndStatusAndServiceDateLessThanEqual(BookingStatus status, LocalDate date);

    /** 약속 시간이 [from, to) 인데 아직 시작하지 않은 배정 예약 중 '늦어지고 있어요' 를 안 보낸 것 — 잠그기 전 id 만 (LateStartNotifier) */
    @Query("select b.id from Booking b where b.status = kr.ansimclean.api.booking.BookingStatus.SCHEDULED and b.lateNotifiedAt is null and b.startsAt >= :from and b.startsAt < :to order by b.id")
    List<UUID> findLateUnstartedIds(Instant from, Instant to);

    /** 매칭 실패로 멈춘 매칭 중 예약 중 약속 시각이 after 보다 뒤인 것 — 잠그기 전 id 만, id 순 (자동 재매칭 MatchingRetry) */
    @Query("select b.id from Booking b where b.status = kr.ansimclean.api.booking.BookingStatus.ASSIGNING and b.matchingFailedAt is not null and b.startsAt > :after order by b.id")
    List<UUID> findMatchingFailedIds(Instant after);

    /** 밤이라 새 콜을 아침으로 미뤄 둔 매칭 중 예약 — 잠그기 전 id 만, id 순 (아침에 MatchingRetry → OfferService.releaseHeldDispatches, V046) */
    @Query("select b.id from Booking b where b.status = kr.ansimclean.api.booking.BookingStatus.ASSIGNING and b.dispatchHeldAt is not null order by b.id")
    List<UUID> findDispatchHeldIds();

    // ── 탈퇴 + 5년 파기 (BookingAccountPurge, BACKEND_PLAN §13-22). 행은 남기고 개인정보 칸만 비운다 ──
    // 돌려주는 int 는 바뀐 행 수. 아래 SQL 의 '{}' 는 Postgres 의 빈 배열이다.

    /** 주소는 시도·시군구만 남긴다(매니저 이력의 지역 표시용), 자유 입력 칸은 비우고 고객이 쓴 취소 사유는 'customer' 로 */
    // road_address: 시도·시군구를 공백으로 이어 붙이고(concat_ws), 둘 다 비었으면 '(주소 파기)'.
    // cancel_reason: 시스템·고객 사유 코드는 그대로 두고, 그 밖의 값(고객이 적은 자유 입력)만 'customer' 로 바꾼다
    @Modifying
    @Query(value = """
            update bookings set road_address = coalesce(nullif(concat_ws(' ', sido, sigungu), ''), '(주소 파기)'), detail_address = null,
                   request = null, notices = '{}', repair_detail = null, repair_photos = '{}',
                   cancel_reason = case when cancel_reason is null or cancel_reason in ('customer', 'series_cancelled', 'unassigned', 'not_started') then cancel_reason else 'customer' end
             where customer_id = :customerId""", nativeQuery = true)
    int scrubCustomerBookings(UUID customerId);

    /** 고객의 정기청소(booking_series) 행에서 주소·상세 주소·요청·주의사항을 비운다. 이 테이블에는 시도·시군구 칸이 없어 주소는 '(주소 파기)' 로만 바꾼다 */
    @Modifying
    @Query(value = "update booking_series set road_address = '(주소 파기)', detail_address = null, request = null, notices = '{}' where customer_id = :customerId", nativeQuery = true)
    int scrubCustomerSeries(UUID customerId);

    /** 고객이 남긴 상태 이력 사유(취소 사유 자유 입력) — 시스템 사유는 둔다 */
    // 고객 본인이 남긴 이력(actor_id = 고객)만, 그 고객 예약의 이력만 본다. 사유 코드(customer·rescheduled 등)는 남기고 나머지를 null 로
    @Modifying
    @Query(value = """
            update booking_status_history set reason = null
             where actor_id = :customerId and booking_id in (select id from bookings where customer_id = :customerId)
               and reason is not null and reason not in ('customer', 'series_cancelled', 'unassigned', 'not_started', 'rescheduled')""", nativeQuery = true)
    int scrubCustomerHistory(UUID customerId);

    /** 콜 수락·만료 처리의 잠금 (§6.2-3 순서: 매니저 → 예약 → 오퍼) */
    // @Lock(PESSIMISTIC_WRITE) = select … for update (CODE_GUIDE.md 3-4). 수락 말고도 예: 고객 취소·일정 변경이
    // BookingService.lockForUpdate 를 거쳐 이걸 쓴다. 잠그기 전에 같은 예약 엔티티를 읽어 두면 옛 상태가 돌아오므로 id 만 먼저 받는다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findForUpdate(UUID id);

    /** 매니저 자신의 작업 한 건 — 남의 것이면 빈 값 (BookingService.requireManagerBooking 이 NOT_FOUND) */
    Optional<Booking> findByIdAndManagerId(UUID id, UUID managerId);

    /** 그 매니저의 겹치는 작업이 있는가 ([startsAt, endsAt) 교집합, §6.2-1) */
    // 이름 순서대로 인자가 들어간다: 예약.startsAt < 인자 endsAt 이고 예약.endsAt > 인자 startsAt — 그래서 endsAt 인자가 startsAt 보다 앞에 온다
    boolean existsByManagerIdAndStatusInAndStartsAtLessThanAndEndsAtGreaterThan(UUID managerId, Collection<BookingStatus> statuses, Instant endsAt, Instant startsAt);

    /** 매니저의 방문일 [from, to] 예약(양 끝 포함), 시작 시각 순 — BookingService.managerBookings */
    List<Booking> findByManagerIdAndServiceDateBetweenOrderByStartsAt(UUID managerId, LocalDate from, LocalDate to);

    /** 그 상태들 중 첫 한 건 — 진행 중 작업 찾기(BookingService.activeJob, 매니저당 최대 1건) */
    Optional<Booking> findFirstByManagerIdAndStatusIn(UUID managerId, Collection<BookingStatus> statuses);

    /** 그 상태 중 끝난 시각이 가장 늦은 한 건 — 마지막으로 완료한 작업(BookingService.lastCompleted) */
    Optional<Booking> findFirstByManagerIdAndStatusOrderByEndedAtDesc(UUID managerId, BookingStatus status);

    /** 방문일이 지났는데 아직 끝나지 않은 것 (StaleBookingCleaner) */
    // Before = 그 날짜보다 앞(그날은 빠진다). 부르는 쪽이 오늘 또는 어제를 넘겨 기준일을 정한다
    List<Booking> findByStatusInAndServiceDateBefore(Collection<BookingStatus> statuses, LocalDate date);
}
