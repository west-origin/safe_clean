package kr.ansimclean.api.booking;

import kr.ansimclean.api.booking.dto.CancelRequest;
import kr.ansimclean.api.catalog.AppSettingsService;
import kr.ansimclean.api.catalog.CatalogService;
import kr.ansimclean.api.catalog.dto.PriceQuote;
import kr.ansimclean.api.common.events.SeriesEnding;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.time.AppClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 정기청소 묶음 (BACKEND_PLAN.md §6.8): 전체 취소는 기한 안 회차만, 회차 매칭은 직전 매니저 우선, 종료 안내 1회 */
// BookingSeriesService 단위 테스트. 저장소·BookingService·설정·아웃박스는 Mockito 가짜(mock)이고, 시리즈와 회차 엔티티는 진짜다.
// 가짜 BookingService 의 cancelOne·startAssigning 은 아무것도 하지 않으므로, 회차 상태 대신 '불렸는가' 를 본다.
class BookingSeriesServiceTest {
    static final Instant NOW = Instant.parse("2026-11-01T05:00:00Z"); // 일 14:00 서울
    static final AppClock CLOCK = new AppClock(Clock.fixed(NOW, AppClock.ZONE)); // 멈춘 시계 — 서비스의 '지금'·'오늘(11/1)' 이 늘 같다
    static final UUID CUSTOMER = UUID.randomUUID();
    static final UUID MANAGER = UUID.randomUUID();

    BookingRepository bookings = mock(BookingRepository.class);
    BookingSeriesRepository seriesRepo = mock(BookingSeriesRepository.class);
    BookingService bookingService = mock(BookingService.class);
    CatalogService catalog = mock(CatalogService.class);
    AppSettingsService settings = mock(AppSettingsService.class);
    OutboxService outbox = mock(OutboxService.class);
    BookingSeriesService service;
    BookingSeries series;
    List<Booking> visits = new ArrayList<>();

    // 패널티 가짜: droppedSeries 를 정하지 않아 false — 직전 매니저가 이 시리즈를 취소한 적 없는 것으로 본다
    kr.ansimclean.api.profile.ManagerPenaltyService penalties = mock(kr.ansimclean.api.profile.ManagerPenaltyService.class);

    /**
     * 테스트마다: 기한 24시간·매칭 시점 7일 설정, 11/2 10:00 부터 매월 1회 6회차 시리즈와 그 회차 예약들(전부 requested)을 만들고,
     * 저장소·BookingService 가짜가 이 시리즈와 회차를 돌려주게 연결한다.
     * require·lockCustomerBooking 대역은 지금 BookingSeriesService 가 부르지 않는다(회차 잠금은 lockForUpdate).
     */
    @BeforeEach
    void setUp() {
        service = new BookingSeriesService(bookings, seriesRepo, bookingService, catalog, settings, outbox, CLOCK, penalties);
        when(catalog.cancelDeadlineHours()).thenReturn(24);
        when(settings.integer(BookingSeriesService.SETTING_DISPATCH_LEAD_DAYS)).thenReturn(7);
        // 매월 1회 × 6개월 (사용자 결정 2026-09-22)
        series = BookingSeries.create(CUSTOMER, "home", LocalDate.of(2026, 11, 2), LocalTime.of(10, 0), 6, "주소", null, List.of(), List.of(), List.of(), null, NOW);
        for (LocalDate d : series.visitDates()) {
            visits.add(Booking.create(CUSTOMER, series.getId(), "home", "recurring", d, LocalTime.of(10, 0), "주소", null, null, null,
                    List.of(), List.of(), List.of(), null, null, new PriceQuote(80000, 0, 80000, 8000, 180), NOW));
        }
        when(seriesRepo.findById(series.getId())).thenReturn(Optional.of(series));
        when(seriesRepo.lockById(series.getId())).thenReturn(Optional.of(series)); // 전체 취소는 시리즈를 먼저 잠근다
        when(bookings.findBySeriesIdOrderByServiceDate(series.getId())).thenReturn(visits);
        when(bookings.findIdsBySeriesId(series.getId())).thenReturn(visits.stream().map(Booking::getId).toList());
        for (Booking v : visits) {
            when(bookingService.require(CUSTOMER, v.getId())).thenReturn(v);
            when(bookingService.seriesIdOf(CUSTOMER, v.getId())).thenReturn(v.getSeriesId());
            when(bookingService.lockCustomerBooking(CUSTOMER, v.getId())).thenReturn(v); // 지금은 쓰이지 않는 대역이다 (위 설명)
            when(bookingService.lockForUpdate(v.getId())).thenReturn(v); // 전체 취소·회차 매칭은 이것으로 회차를 잠그고 읽는다
        }
    }

    @Test
    void 전체_취소는_시리즈를_끝내고_24시간_밖의_회차만_취소한다() {
        // 첫 회차 11/2 10:00 은 지금(11/1 14:00)부터 20시간 뒤 → 기한 안이 아니므로 남는다
        service.cancelWhole(CUSTOMER, visits.get(1).getId(), new CancelRequest(null, true));
        assertThat(series.getStatus()).isEqualTo(BookingSeries.CANCELLED);
        verify(bookingService, never()).cancelOne(eq(visits.get(0)), any(), any());
        for (Booking v : visits.subList(1, visits.size())) verify(bookingService).cancelOne(v, CUSTOMER, BookingService.REASON_SERIES);
        verify(bookingService).reservation(CUSTOMER, visits.get(1).getId()); // 응답은 요청한 회차(두 번째)의 카드
    }

    // 첫 회차를 MANAGER 에게 배정해 두고, 범위 조회가 인자 (requested, 오늘 11/1, 7일 뒤 11/8) 로 불릴 때만 두 번째 회차를
    // 돌려주게 한다 (실제 날짜 12/2 와 상관없이 가짜가 돌려준다) — 서비스가 범위를 이렇게 계산해야 1건이 나온다.
    // → 직전 매니저 조회가 MANAGER 면 startAssigning 의 우선 매니저 칸에 MANAGER 가 들어가야 한다
    @Test
    void 회차_매칭은_7일_안의_requested_만_하고_직전_회차_매니저에게_우선한다() {
        visits.get(0).beginAssigning();
        visits.get(0).assign(MANAGER, NOW);
        when(bookings.findSeriesVisitIdsDue(BookingStatus.REQUESTED, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 8))).thenReturn(List.of(visits.get(1).getId()));
        when(bookings.findPreviousManagerIds(series.getId(), visits.get(1).getServiceDate())).thenReturn(List.of(MANAGER));
        assertThat(service.dispatchDue()).isEqualTo(1);
        verify(bookingService).startAssigning(visits.get(1), null, MANAGER);
    }

    // findPreviousManagerIds 를 정하지 않아 가짜가 빈 목록을 돌려준다 → 우선 매니저 없이(null) 매칭
    @Test
    void 직전_매니저가_없으면_일반_매칭() {
        when(bookings.findSeriesVisitIdsDue(BookingStatus.REQUESTED, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 8))).thenReturn(List.of(visits.get(1).getId()));
        service.dispatchDue();
        verify(bookingService).startAssigning(visits.get(1), null, null);
    }

    // 회차는 requested 그대로지만 시리즈가 cancelled 면 dispatchDue 가 건너뛴다 (any() = 어떤 인자로 불러도)
    @Test
    void 취소된_시리즈의_회차는_매칭하지_않는다() {
        series.cancel(NOW);
        when(bookings.findSeriesVisitIdsDue(any(), any(), any())).thenReturn(List.of(visits.get(1).getId()));
        assertThat(service.dispatchDue()).isZero();
        verify(bookingService, never()).startAssigning(any(), any(), any());
    }

    @Test
    void 잠금을_기다리는_사이_취소된_회차는_매칭하지_않는다() {
        // 조회 → (고객 취소 커밋) → 잠금 뒤 다시 본 상태가 cancelled 면 건너뛴다 (취소를 assigning 으로 덮어쓰던 경쟁)
        visits.get(1).cancelBySystem("customer", NOW);
        when(bookings.findSeriesVisitIdsDue(any(), any(), any())).thenReturn(List.of(visits.get(1).getId()));
        assertThat(service.dispatchDue()).isZero();
        verify(bookingService, never()).startAssigning(any(), any(), any());
    }

    // 조회(term_end ≤ 11/8 이고 아직 안내하지 않은 active 시리즈)가 이 시리즈를 돌려주게 정하면 → 아웃박스에 SeriesEnding 한 건 + 안내 시각 표시.
    // 표시가 남아 다음 날 조회(…EndingNotifiedAtIsNull)에서 빠지는 것이 '한 번만' 의 근거다
    @Test
    void 종료_안내는_한_번만_나간다() {
        when(seriesRepo.findByStatusAndTermEndLessThanEqualAndEndingNotifiedAtIsNull(BookingSeries.ACTIVE, LocalDate.of(2026, 11, 8))).thenReturn(List.of(series));
        assertThat(service.notifyEnding()).isEqualTo(1);
        verify(outbox).publish(new SeriesEnding(series.getId(), CUSTOMER));
        assertThat(series.getEndingNotifiedAt()).isEqualTo(NOW);
    }

    // 조회(term_end 가 오늘 11/1 보다 앞인 active 시리즈)가 이 시리즈를 돌려주게 정하면 ended 로 바뀐다
    @Test
    void 기간이_지난_시리즈는_ended() {
        when(seriesRepo.findByStatusAndTermEndBefore(BookingSeries.ACTIVE, LocalDate.of(2026, 11, 1))).thenReturn(List.of(series));
        assertThat(service.endExpired()).isEqualTo(1);
        assertThat(series.getStatus()).isEqualTo(BookingSeries.ENDED);
    }
}
