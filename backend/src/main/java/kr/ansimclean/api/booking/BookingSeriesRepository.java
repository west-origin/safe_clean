package kr.ansimclean.api.booking;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * booking_series(정기청소 묶음) 테이블을 읽고 쓰는 창구 (Repository 기본: CODE_GUIDE.md 3-1).
 * booking 패키지 안에서만 쓴다 (예: BookingSeriesService). 다른 패키지는 BookingService 를 거친다.
 */
public interface BookingSeriesRepository extends JpaRepository<BookingSeries, UUID> {
    /** 그 고객에게 이 상태의 시리즈가 있는가 — 탈퇴 차단(BookingService.hasActiveSeries)이 'active' 로 부른다 */
    boolean existsByCustomerIdAndStatus(UUID customerId, String status);

    /** status 이고 term_end < date 인 시리즈 — 매일 09:00 종료 처리(BookingSeriesService.endExpired)가 '오늘' 로 부른다 */
    List<BookingSeries> findByStatusAndTermEndBefore(String status, LocalDate date);

    // @Lock(PESSIMISTIC_WRITE) = select … for update (CODE_GUIDE.md 3-4).
    // 아래 순서(시리즈 → 예약)는 작업 완료(BookingService.lockWithSeries)도 따른다.
    // 시리즈 마감(endSeriesIfNothingLeft)은 회차 상태를 읽기 전에 이 행을 잠근다
    /** 회차 취소·전체 취소는 시리즈를 먼저 잠근다(순서: 시리즈 → 예약 id 순) — 마지막 두 회차를 동시에 취소하면 둘 다 '남은 회차 있음' 으로 보고 시리즈가 active 로 남는다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from BookingSeries s where s.id = :id")
    Optional<BookingSeries> lockById(UUID id);

    /** status 이고 term_end ≤ date 인데 종료 안내를 아직 안 보낸 시리즈 — 종료 7일 전 안내(BookingSeriesService.notifyEnding)용 */
    List<BookingSeries> findByStatusAndTermEndLessThanEqualAndEndingNotifiedAtIsNull(String status, LocalDate date);
}
