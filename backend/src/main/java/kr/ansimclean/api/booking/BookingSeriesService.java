package kr.ansimclean.api.booking;

import kr.ansimclean.api.booking.dto.CancelRequest;
import kr.ansimclean.api.booking.dto.ReservationResponse;
import kr.ansimclean.api.catalog.AppSettingsService;
import kr.ansimclean.api.catalog.CatalogService;
import kr.ansimclean.api.common.events.SeriesEnding;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.time.AppClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// 부르는 곳: BookingController(고객 앱의 '정기청소 전체' 취소 → cancelWhole),
// SeriesDispatcher(매일 09:00 서울 → dispatchDue·endExpired·notifyEnding 순서로).
// 회차 하나의 취소·생성·잠금은 BookingService 에 있고, 여기서는 그것을 불러 묶음 단위로 처리한다.
// 트랜잭션(CODE_GUIDE.md 3-3)은 메서드 호출 하나 단위다: dispatchDue 는 그날 매칭할 회차 전부가 한 묶음이라
// 한 회차에서 예외가 나면 그날 매칭이 모두 되돌려지고, SeriesDispatcher 가 로그만 남긴 채 endExpired·notifyEnding 도
// 그날은 건너뛴다(다음 날 09:00 에 다시 한다).
/** 정기청소 묶음 동작 (BACKEND_PLAN.md §6.8): 전체 취소, 회차 매칭(7일 전·기존 매니저 우선), 종료, 종료 안내 */
@Service
@Transactional
public class BookingSeriesService {
    public static final String SETTING_DISPATCH_LEAD_DAYS = "dispatch_lead_days"; // app_settings 키: 정기 회차를 방문 며칠 전에 매칭할지 (V005 기본 7)
    static final int ENDING_NOTICE_DAYS = 7; // 마지막 회차 며칠 전에 '곧 끝나요' 푸시를 보낼지
    private static final Logger log = LoggerFactory.getLogger(BookingSeriesService.class);

    private final BookingRepository bookings;
    private final BookingSeriesRepository series;
    private final BookingService bookingService;
    private final CatalogService catalog;
    private final AppSettingsService settings;
    private final OutboxService outbox;
    private final kr.ansimclean.api.profile.ManagerPenaltyService penalties;
    private final AppClock clock;

    public BookingSeriesService(BookingRepository bookings, BookingSeriesRepository series, BookingService bookingService, CatalogService catalog,
                                AppSettingsService settings, OutboxService outbox, AppClock clock,
                                kr.ansimclean.api.profile.ManagerPenaltyService penalties) {
        this.penalties = penalties;
        this.bookings = bookings;
        this.series = series;
        this.bookingService = bookingService;
        this.catalog = catalog;
        this.settings = settings;
        this.outbox = outbox;
        this.clock = clock;
    }

    /**
     * "정기청소 전체" 취소: 시리즈 cancelled + 시작 전이고 기한 안인 회차 전부 취소. 기한이 지난 회차는 남는다(앱 시트가 안내).
     * @return 요청한 회차의 카드 (그 회차가 기한 안이면 취소돼 있다)
     * 부르는 곳: BookingController 의 POST /api/v1/customer/reservations/{id}/cancel 에서 본문 wholeSeries 가 true 일 때.
     */
    public ReservationResponse cancelWhole(UUID customerId, UUID bookingId, CancelRequest req) {
        UUID seriesId = bookingService.seriesIdOf(customerId, bookingId); // 소유권·시리즈 확인만 — 엔티티를 올리지 않는다 (잠금은 아래에서 id 순서로)
        if (seriesId == null) return bookingService.cancel(customerId, bookingId, req); // 1회 예약이면 보통 취소와 같다
        BookingSeries s = series.lockById(seriesId).orElseThrow(); // 순서: 시리즈 → 예약 id 순 (회차 취소와 같다)
        Instant now = clock.now();
        // 시리즈가 이미 active 가 아니어도(끝남·취소됨) 아래의 회차 취소는 그대로 진행한다
        if (s.isActive()) s.cancel(now);
        int deadline = catalog.cancelDeadlineHours();
        // 시작 전(요청·매칭 중·배정됨)이고 취소 기한(약속 시각까지 deadline 시간 이상 남음) 안인 회차만 취소한다.
        // 진행 중·완료·이미 취소된 회차와 기한이 지난 회차는 그대로 둔다
        for (UUID id : bookings.findIdsBySeriesId(s.getId())) { // id 오름차순 = OfferExpirer 와 같은 잠금 순서 (데드락 방지)
            Booking b = bookingService.lockForUpdate(id); // 회차마다 FOR UPDATE — 동시에 수락된 회차는 잠금 뒤의 실제 상태를 본다
            if (BookingStatus.BEFORE_START.contains(b.getStatus()) && b.isBeforeDeadline(now, deadline)) {
                bookingService.cancelOne(b, customerId, BookingService.REASON_SERIES);
            }
        }
        return bookingService.reservation(customerId, bookingId); // 요청한 회차 한 장의 최신 카드 (위에서 취소됐으면 취소 상태)
    }

    // 결과 = 이번에 매칭을 시작한 회차 수 (SeriesDispatcher 가 로그에 남긴다).
    // 부작용: 회차 상태 requested → assigning, 매니저 콜(오퍼) 생성·푸시(BookingDispatcher 가 처리)
    /** 매일: 방문 dispatch_lead_days 일 전이 된 requested 회차를 매칭. 직전 회차 매니저가 있으면 우선 오퍼 */
    public int dispatchDue() {
        LocalDate today = clock.today();
        LocalDate until = today.plusDays(settings.integer(SETTING_DISPATCH_LEAD_DAYS));
        int count = 0;
        // 이미 지난 회차는 범위 밖(03:10 StaleBookingCleaner 가 취소한다). id 만 받고 회차마다 잠그고 읽는다 — 고객 취소와 경합하면 취소를 assigning 으로 덮어쓰지 않게
        for (UUID id : bookings.findSeriesVisitIdsDue(BookingStatus.REQUESTED, today, until)) {
            Booking b = bookingService.lockForUpdate(id);
            if (b.getStatus() != BookingStatus.REQUESTED) continue; // 잠금을 기다리는 사이 취소됐다
            BookingSeries s = series.findById(b.getSeriesId()).orElse(null);
            if (s == null || !s.isActive()) continue; // 취소된 시리즈에 남은 requested 회차 = cancelWhole 때 이미 기한이 지나 남은 것 (방문일이 지나면 StaleBookingCleaner 가 취소)
            bookingService.startAssigning(b, null, previousManager(b));
            count++;
        }
        return count;
    }

    /** 같은 시리즈에서 이 회차 직전에 매니저가 붙었던 회차의 매니저 (엔티티를 읽지 않는 조회 — 잠근 회차의 상태를 흐리지 않게) */
    UUID previousManager(Booking b) {
        List<UUID> found = bookings.findPreviousManagerIds(b.getSeriesId(), b.getServiceDate());
        if (found.isEmpty()) return null;
        UUID previous = found.get(0);
        // 이 시리즈를 한 번 취소·미방문한 매니저에게는 다음 회차를 먼저 주지 않는다 (docs/manager-policy.md)
        return penalties.droppedSeries(previous, b.getSeriesId()) ? null : previous;
    }

    /** 매일: term_end 가 지난 active 시리즈 → ended */
    public int endExpired() {
        List<BookingSeries> expired = series.findByStatusAndTermEndBefore(BookingSeries.ACTIVE, clock.today()); // term_end 가 오늘이면 아직 끝내지 않는다 (마지막 방문 당일)
        expired.forEach(BookingSeries::end);
        return expired.size();
    }

    /** 매일: 종료 7일 전 시리즈에 SeriesEnding (고객 푸시) 한 번 */
    public int notifyEnding() {
        LocalDate until = clock.today().plusDays(ENDING_NOTICE_DAYS);
        int count = 0;
        // term_end ≤ 오늘 + 7일 이고 아직 안내하지 않은 시리즈. 7일보다 짧게 남은 시리즈도 한 번은 받는다
        for (BookingSeries s : series.findByStatusAndTermEndLessThanEqualAndEndingNotifiedAtIsNull(BookingSeries.ACTIVE, until)) {
            // 고객 푸시 (아웃박스 → PushHandler 의 SeriesEnding)
            outbox.publish(new SeriesEnding(s.getId(), s.getCustomerId()));
            s.markEndingNotified(clock.now()); // 다음 날부터는 조회에서 빠진다 (한 번만 보내기)
            count++;
        }
        if (count > 0) log.info("정기청소 종료 안내 {}건", count);
        return count;
    }
}
