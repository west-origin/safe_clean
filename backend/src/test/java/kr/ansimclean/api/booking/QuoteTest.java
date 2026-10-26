package kr.ansimclean.api.booking;

import kr.ansimclean.api.booking.dto.QuoteRequest;
import kr.ansimclean.api.booking.dto.QuoteResponse;
import kr.ansimclean.api.catalog.AppSettingsService;
import kr.ansimclean.api.catalog.CatalogOption;
import kr.ansimclean.api.catalog.CatalogService;
import kr.ansimclean.api.catalog.PricingService;
import kr.ansimclean.api.catalog.ServiceCategory;
import kr.ansimclean.api.catalog.dto.PriceQuote;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.common.web.Idempotent;
import kr.ansimclean.api.profile.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 견적 (B3 완료 기준 QuoteTest): 1회·정기 금액, 회차 날짜, 일정 규칙 */
// BookingService.quote 와 BookingSeries 의 날짜 계산을 DB 없이 본다. 카탈로그·가격·설정은 Mockito 가짜(mock)다.
// 가짜 설정: 운영 시간대는 10:00 하나, 예약 가능 60일, 1회 100,000원 / 정기 회당 85,000원, 정기 6개월.
class QuoteTest {
    /** 2026-09-15(화) 14:00 서울 */
    static final AppClock CLOCK = new AppClock(Clock.fixed(Instant.parse("2026-09-15T05:00:00Z"), AppClock.ZONE));

    CatalogService catalog = mock(CatalogService.class);
    PricingService pricing = mock(PricingService.class);
    AppSettingsService settings = mock(AppSettingsService.class);
    BookingService service;

    /** 테스트마다 서비스를 새로 만든다. 견적에 쓰지 않는 협력자(저장소·디스패처·아웃박스 등)는 빈 가짜로 채운다 */
    @BeforeEach
    void setUp() {
        service = new BookingService(mock(BookingRepository.class), mock(BookingSeriesRepository.class), mock(BookingStatusHistoryRepository.class),
                catalog, pricing, settings, mock(ProfileService.class), mock(BookingDispatcher.class), mock(RecordingLookup.class), mock(OutboxService.class), mock(Idempotent.class), CLOCK,
                new kr.ansimclean.api.common.web.RateLimiter(CLOCK), mock(RepairPhotoService.class));
        when(catalog.requireCategory("home")).thenReturn(mock(ServiceCategory.class));
        // 뒤에 정한 것이 이긴다: 어떤 시각이든 false, 그중 "10:00" 만 true
        when(catalog.isTimeSlot(anyString())).thenReturn(false);
        when(catalog.isTimeSlot("10:00")).thenReturn(true);
        when(catalog.bookableDays()).thenReturn(60);
        when(catalog.requireOptions(eq("home"), anyList(), anyList())).thenReturn(List.<CatalogOption>of());
        when(pricing.quote(eq("home"), eq("oneTime"), any())).thenReturn(new PriceQuote(100000, 0, 100000, 10000, 180));
        when(pricing.quote(eq("home"), eq("recurring"), any())).thenReturn(new PriceQuote(80000, 5000, 85000, 8500, 180));
        when(settings.integer(BookingService.SETTING_SERIES_TERM_MONTHS)).thenReturn(6); // 매월 1회 × 6개월 (사용자 결정 2026-09-22)
        when(settings.get(BookingService.SETTING_PAYMENT_NOTE, String.class)).thenReturn("결제는 청소 완료 후 진행돼요");
    }

    // 1회 견적: 방문 1개(날짜 문구 포함), 총액 = 1회 금액, 결제 안내는 설정 문구 그대로
    @Test
    void 일회_견적은_방문_1개와_1회_금액() {
        QuoteResponse q = service.quote(new QuoteRequest("home", "oneTime", "2026-11-02", "10:00", List.of(), List.of()));
        assertThat(q.perVisit().label()).isEqualTo("100,000원");
        assertThat(q.visits()).hasSize(1);
        assertThat(q.visits().get(0).label()).isEqualTo("2026년 11월 2일 (월)");
        assertThat(q.visitsPlanned()).isEqualTo(1);
        assertThat(q.totalLabel()).isEqualTo("100,000원");
        assertThat(q.paymentNote()).isEqualTo("결제는 청소 완료 후 진행돼요");
    }

    @Test
    void 정기_견적은_매월_같은_날짜_6회차와_총액() {
        // 2026-11-02 부터 매월 1회 6회. 2027-01-02 토요일도 날짜를 유지한다.
        QuoteResponse q = service.quote(new QuoteRequest("home", "recurring", "2026-11-02", "10:00", List.of("dishes"), List.of("fridge")));
        assertThat(q.visits()).extracting(QuoteResponse.Visit::date).containsExactly("2026-11-02", "2026-12-02", "2027-01-02", "2027-02-02", "2027-03-02", "2027-04-02");
        assertThat(q.visitsPlanned()).isEqualTo(6);
        assertThat(q.perVisit().total()).isEqualTo(85000);
        assertThat(q.totalAmount()).isEqualTo(510000); // 85,000 × 6회
        assertThat(q.totalLabel()).isEqualTo("510,000원");
    }

    @Test
    void 없는_날짜만_그_달_마지막_날로_가고_주말은_그대로_유지한다() {
        assertThat(BookingSeries.monthlyDates(LocalDate.of(2027, 1, 29), 3))
                .containsExactly(LocalDate.of(2027, 1, 29), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 29));
        assertThat(BookingSeries.monthlyDates(LocalDate.of(2027, 1, 31), 3))
                .containsExactly(LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31));
        // 저장된 시리즈(term_start ~ term_end)에서 다시 만들어도 같은 목록
        assertThat(BookingSeries.visitDates(LocalDate.of(2026, 11, 2), LocalDate.of(2027, 4, 2))).hasSize(6);
    }

    // 주말도 견적 가능. 최소 예약 간격·운영 시간대·날짜 형식 제한은 유지한다.
    @Test
    void 주말은_허용하고_과거와_슬롯_밖은_SLOT_UNAVAILABLE() {
        assertThat(service.quote(new QuoteRequest("home", "oneTime", "2026-11-01", "10:00", List.of(), List.of())).visits().get(0).date()).isEqualTo("2026-11-01");
        assertThatThrownBy(() -> service.quote(new QuoteRequest("home", "oneTime", "2026-09-15", "10:00", List.of(), List.of()))) // 오늘
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.SLOT_UNAVAILABLE);
        assertThatThrownBy(() -> service.quote(new QuoteRequest("home", "oneTime", "2026-09-16", "10:30", List.of(), List.of()))) // 슬롯 밖
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.SLOT_UNAVAILABLE);
        assertThatThrownBy(() -> service.quote(new QuoteRequest("home", "oneTime", "2026-13-01", "10:00", List.of(), List.of())))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.VALIDATION);
    }

    // 오늘(9/15) 다음 날 9/16(수) 10:00 은 예약 가능한 가장 이른 날이다
    @Test
    void 내일은_된다() {
        QuoteResponse q = service.quote(new QuoteRequest("home", "oneTime", "2026-09-16", "10:00", List.of(), List.of()));
        assertThat(q.visits().get(0).date()).isEqualTo("2026-09-16");
    }
}
