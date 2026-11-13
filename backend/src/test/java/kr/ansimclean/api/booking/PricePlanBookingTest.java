package kr.ansimclean.api.booking;

import kr.ansimclean.api.booking.dto.BookingResponse;
import kr.ansimclean.api.booking.dto.CreateBookingRequest;
import kr.ansimclean.api.booking.dto.QuoteRequest;
import kr.ansimclean.api.booking.dto.QuoteResponse;
import kr.ansimclean.api.catalog.AppSettingsService;
import kr.ansimclean.api.catalog.CatalogOption;
import kr.ansimclean.api.catalog.CatalogPriceRepository;
import kr.ansimclean.api.catalog.CatalogService;
import kr.ansimclean.api.catalog.PricingService;
import kr.ansimclean.api.catalog.ServiceCategory;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.web.Idempotent;
import kr.ansimclean.api.profile.FirstCleaningService;
import kr.ansimclean.api.profile.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 요금표 전환 (2026-11-13, docs/domain.md '요금표 전환'): 가정·원룸·분해청소 예약이 요금표 요금제로 만들어지는지.
 * PricingService 는 진짜(요금표 JSON 은 V074 와 같은 값), 나머지 협력자는 mock. 시계는 BookingServiceTest 와 같은 9/15(화) 14:00 서울.
 */
class PricePlanBookingTest {
    static final String PLANS = """
            {"house":{"hours":3,"trial":50000,"weekly":{"1":329000,"2":599000,"3":889000}},
             "studio":{"hours":2,"once":69000,"weekly":{"1":229000,"2":399000,"3":599000},"firstMonth":{"1":199000}},
             "hood":{"sizes":{"600":130000,"900":150000,"1200":200000}},"weeks":4}""";

    BookingRepository bookings = mock(BookingRepository.class);
    BookingSeriesRepository seriesRepo = mock(BookingSeriesRepository.class);
    CatalogService catalog = mock(CatalogService.class);
    AppSettingsService settings = mock(AppSettingsService.class);
    Idempotent idempotent = mock(Idempotent.class);
    FirstCleaningService firstCleaning = mock(FirstCleaningService.class);
    BookingService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        JsonMapper json = JsonMapper.builder().build();
        when(settings.node(PricingService.SETTING_PRICE_PLANS)).thenReturn(json.readTree(PLANS));
        when(settings.node(PricingService.SETTING_DURATION_HOURS)).thenReturn(json.readTree("{\"appliance\":2}"));
        when(settings.decimal(PricingService.SETTING_FEE_RATE)).thenReturn(0.1);
        when(settings.integer(BookingService.SETTING_SERIES_TERM_MONTHS)).thenReturn(6);
        when(settings.get(BookingService.SETTING_CLEANER_PENDING, String.class)).thenReturn("배정 후 알려드려요");
        PricingService pricing = new PricingService(mock(CatalogPriceRepository.class), settings);
        service = new BookingService(bookings, seriesRepo, mock(BookingStatusHistoryRepository.class), catalog, pricing, settings, mock(ProfileService.class),
                mock(BookingDispatcher.class), mock(RecordingLookup.class), mock(OutboxService.class), idempotent, BookingServiceTest.CLOCK,
                new kr.ansimclean.api.common.web.RateLimiter(BookingServiceTest.CLOCK), mock(RepairPhotoService.class));
        service.setFirstCleaning(firstCleaning);
        ServiceCategory category = mock(ServiceCategory.class);
        when(category.getLabel()).thenReturn("가정/원룸 청소");
        when(catalog.requireCategory(anyString())).thenReturn(category);
        when(catalog.isTimeSlot(anyString())).thenReturn(true);
        when(catalog.bookableDays()).thenReturn(60);
        when(catalog.requireOptions(anyString(), anyList(), anyList())).thenReturn(List.<CatalogOption>of());
        when(bookings.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(seriesRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(idempotent.run(any(), any(), any(), any(), any())).thenAnswer(inv -> ((Supplier<Object>) inv.getArgument(4)).get());
    }

    /** 11/2(월) 10:00 가정/원룸 요청 */
    static CreateBookingRequest home(String frequency, String homeType, Integer perWeek, List<Integer> weekdays) {
        return new CreateBookingRequest("home", frequency, "2026-11-02", "10:00", "서울특별시 강남구 테헤란로 123", "3층", List.of(), List.of(), List.of(),
                null, null, null, false, homeType, 5, perWeek, weekdays, null);
    }

    List<Booking> savedVisits() {
        ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
        verify(bookings, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        return saved.getAllValues();
    }

    @Test
    void 원룸_주_2회는_고른_요일마다_4주_8회이고_한_달_금액을_나눠_고정한다() {
        when(seriesRepo.hasStudioPlan(any())).thenReturn(true);
        BookingResponse res = service.create(BookingServiceTest.CUSTOMER, "k1", home("recurring", "studio", 2, List.of(0, 3))); // 월·목
        assertThat(res.visits()).extracting(BookingResponse.Visit::date).containsExactly(
                "2026-11-02", "2026-11-05", "2026-11-09", "2026-11-12", "2026-11-16", "2026-11-19", "2026-11-23", "2026-11-26");
        assertThat(res.pricePlan()).isEqualTo("studio_weekly_2");
        List<Booking> visits = savedVisits();
        assertThat(visits).extracting(Booking::getPricePlan).containsOnly("studio_weekly_2");
        assertThat(visits).extracting(Booking::getDurationMinutes).containsOnly(120); // 고른 청소 시간(5)은 무시 — 원룸 2시간
        assertThat(visits.stream().mapToLong(Booking::getTotalAmount).sum()).isEqualTo(399000);
        assertThat(res.totalAmount()).isEqualTo(399000);
        ArgumentCaptor<BookingSeries> series = ArgumentCaptor.forClass(BookingSeries.class);
        verify(seriesRepo).save(series.capture());
        assertThat(series.getValue().getPricePlan()).isEqualTo("studio_weekly_2");
        assertThat(series.getValue().getPlanAmount()).isEqualTo(399000);
        assertThat(series.getValue().getTermEnd()).hasToString("2026-11-26");
        assertThat(series.getValue().getVisitsPlanned()).isEqualTo(8);
        verify(seriesRepo, never()).advisoryLock(anyString()); // 첫 달 판정은 원룸 주 1회만
    }

    @Test
    void 원룸_주_1회는_첫_원룸_정기면_첫_달_199000원() {
        when(seriesRepo.hasStudioPlan(BookingServiceTest.CUSTOMER)).thenReturn(false);
        BookingResponse first = service.create(BookingServiceTest.CUSTOMER, "k1", home("recurring", "studio", 1, List.of(0)));
        assertThat(first.totalAmount()).isEqualTo(199000);
        verify(seriesRepo).advisoryLock("studio-first:" + BookingServiceTest.CUSTOMER); // 잠근 뒤에 판정한다
        when(seriesRepo.hasStudioPlan(BookingServiceTest.CUSTOMER)).thenReturn(true);
        assertThat(service.create(BookingServiceTest.CUSTOMER, "k2", home("recurring", "studio", 1, List.of(0))).totalAmount()).isEqualTo(229000);
    }

    @Test
    void 요일은_주_N회에_맞는_개수이고_첫_방문일_요일을_포함해야_한다() {
        assertThatThrownBy(() -> service.create(BookingServiceTest.CUSTOMER, "k1", home("recurring", "house", 2, List.of(0))))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.VALIDATION);
        assertThatThrownBy(() -> service.create(BookingServiceTest.CUSTOMER, "k2", home("recurring", "house", 1, List.of(1)))) // 화요일인데 첫 방문은 월요일
                .isInstanceOf(ApiException.class).hasMessage("첫 방문일은 고른 요일 중 하나여야 해요");
        assertThatThrownBy(() -> service.create(BookingServiceTest.CUSTOMER, "k3", home("recurring", "house", 2, List.of(0, 0))))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.VALIDATION);
        assertThatThrownBy(() -> service.create(BookingServiceTest.CUSTOMER, "k4", home("recurring", "house", 4, List.of(0, 1, 2, 3))))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.VALIDATION); // 주 4회 이상은 상담
        verify(bookings, never()).save(any());
    }

    @Test
    void 가정_1회는_첫_체험_50000원이고_체험_자격을_쓴다_원룸_1회는_69000원이고_쓰지_않는다() {
        BookingResponse trial = service.create(BookingServiceTest.CUSTOMER, "k1", home("oneTime", "house", null, null));
        assertThat(trial.totalAmount()).isEqualTo(50000);
        assertThat(trial.pricePlan()).isEqualTo("house_trial");
        verify(firstCleaning).claim(eq(BookingServiceTest.CUSTOMER), any(), anyString(), any());
        org.mockito.Mockito.clearInvocations(firstCleaning);
        BookingResponse once = service.create(BookingServiceTest.CUSTOMER, "k2", home("oneTime", "studio", null, null));
        assertThat(once.totalAmount()).isEqualTo(69000);
        assertThat(once.pricePlan()).isEqualTo("studio_once");
        verify(firstCleaning, never()).claim(any(), any(), any(), any());
    }

    @Test
    void 가정_원룸은_종류가_필요하다() {
        assertThatThrownBy(() -> service.create(BookingServiceTest.CUSTOMER, "k1", home("oneTime", null, null, null)))
                .isInstanceOf(ApiException.class).hasMessage("가정 청소와 원룸 청소 중 골라주세요");
    }

    @Test
    void 분해청소는_후드_크기_요금이고_1회만() {
        CreateBookingRequest hood = new CreateBookingRequest("appliance", "oneTime", "2026-11-02", "10:00", "서울특별시 강남구 테헤란로 123", null,
                List.of(), List.of(), List.of(), null, null, null, false, null, null, null, null, "1200");
        BookingResponse res = service.create(BookingServiceTest.CUSTOMER, "k1", hood);
        assertThat(res.totalAmount()).isEqualTo(200000);
        assertThat(res.pricePlan()).isEqualTo("hood_1200");
        assertThat(savedVisits().get(0).getDurationMinutes()).isEqualTo(120); // 카테고리 기본 소요
        CreateBookingRequest noSize = new CreateBookingRequest("appliance", "oneTime", "2026-11-02", "10:00", "서울특별시 강남구 테헤란로 123", null,
                List.of(), List.of(), List.of(), null, null, null, false, null, null, null, null, null);
        assertThatThrownBy(() -> service.create(BookingServiceTest.CUSTOMER, "k2", noSize)).hasMessage("후드 크기를 골라주세요");
    }

    @Test
    void 견적도_같은_요금제_계산이고_원룸_첫_달은_할인_안내() {
        QuoteResponse q = service.quote(new QuoteRequest("home", "recurring", "2026-11-02", "10:00", List.of(), List.of(), null, "house", 3, List.of(0, 2, 4), null));
        assertThat(q.visitsPlanned()).isEqualTo(12);
        assertThat(q.totalAmount()).isEqualTo(889000);
        assertThat(q.perVisit().total()).isEqualTo(74087); // 첫 회차가 나머지 4원을 갖는다
        assertThat(q.savingNote()).isNull();
        QuoteResponse studio = service.quote(new QuoteRequest("home", "recurring", "2026-11-02", "10:00", List.of(), List.of(), null, "studio", 1, List.of(0), null));
        assertThat(studio.totalAmount()).isEqualTo(199000);
        assertThat(studio.savingNote()).isEqualTo("첫 달 30,000원 할인");
    }
}
