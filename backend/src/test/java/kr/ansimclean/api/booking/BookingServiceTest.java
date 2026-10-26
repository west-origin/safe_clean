package kr.ansimclean.api.booking;

import kr.ansimclean.api.booking.dto.BookingResponse;
import kr.ansimclean.api.booking.dto.CancelRequest;
import kr.ansimclean.api.booking.dto.CreateBookingRequest;
import kr.ansimclean.api.booking.dto.ReservationResponse;
import kr.ansimclean.api.booking.dto.ScheduleChangeRequest;
import kr.ansimclean.api.catalog.AppSettingsService;
import kr.ansimclean.api.catalog.CatalogOption;
import kr.ansimclean.api.catalog.CatalogService;
import kr.ansimclean.api.catalog.PricingService;
import kr.ansimclean.api.catalog.ServiceCategory;
import kr.ansimclean.api.catalog.dto.PriceQuote;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.events.BookingCancelled;
import kr.ansimclean.api.common.events.BookingRescheduled;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.common.web.Idempotent;
import kr.ansimclean.api.profile.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 생성·취소·변경의 규칙과 부수 효과 (BACKEND_PLAN.md §6.1·§6.8). 협력자는 mock, 엔티티는 진짜 */
// BookingService 단위 테스트 (DB·스프링 없이). 시계는 9/15(화) 14:00 서울에 멈춰 있다.
// ArgumentCaptor = 가짜에 넘어간 인자를 붙잡아 꺼내 보는 도구(저장된 Booking 을 확인할 때), any()/eq() = 인자 조건.
// 시계와 시간당 한도(RateLimiter)는 진짜다 — 한도는 setUp 이 테스트마다 새로 만들어 횟수가 테스트끼리 섞이지 않는다.
class BookingServiceTest {
    @Test
    void 주말에_시작하는_정기청소도_선택한_날짜로_생성한다() {
        CreateBookingRequest weekend = new CreateBookingRequest("home", "recurring", "2026-09-19", "10:00", "서울특별시 강남구 테헤란로 123", "3층",
                List.of(), List.of(), List.of(), null, null, null, false);
        BookingResponse result = service.create(CUSTOMER, "weekend-series", weekend);
        assertThat(result.visits()).extracting(BookingResponse.Visit::date)
                .containsExactly("2026-09-19", "2026-10-19", "2026-11-19", "2026-12-19", "2027-01-19", "2027-02-19");
    }

    @Test
    void 일정_변경도_새_방문_시간은_3시간_이상_남아야_한다() {
        Booking b = existing(BookingStatus.SCHEDULED);
        assertThatThrownBy(() -> service.reschedule(CUSTOMER, b.getId(), new ScheduleChangeRequest("2026-09-15", "16:00")))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.SLOT_UNAVAILABLE);
        service.reschedule(CUSTOMER, b.getId(), new ScheduleChangeRequest("2026-09-15", "17:00"));
        assertThat(b.getServiceDate()).isEqualTo(CLOCK.today());
        assertThat(b.getServiceTime()).isEqualTo(LocalTime.of(17, 0));
    }

    @Test
    void 청소는_현재부터_정확히_3시간이면_당일도_가능하다() {
        for (String category : List.of("home", "office", "appliance", "move-in")) {
            service.requireBookableSchedule(category, CLOCK.today(), LocalTime.of(17, 0), CLOCK.today());
            assertThatThrownBy(() -> service.requireBookableSchedule(category, CLOCK.today(), LocalTime.of(16, 59), CLOCK.today()))
                    .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.SLOT_UNAVAILABLE);
        }
    }

    @Test
    void 청소는_시간이_흐르면_같은_선택도_서버에서_다시_거부한다() {
        AppClock later = new AppClock(Clock.fixed(CLOCK.now().plusSeconds(1), AppClock.ZONE));
        service = new BookingService(bookings, seriesRepo, history, catalog, pricing, settings, profiles, dispatcher,
                mock(RecordingLookup.class), outbox, idempotent, later, new kr.ansimclean.api.common.web.RateLimiter(later), photoUploads);
        assertThatThrownBy(() -> service.requireBookableSchedule("home", CLOCK.today(), LocalTime.of(17, 0), CLOCK.today()))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.SLOT_UNAVAILABLE);
    }

    @Test
    void 청소_생성과_견적은_3시간_미만이면_거부하고_저장하지_않는다() {
        CreateBookingRequest early = new CreateBookingRequest("home", "oneTime", "2026-09-15", "16:00", "서울특별시 강남구 테헤란로 123", "3층",
                List.of(), List.of(), List.of(), null, null, null, false);
        assertThatThrownBy(() -> service.create(CUSTOMER, "early", early))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.SLOT_UNAVAILABLE);
        assertThatThrownBy(() -> service.quote(new kr.ansimclean.api.booking.dto.QuoteRequest("home", "oneTime", "2026-09-15", "16:00", List.of(), List.of())))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.SLOT_UNAVAILABLE);
        verify(bookings, never()).save(any());
    }

    @Test
    void 주말도_허용하고_수리의_당일_예약_금지와_운영시간_제한은_유지한다() {
        assertThatThrownBy(() -> service.requireBookableSchedule("repair", CLOCK.today(), LocalTime.of(18, 0), CLOCK.today()))
                .isInstanceOf(ApiException.class).hasMessage("내일부터 예약할 수 있어요");
        for (String category : List.of("home", "office", "appliance", "move-in", "repair")) {
            service.requireBookableSchedule(category, LocalDate.of(2026, 9, 19), LocalTime.of(17, 0), CLOCK.today());
            service.requireBookableSchedule(category, LocalDate.of(2026, 9, 20), LocalTime.of(17, 0), CLOCK.today());
        }
        when(catalog.isTimeSlot("23:00")).thenReturn(false);
        assertThatThrownBy(() -> service.requireBookableSchedule("home", CLOCK.today(), LocalTime.of(23, 0), CLOCK.today()))
                .isInstanceOf(ApiException.class).hasMessage("선택할 수 없는 시간이에요");
    }

    static final AppClock CLOCK = new AppClock(Clock.fixed(Instant.parse("2026-09-15T05:00:00Z"), AppClock.ZONE)); // 화 14:00 서울
    static final UUID CUSTOMER = UUID.randomUUID();
    static final UUID MANAGER = UUID.randomUUID();
    static final PriceQuote QUOTE = new PriceQuote(80000, 0, 80000, 8000, 180); // 기본 80,000 · 옵션 0 · 합계 80,000 · 수수료 8,000 · 180분

    BookingRepository bookings = mock(BookingRepository.class);
    BookingSeriesRepository seriesRepo = mock(BookingSeriesRepository.class);
    BookingStatusHistoryRepository history = mock(BookingStatusHistoryRepository.class);
    CatalogService catalog = mock(CatalogService.class);
    PricingService pricing = mock(PricingService.class);
    AppSettingsService settings = mock(AppSettingsService.class);
    ProfileService profiles = mock(ProfileService.class);
    BookingDispatcher dispatcher = mock(BookingDispatcher.class);
    OutboxService outbox = mock(OutboxService.class);
    Idempotent idempotent = mock(Idempotent.class);
    RepairPhotoService photoUploads = mock(RepairPhotoService.class);
    BookingService service;

    /**
     * 테스트마다 서비스를 새로 만들고 가짜들의 기본 답을 정한다: 카테고리 '가정/원룸 청소', 모든 시각이 운영 시간대,
     * 예약 가능 60일, 기한 24시간, 옵션 검증 통과, 견적 QUOTE, 정기 6개월, 매니저 칸 문구 두 개.
     * save 는 받은 객체를 그대로 돌려주고, 멱등 처리는 건너뛰고 업무를 바로 실행한다.
     * 붙은 SuppressWarnings("unchecked") 는 아래 Supplier 형변환의 컴파일 경고를 끈다
     */
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new BookingService(bookings, seriesRepo, history, catalog, pricing, settings, profiles, dispatcher, mock(RecordingLookup.class), outbox, idempotent, CLOCK,
                new kr.ansimclean.api.common.web.RateLimiter(CLOCK), photoUploads);
        ServiceCategory home = mock(ServiceCategory.class);
        when(home.getLabel()).thenReturn("가정/원룸 청소");
        when(catalog.requireCategory("home")).thenReturn(home);
        when(catalog.isTimeSlot(anyString())).thenReturn(true);
        when(catalog.bookableDays()).thenReturn(60);
        when(catalog.cancelDeadlineHours()).thenReturn(24);
        when(catalog.requireOptions(anyString(), anyList(), anyList())).thenReturn(List.<CatalogOption>of());
        when(pricing.quote(anyString(), anyString(), any())).thenReturn(QUOTE);
        when(settings.integer(BookingService.SETTING_SERIES_TERM_MONTHS)).thenReturn(6); // 매월 1회 × 6개월 (사용자 결정 2026-09-22)
        when(settings.get(BookingService.SETTING_CLEANER_PENDING, String.class)).thenReturn("배정 후 알려드려요");
        when(settings.get(BookingService.SETTING_CLEANER_MATCHING_FAILED, String.class)).thenReturn("매니저를 찾고 있어요 · 조금 더 걸릴 수 있어요");
        when(bookings.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(seriesRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // Idempotent 는 그냥 실행 (알고리즘은 IdempotencyIT)
        when(idempotent.run(any(), any(), any(), any(), any())).thenAnswer(inv -> ((Supplier<Object>) inv.getArgument(4)).get());
    }

    /** 생성 요청: 11/2(월) 10:00 가정 청소, 무료 옵션 dishes, 주의사항 pet, 요청사항은 앞뒤 공백 포함(저장 때 잘리는지 본다), 주소 저장 */
    static CreateBookingRequest request(String frequency) {
        return new CreateBookingRequest("home", frequency, "2026-11-02", "10:00", "서울특별시 강남구 테헤란로 123", "3층",
                List.of("dishes"), List.of(), List.of("pet"), " 잘 부탁드려요 ", null, null, true);
    }

    // 매칭 중 예약에 실패 표시를 하고 상세(get)를 부른다. 약속(11/2)이 멀어 자동 재매칭이 아직 볼 수 있으므로
    // 매니저 칸은 SETTING_CLEANER_MATCHING_FAILED 문구다. 주의: 최종 실패 문구 설정은 정하지 않아(null) 약속이 가까워도
    // 같은 문구가 나온다 — 최종 실패 쪽 분기는 아래 cleanerLabel 테스트가 본다
    @Test
    void 예약_상세도_매칭_실패면_목록과_같은_실패_안내를_보인다() { // 예전에는 늘 '배정 후 알려드려요' (자체 점검 후속 검토)
        Booking b = existing(BookingStatus.ASSIGNING);
        b.markMatchingFailed(CLOCK.now());
        assertThat(service.get(CUSTOMER, b.getId()).cleaner()).isEqualTo("매니저를 찾고 있어요 · 조금 더 걸릴 수 있어요"); // 이 테스트의 실패 문구 설정값
    }

    // 매칭에 실패한 채 매니저 없이 고객이 취소한 예약을 다시 연다(예: 예약 완료 화면으로 돌아옴).
    // 상세의 매니저 칸도 목록 카드와 같은 '배정 전 취소' 여야 한다 — 예전에는 매칭 실패·배정 전 문구가 나왔다
    @Test
    void 매니저_없이_취소된_예약의_상세_매니저_칸은_목록과_같은_배정_전_취소() { // 문제 목록 9번
        Booking b = existing(BookingStatus.ASSIGNING);
        b.markMatchingFailed(CLOCK.now());
        service.cancel(CUSTOMER, b.getId(), new CancelRequest(null, false));
        assertThat(service.get(CUSTOMER, b.getId()).cleaner()).isEqualTo("배정 전 취소");
        assertThat(service.reservation(CUSTOMER, b.getId()).rows().get(2).value()).isEqualTo("배정 전 취소"); // 목록 카드와 같은 글자
        assertThat(service.get(CUSTOMER, b.getId()).cancelled()).isTrue(); // 수리 완료 화면이 이 값으로 '배정 중' 대신 위 문구를 보인다 (합친 38건 검토 R16)
    }

    // 매칭 실패 표시 없이(콜이 나가 있는 중) 취소된 예약도 상세가 cancelled=true 와 '배정 전 취소' 를 준다 —
    // 수리 완료 화면은 matchingFailed 만 보고 '배정 중' 을 그렸다 (합친 38건 검토 R16)
    @Test
    void 매칭_실패_없이_취소된_예약의_상세는_cancelled_와_배정_전_취소를_준다() {
        Booking b = existing(BookingStatus.ASSIGNING);
        service.cancel(CUSTOMER, b.getId(), new CancelRequest(null, false));
        BookingResponse res = service.get(CUSTOMER, b.getId());
        assertThat(res.cancelled()).isTrue();
        assertThat(res.matchingFailed()).isFalse();
        assertThat(res.cleaner()).isEqualTo("배정 전 취소");
    }

    // cleanerLabel(예약, 지금, 배정 전 문구, 실패 문구, 최종 실패 문구)을 직접 부른다.
    // 실패 표시가 있으면 '지금' 에 따라 실패/최종 실패 문구 중 하나, 표시가 없으면 배정 전 문구
    @Test
    void 매칭_실패_카드는_자동_재매칭이_아직_볼_수_있을_때만_다시_찾는다고_한다() { // 자체 점검 후속 검토
        Booking b = existing(BookingStatus.ASSIGNING, null, LocalDate.of(2026, 11, 2));
        b.markMatchingFailed(CLOCK.now());
        assertThat(BookingService.cleanerLabel(b, CLOCK.now(), "배정 후", "다시 찾아요", "문의해주세요")).isEqualTo("다시 찾아요");
        assertThat(BookingService.cleanerLabel(b, b.getStartsAt().minusSeconds(3600), "배정 후", "다시 찾아요", "문의해주세요")).isEqualTo("문의해주세요"); // 약속 1시간 전
        Booking notFailed = existing(BookingStatus.ASSIGNING);
        assertThat(BookingService.cleanerLabel(notFailed, CLOCK.now(), "배정 후", "다시 찾아요", "문의해주세요")).isEqualTo("배정 후");
    }

    @Test
    void 같은_옵션_key_가_두_번_오면_검증_가격_저장_전부_한_번으로_본다() {
        CreateBookingRequest req = new CreateBookingRequest("home", "oneTime", "2026-11-02", "10:00", "서울특별시 강남구 테헤란로 123", null,
                List.of("dishes", "dishes"), List.of("fridge", "fridge"), List.of(), null, null, null, false);
        service.create(CUSTOMER, "key", req);

        verify(catalog).requireOptions("home", List.of("dishes"), List.of("fridge")); // 한도 검사도 중복 제거 뒤
        ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
        verify(bookings).save(saved.capture());
        assertThat(saved.getValue().getFreeOptions()).containsExactly("dishes"); // 작업 상세 '청소 범위' 에 같은 옵션이 두 줄로 나오지 않게
        assertThat(saved.getValue().getPaidOptions()).containsExactly("fridge");
    }

    // 세 가지를 차례로: 내 사진 경로(고객 id 로 시작)로 만든 수리 예약의 저장 값 → 남의 사진 경로는 VALIDATION
    // → frequency 를 비운 수리 예약도 VALIDATION 이고 시리즈는 저장되지 않는다
    @Test
    void 수리_예약은_가전_증상_상세_사진을_저장하고_남의_사진_경로는_거부한다() {
        ServiceCategory repair = mock(ServiceCategory.class);
        when(repair.getLabel()).thenReturn("수리 서비스");
        when(catalog.requireCategory("repair")).thenReturn(repair);
        List<String> mine = List.of(CUSTOMER + "/a.jpg", CUSTOMER + "/a.jpg", CUSTOMER + "/b.jpg"); // 중복은 한 번
        CreateBookingRequest req = new CreateBookingRequest("repair", "oneTime", "2026-11-02", "10:00", "서울특별시 강남구 테헤란로 123", "604동 1002호",
                List.of(), List.of(), List.of("parking", "elevator"), "방문 10분 전에 연락 부탁드려요.", "appliance",
                new CreateBookingRequest.Repair("냉장고", "냉장이 잘 안 돼요", "fridge", "cooling", "냉장실 온도가 올라가요", mine), false);
        service.create(CUSTOMER, "key", req);

        ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
        verify(bookings).save(saved.capture());
        Booking b = saved.getValue();
        assertThat(b.isRepair()).isTrue();
        assertThat(b.getRepairType()).isEqualTo("appliance");
        assertThat(b.getRepairAppliance()).isEqualTo("냉장고");
        assertThat(b.getRepairSymptom()).isEqualTo("냉장이 잘 안 돼요");
        assertThat(b.getRepairApplianceKey()).isEqualTo("fridge"); // 키는 문구와 나란히 (V017)
        assertThat(b.getRepairSymptomKey()).isEqualTo("cooling");
        assertThat(b.getRepairPhotos()).containsExactly(CUSTOMER + "/a.jpg", CUSTOMER + "/b.jpg");
        verify(photoUploads).attach(CUSTOMER, List.of(CUSTOMER + "/a.jpg", CUSTOMER + "/b.jpg")); // 24시간 정리에서 빠진다 — 붙은 수 확인도 이 함수가 (감사 M-04·재감사 2차 #3)
        assertThat(b.getNotices()).containsExactly("parking", "elevator");
        assertThat(b.requestLines()).containsExactly("가전: 냉장고", "증상: 냉장이 잘 안 돼요", "냉장실 온도가 올라가요", "사진 2장 첨부", "방문 10분 전에 연락 부탁드려요."); // 기사가 보는 요청사항

        CreateBookingRequest theirs = new CreateBookingRequest("repair", "oneTime", "2026-11-03", "10:00", "서울특별시 강남구 테헤란로 123", null,
                List.of(), List.of(), List.of(), null, "appliance", new CreateBookingRequest.Repair("냉장고", null, null, null, null, List.of(UUID.randomUUID() + "/x.jpg")), false);
        assertThatThrownBy(() -> service.create(CUSTOMER, "key2", theirs)).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.VALIDATION);

        // 수리는 1회만 — frequency 를 비우면 recurring 으로 채워지므로 시리즈가 생기지 않게 거부한다 (검수 3차)
        CreateBookingRequest recurring = new CreateBookingRequest("repair", null, "2026-11-03", "10:00", "서울특별시 강남구 테헤란로 123", null,
                List.of(), List.of(), List.of(), null, "appliance", new CreateBookingRequest.Repair("냉장고", null, null, null, null, List.of()), false);
        assertThatThrownBy(() -> service.create(CUSTOMER, "key3", recurring)).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.VALIDATION);
        verify(seriesRepo, never()).save(any());
    }

    // 탈퇴와 줄 서는 첫 줄(profiles.holdLive)이 401(UNAUTHENTICATED)을 던지게 하면 → 예약 저장·매칭이 하나도 없어야 한다
    @Test
    void 탈퇴가_먼저_커밋된_고객의_예약은_아무것도_만들지_않는다() { // 감사 M-06
        when(profiles.holdLive(CUSTOMER)).thenThrow(new kr.ansimclean.api.common.error.ApiException(kr.ansimclean.api.common.error.ErrorCode.UNAUTHENTICATED));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.create(CUSTOMER, "key", request("oneTime")))
                .isInstanceOf(kr.ansimclean.api.common.error.ApiException.class);
        verify(bookings, never()).save(any());
        verify(dispatcher, never()).dispatch(any(), any());
    }

    // 멱등 키를 매번 다르게 해(재시도가 아닌 새 예약) 한도만큼 만들고, 그다음 한 건이 RATE_LIMITED 인지 본다
    @Test
    void 같은_고객의_예약_생성은_시간당_10건까지() { // 감사 M-05 — 예약 한 건이 후보 전원 오퍼·푸시로 커진다
        for (int i = 0; i < kr.ansimclean.api.common.web.RateLimiter.BOOKING_PER_USER_PER_HOUR; i++) service.create(CUSTOMER, "key-" + i, request("oneTime"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.create(CUSTOMER, "key-over", request("oneTime")))
                .isInstanceOf(kr.ansimclean.api.common.error.ApiException.class).extracting("code").isEqualTo(kr.ansimclean.api.common.error.ErrorCode.RATE_LIMITED);
    }

    // 저장된 예약 한 건(상태·주소에서 뽑은 시도·시군구·앞뒤 공백을 뺀 요청사항)과 부수 효과
    // (매칭 시작, 시리즈 없음, 주소 기억, 상태 이력 두 줄), 응답 문구를 한 번에 본다
    @Test
    void 일회_예약은_1건_만들고_바로_매칭을_시작한다() {
        BookingResponse res = service.create(CUSTOMER, "key", request("oneTime"));

        ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
        verify(bookings, times(1)).save(saved.capture());
        Booking b = saved.getValue();
        assertThat(b.getStatus()).isEqualTo(BookingStatus.ASSIGNING);
        assertThat(b.getSido()).isEqualTo("서울특별시");
        assertThat(b.getSigungu()).isEqualTo("강남구");
        assertThat(b.getRequest()).isEqualTo("잘 부탁드려요");
        verify(dispatcher).dispatch(b, Set.of());
        verify(seriesRepo, never()).save(any());
        verify(profiles).rememberAddress(CUSTOMER, "서울특별시 강남구 테헤란로 123", "3층");
        verify(history, times(2)).save(any()); // null→requested, requested→assigning
        assertThat(res.serviceLabel()).isEqualTo("가정/원룸 청소");
        assertThat(res.cleaner()).isEqualTo("배정 후 알려드려요");
        assertThat(res.matchingFailed()).isFalse(); // 후보에게 콜이 나갔다 — 매칭 실패가 아니다
        assertThat(res.cancelled()).isFalse(); // 방금 만든 예약
        assertThat(res.seriesId()).isNull();
        assertThat(res.visits()).hasSize(1);
        assertThat(res.totalLabel()).isEqualTo("80,000원");
    }

    // 후보가 한 명도 없어 생성 트랜잭션 안에서 매칭 실패로 표시되면(dispatcher 가짜가 표시한다) 생성 응답도 그 사실을 싣는다 —
    // 수리 예약 완료 화면이 이 값을 보고 Figma '배정 중' 대신 서버의 실패 안내(cleaner)를 보인다
    @Test
    void 생성_중에_매칭_실패하면_응답에_matchingFailed_와_실패_안내를_싣는다() { // 문제 목록 8번
        doAnswer(inv -> inv.<Booking>getArgument(0).markMatchingFailed(CLOCK.now())).when(dispatcher).dispatch(any(), any());
        BookingResponse res = service.create(CUSTOMER, "key", request("oneTime"));
        assertThat(res.matchingFailed()).isTrue();
        assertThat(res.managerAssigned()).isFalse();
        assertThat(res.cleaner()).isEqualTo("매니저를 찾고 있어요 · 조금 더 걸릴 수 있어요"); // 이 테스트의 실패 문구 설정값
        ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
        verify(bookings).save(saved.capture());
        when(bookings.findByIdAndCustomerId(res.id(), CUSTOMER)).thenReturn(Optional.of(saved.getValue()));
        assertThat(service.get(CUSTOMER, res.id()).matchingFailed()).isTrue(); // 다시 열어도(상세) 같다
    }

    // 정기 6개월 설정이라 회차 6건이 저장된다. 첫 회차만 assigning, 나머지는 requested(방문 7일 전에 스케줄러가 매칭)
    @Test
    void 정기_예약은_시리즈와_매월_1회_회차를_만들고_첫_회차만_매칭한다() {
        BookingResponse res = service.create(CUSTOMER, "key", request("recurring"));

        ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
        verify(bookings, times(6)).save(saved.capture());
        List<Booking> visits = saved.getAllValues();
        assertThat(visits.get(0).getStatus()).isEqualTo(BookingStatus.ASSIGNING);
        assertThat(visits.subList(1, 6)).allMatch(v -> v.getStatus() == BookingStatus.REQUESTED);
        assertThat(visits).allMatch(v -> v.getSeriesId() != null && v.getSeriesId().equals(res.seriesId()));
        verify(dispatcher, times(1)).dispatch(any(), any());
        assertThat(res.visits()).extracting(BookingResponse.Visit::date).containsExactly("2026-11-02", "2026-12-02", "2027-01-02", "2027-02-02", "2027-03-02", "2027-04-02"); // 토요일도 선택일 유지
        assertThat(res.totalAmount()).isEqualTo(480000);
        assertThat(res.perVisitLabel()).isEqualTo("80,000원");
    }

    // 카탈로그 가짜가 a·b·c 옵션에 VALIDATION 을 던지게 해 두고, 서비스가 그 오류를 그대로 내보내며 저장하지 않는지 본다
    @Test
    void 무료_옵션_초과와_모르는_key_는_카탈로그가_VALIDATION_으로_막는다() {
        when(catalog.requireOptions(eq("home"), eq(List.of("a", "b", "c")), anyList())).thenThrow(ApiException.validation("무료 옵션은 2개까지 고를 수 있어요"));
        CreateBookingRequest req = new CreateBookingRequest("home", "oneTime", "2026-11-02", "10:00", "서울특별시 강남구 테헤란로 123", null, List.of("a", "b", "c"), List.of(), List.of(), null, null, null, false);
        assertThatThrownBy(() -> service.create(CUSTOMER, null, req)).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.VALIDATION);
        verify(bookings, never()).save(any());
    }

    @Test
    void 첨부_사진이_정리로_지워졌으면_예약을_만들지_않는다() { // 재감사 2차 #3 — 확인 함수를 만들어 놓고 예약 생성이 부르지 않았다
        // doThrow(…).when(가짜).메서드(…) = 돌려줄 값이 없는(void) 메서드가 예외를 던지게 정한다
        org.mockito.Mockito.doThrow(ApiException.validation(RepairPhotoService.PHOTO_EXPIRED)).when(photoUploads).attach(eq(CUSTOMER), anyList());
        CreateBookingRequest req = new CreateBookingRequest("repair", "oneTime", "2026-11-02", "10:00", "서울특별시 강남구 테헤란로 123", null,
                List.of(), List.of(), List.of(), null, "appliance", new CreateBookingRequest.Repair("냉장고", null, null, null, null, List.of(CUSTOMER + "/old.jpg")), false);
        assertThatThrownBy(() -> service.create(CUSTOMER, null, req)).isInstanceOf(ApiException.class).hasMessage(RepairPhotoService.PHOTO_EXPIRED);
        verify(dispatcher, never()).dispatch(any(), any()); // 트랜잭션 안에서 던져 예약은 롤백된다
    }

    @Test
    void 시도를_읽을_수_없는_주소는_받지_않는다() { // 직접 사용 점검 U1 — "강남대로 396" 은 매칭 후보가 0명이라 영영 배정되지 않았다
        CreateBookingRequest req = new CreateBookingRequest("home", "oneTime", "2026-11-02", "10:00", "강남대로 396", "101동", List.of(), List.of(), List.of(), null, null, null, false);
        assertThatThrownBy(() -> service.create(CUSTOMER, null, req)).isInstanceOf(ApiException.class)
                .hasMessage(kr.ansimclean.api.profile.AddressRegion.MISSING_SIDO).extracting("code").isEqualTo(ErrorCode.VALIDATION);
        verify(bookings, never()).save(any());
        // 같은 잘못된 주소로 한도만큼 더 실패시킨다 — 주소 검사가 한도 검사보다 앞이라 횟수가 쌓이지 않아야 한다
        for (int i = 0; i < kr.ansimclean.api.common.web.RateLimiter.BOOKING_PER_USER_PER_HOUR; i++) {
            assertThatThrownBy(() -> service.create(CUSTOMER, null, req)).isInstanceOf(ApiException.class);
        }
        service.create(CUSTOMER, null, request("oneTime")); // 고쳐 다시 보내도 예약 한도(시간당 10건)가 줄어 있지 않다
    }

    /** 이미 있는 1회 예약 (11/2 10:00) — 아래 세 인자 버전의 짧은 꼴 */
    Booking existing(BookingStatus status) {
        return existing(status, null);
    }

    /** seriesId 가 있으면 정기 회차 (11/2 10:00) */
    Booking existing(BookingStatus status, UUID seriesId) {
        return existing(status, seriesId, LocalDate.of(2026, 11, 2));
    }

    /**
     * 이미 저장돼 있는 예약을 흉내 낸다: 엔티티를 만들어 status 까지 옮기고(requested·assigning·scheduled 만 된다),
     * 저장소 가짜의 소유자 조회·시리즈 조회·잠금 조회가 이 예약을 돌려주게 한다.
     * 매니저 이름 조회는 무엇을 물어도 'MANAGER → 김미영' 한 줄짜리 표를 돌려준다 (다른 매니저 id 는 이름이 없다)
     */
    Booking existing(BookingStatus status, UUID seriesId, LocalDate date) {
        Booking b = Booking.create(CUSTOMER, seriesId, "home", "oneTime", date, LocalTime.of(10, 0), "서울특별시 강남구 테헤란로 123", null,
                "서울특별시", "강남구", List.of(), List.of(), List.of(), null, null, QUOTE, CLOCK.now());
        if (status != BookingStatus.REQUESTED) b.beginAssigning();
        if (status == BookingStatus.SCHEDULED) b.assign(MANAGER, CLOCK.now());
        when(bookings.findByIdAndCustomerId(b.getId(), CUSTOMER)).thenReturn(Optional.of(b));
        // SeriesRef = id·seriesId 두 칸만 읽는 조회 결과 모양(인터페이스). 여기서는 익명 클래스로 그 자리에서 구현한다
        when(bookings.findSeriesRef(b.getId(), CUSTOMER)).thenReturn(Optional.of(new BookingRepository.SeriesRef() {
            public UUID getId() { return b.getId(); }
            public UUID getSeriesId() { return b.getSeriesId(); }
        }));
        when(bookings.findForUpdate(b.getId())).thenReturn(Optional.of(b)); // 취소·변경은 잠그고 읽는다
        when(profiles.namesOf(any())).thenReturn(Map.of(MANAGER, "김미영"));
        return b;
    }

    // 배정된 예약을 사유 없이 취소 → 사유 'customer', 옛 콜 정리(onCancelled), 매니저에게 BookingCancelled,
    // 카드의 매니저 행은 취소 뒤에도 그 매니저 이름
    @Test
    void 배정_후_취소는_오퍼를_정리하고_매니저에게_알린다() {
        Booking b = existing(BookingStatus.SCHEDULED);
        ReservationResponse res = service.cancel(CUSTOMER, b.getId(), new CancelRequest(null, false));
        assertThat(b.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(b.getCancelReason()).isEqualTo("customer");
        verify(dispatcher).onCancelled(b);
        verify(outbox).publish(new BookingCancelled(b.getId(), CUSTOMER, MANAGER, "customer"));
        assertThat(res.tab()).isEqualTo("cancelled");
        assertThat(res.status()).isEqualTo("cancelled");
        assertThat(res.rows().get(2).value()).isEqualTo("김미영 매니저");
    }

    @Test
    void 배정된_예약을_자유_입력_사유로_취소하면_이벤트에는_사유_코드만_싣는다() { // 자체 점검 후속 검토 — 아웃박스 원장에 번호·주소가 남지 않게
        Booking b = existing(BookingStatus.SCHEDULED);
        service.cancel(CUSTOMER, b.getId(), new CancelRequest("이사 가요 010-1234-5678", false));
        assertThat(b.getCancelReason()).isEqualTo("이사 가요 010-1234-5678"); // 예약 행에는 남는다(5년 뒤 파기)
        verify(outbox).publish(new BookingCancelled(b.getId(), CUSTOMER, MANAGER, "customer"));
    }

    // 고객이 사유로 시스템 값 'not_started'(미방문 취소)를 보내도 'customer' 로 바꿔 저장·발행한다 — 미방문 알림이 잘못 나가지 않게
    @Test
    void 고객이_시스템_사유를_보내도_고객_취소로_남아_미방문_푸시가_나가지_않는다() { // 자체 점검 #57
        Booking b = existing(BookingStatus.SCHEDULED);
        service.cancel(CUSTOMER, b.getId(), new CancelRequest(StaleBookingCleaner.REASON_NOT_STARTED, false));
        assertThat(b.getCancelReason()).isEqualTo("customer");
        verify(outbox).publish(new BookingCancelled(b.getId(), CUSTOMER, MANAGER, "customer"));
    }

    // 앞부분: 고객 취소를 두 번 보내면 두 번째도 취소 카드이고 알림은 한 번.
    // 뒷부분: 시스템이 먼저 취소한 예약(unassigned)에 고객 취소를 보내면 INVALID_STATE
    @Test
    void 이미_고객이_취소한_예약을_다시_취소하면_같은_카드를_돌려준다_시스템_취소는_막는다() { // 자체 점검 #58
        Booking b = existing(BookingStatus.SCHEDULED);
        service.cancel(CUSTOMER, b.getId(), new CancelRequest(null, false));
        ReservationResponse again = service.cancel(CUSTOMER, b.getId(), new CancelRequest(null, false)); // 응답이 늦어 다시 눌렀다
        assertThat(again.status()).isEqualTo("cancelled");
        verify(outbox, times(1)).publish(any(BookingCancelled.class)); // 두 번 알리지 않는다

        Booking stale = existing(BookingStatus.ASSIGNING);
        stale.cancelBySystem(StaleBookingCleaner.REASON_UNASSIGNED, CLOCK.now());
        assertThatThrownBy(() -> service.cancel(CUSTOMER, stale.getId(), new CancelRequest(null, false)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.INVALID_STATE);
    }

    // 매니저가 없으면 알릴 사람이 없어 아웃박스 발행이 없고, 고객이 적은 사유는 그대로 저장된다.
    // 다른 고객 id 로 같은 예약을 취소하면 NOT_FOUND (남의 예약은 있다는 것도 알리지 않는다)
    @Test
    void 배정_전_취소는_푸시가_없고_남의_예약은_NOT_FOUND() {
        Booking b = existing(BookingStatus.ASSIGNING);
        service.cancel(CUSTOMER, b.getId(), new CancelRequest("사정이 생겼어요", false));
        verify(outbox, never()).publish(any());
        assertThat(b.getCancelReason()).isEqualTo("사정이 생겼어요");
        assertThatThrownBy(() -> service.cancel(UUID.randomUUID(), b.getId(), new CancelRequest(null, false)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.NOT_FOUND);
    }

    // 같은 일정으로 20번 → 한도에 세지 않는다. 11/3 의 두 시각을 번갈아 한도만큼 바꾸면 통과하고, 그다음 변경은 RATE_LIMITED
    @Test
    void 일정_변경도_시간당_한도를_넘으면_막고_같은_일정은_세지_않는다() { // 자체 점검 #20
        Booking b = existing(BookingStatus.ASSIGNING);
        for (int i = 0; i < 20; i++) service.reschedule(CUSTOMER, b.getId(), new ScheduleChangeRequest("2026-11-02", "10:00")); // 같은 일정은 세지 않는다
        for (int i = 0; i < kr.ansimclean.api.common.web.RateLimiter.RESCHEDULE_PER_USER_PER_HOUR; i++) {
            service.reschedule(CUSTOMER, b.getId(), new ScheduleChangeRequest("2026-11-03", i % 2 == 0 ? "14:00" : "10:00"));
        }
        assertThatThrownBy(() -> service.reschedule(CUSTOMER, b.getId(), new ScheduleChangeRequest("2026-11-04", "10:00")))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.RATE_LIMITED);
    }

    // 배정된 예약의 일정을 바꾸면 assigning 으로 돌아가고, 옛 콜 정리(onRescheduled) + 원래 매니저에게 BookingRescheduled
    @Test
    void 일정_변경은_scheduled_면_오퍼를_되돌리고_이전_매니저에게_알린다() {
        Booking b = existing(BookingStatus.SCHEDULED);
        ReservationResponse res = service.reschedule(CUSTOMER, b.getId(), new ScheduleChangeRequest("2026-11-03", "14:00"));
        assertThat(b.getStatus()).isEqualTo(BookingStatus.ASSIGNING);
        verify(dispatcher).onRescheduled(b, MANAGER);
        verify(outbox).publish(new BookingRescheduled(b.getId(), CUSTOMER, MANAGER, false)); // 목(mock) 디스패처는 우선 오퍼를 안 보냈다고 답한다
        assertThat(res.status()).isEqualTo("assigning");
        assertThat(res.rows().get(0).value()).isEqualTo("2026년 11월 3일 (화) 오후 2시");
        assertThat(res.rows().get(2).value()).isEqualTo("배정 후 알려드려요");
    }

    // 밤이라 디스패처가 새 콜을 아침으로 미뤘으면(보류 표시) 원래 매니저 안내를 지금 적지 않는다 — 콜이 없는데 '새 콜을 확인해주세요' 가 가지 않게.
    // 안내는 아침에 우선 콜을 보낼 때 OfferService 가 적는다 (문제 목록 2번)
    @Test
    void 밤이라_매칭을_미뤘으면_이전_매니저_안내도_지금_보내지_않는다() {
        Booking b = existing(BookingStatus.SCHEDULED);
        org.mockito.Mockito.doAnswer(inv -> {
            ((Booking) inv.getArgument(0)).holdDispatch(CLOCK.now(), inv.getArgument(1), true); // 진짜 디스패처가 밤에 하는 일
            return false;
        }).when(dispatcher).onRescheduled(any(), any());
        ReservationResponse res = service.reschedule(CUSTOMER, b.getId(), new ScheduleChangeRequest("2026-11-03", "14:00"));
        verify(outbox, never()).publish(any(BookingRescheduled.class));
        assertThat(res.rows().get(2).value()).isEqualTo("배정 후 알려드려요"); // 고객 카드는 배정 전 문구 그대로
    }

    // 밤에 일정을 바꿔 원래 매니저 안내를 아침으로 미룬 뒤 아침 전에 취소했다 → 그 매니저에게 '예약이 취소됐어요'(BookingCancelled)를 지금 적는다.
    // 취소 안내는 새 콜이 아니라 밤에도 보낸다(밤에 배정된 예약을 취소할 때와 같다). 예전에는 일정 변경이 매니저 칸을 비워 BookingCancelled 를 적지 않았고,
    // onCancelled 가 보류를 지워 아침 안내도 사라져 그 매니저는 아무 알림도 받지 못했다 (합친 38건 검토 R1·R14).
    // 정기청소 다음 회차의 우선 매니저 보류(일정 변경이 아님)는 이 예약을 맡은 적이 없어 알리지 않는다
    @Test
    void 밤에_일정을_바꾼_뒤_아침_전에_취소하면_원래_매니저에게_취소를_알린다() {
        Booking b = existing(BookingStatus.SCHEDULED);
        doAnswer(inv -> {
            ((Booking) inv.getArgument(0)).holdDispatch(CLOCK.now(), inv.getArgument(1), true); // 진짜 디스패처가 밤에 하는 일
            return false;
        }).when(dispatcher).onRescheduled(any(), any());
        doAnswer(inv -> {
            ((Booking) inv.getArgument(0)).clearDispatchHold(); // 진짜 디스패처처럼 취소 때 보류를 지운다
            return null;
        }).when(dispatcher).onCancelled(any());
        service.reschedule(CUSTOMER, b.getId(), new ScheduleChangeRequest("2026-11-03", "14:00"));
        assertThat(b.getManagerId()).isNull();

        service.cancel(CUSTOMER, b.getId(), new CancelRequest(null, false));
        verify(outbox).publish(new BookingCancelled(b.getId(), CUSTOMER, MANAGER, "customer"));

        Booking preferredHeld = existing(BookingStatus.ASSIGNING);
        preferredHeld.holdDispatch(CLOCK.now(), MANAGER, false); // 정기청소 직전 매니저에게 아침에 줄 우선 콜
        service.cancel(CUSTOMER, preferredHeld.getId(), new CancelRequest(null, false));
        verify(outbox, times(1)).publish(any(BookingCancelled.class)); // 더 늘지 않았다
    }

    // 세 경우를 차례로: 완료 + 배정 남음 → active 유지 / 완료 + 취소 → ended / 취소뿐인 다른 시리즈 → cancelled.
    // 시리즈 잠금(lockById)과 회차 조회가 경우마다 다른 목록을 돌려주게 바꿔 가며 endSeriesIfNothingLeft 를 부른다
    @Test
    void 남은_회차가_없으면_시리즈를_닫는다_마친_회차가_있으면_끝남_전부_취소면_취소() { // 자체 점검 #8
        BookingSeries series = BookingSeries.create(CUSTOMER, "home", LocalDate.of(2026, 10, 5), LocalTime.of(10, 0), 2,
                "서울특별시 강남구 테헤란로 123", null, List.of(), List.of(), List.of(), null, CLOCK.now());
        when(seriesRepo.lockById(series.getId())).thenReturn(Optional.of(series));
        Booking done = TestBookings.inProgress(CUSTOMER, MANAGER, LocalDate.of(2026, 10, 5), LocalTime.of(10, 0), CLOCK.now());
        done.complete(CLOCK.now());
        Booking scheduled = existing(BookingStatus.SCHEDULED, series.getId());

        when(bookings.findBySeriesIdOrderByServiceDate(series.getId())).thenReturn(List.of(done, scheduled));
        service.endSeriesIfNothingLeft(series.getId(), CLOCK.now());
        assertThat(series.isActive()).isTrue(); // 아직 남은 회차가 있다

        Booking cancelled = existing(BookingStatus.ASSIGNING, series.getId());
        cancelled.cancel("customer", CLOCK.now(), 24);
        when(bookings.findBySeriesIdOrderByServiceDate(series.getId())).thenReturn(List.of(done, cancelled));
        service.endSeriesIfNothingLeft(series.getId(), CLOCK.now());
        assertThat(series.getStatus()).isEqualTo(BookingSeries.ENDED);

        BookingSeries other = BookingSeries.create(CUSTOMER, "home", LocalDate.of(2026, 10, 5), LocalTime.of(10, 0), 2,
                "서울특별시 강남구 테헤란로 123", null, List.of(), List.of(), List.of(), null, CLOCK.now());
        when(seriesRepo.lockById(other.getId())).thenReturn(Optional.of(other));
        when(bookings.findBySeriesIdOrderByServiceDate(other.getId())).thenReturn(List.of(cancelled));
        service.endSeriesIfNothingLeft(other.getId(), CLOCK.now());
        assertThat(other.getStatus()).isEqualTo(BookingSeries.CANCELLED);
    }

    @Test
    void 아직_매칭_전인_회차의_일정_변경은_오퍼_정리도_알림도_없고_기간_밖이면_거부한다() {
        // REQUESTED 는 정기 회차에만 있다 — 시리즈를 붙이고 매칭 시점(dispatch_lead_days) 밖의 날짜로 옮긴다
        BookingSeries series = BookingSeries.create(CUSTOMER, "home", LocalDate.of(2026, 10, 5), LocalTime.of(10, 0), 2,
                "서울특별시 강남구 테헤란로 123", null, List.of(), List.of(), List.of(), null, CLOCK.now()); // 10/5 · 11/5 → term_end = 11/5
        Booking b = existing(BookingStatus.REQUESTED, series.getId());
        when(seriesRepo.findById(series.getId())).thenReturn(Optional.of(series));
        when(settings.integer(BookingSeriesService.SETTING_DISPATCH_LEAD_DAYS)).thenReturn(7);

        service.reschedule(CUSTOMER, b.getId(), new ScheduleChangeRequest("2026-11-03", "14:00"));
        verify(dispatcher, never()).onRescheduled(any(), any());
        verify(outbox, never()).publish(any());
        assertThat(b.getStatus()).isEqualTo(BookingStatus.REQUESTED);

        assertThatThrownBy(() -> service.reschedule(CUSTOMER, b.getId(), new ScheduleChangeRequest("2026-11-06", "14:00")))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.SLOT_UNAVAILABLE); // 기간 밖으로 미루면 영영 매칭되지 않는다
    }

    @Test
    void 예약_가능_기간_밖의_정기_회차도_지금_방문일부터_기간_안으로는_바꿀_수_있다() { // 전체 점검 G3 — 60일 뒤 회차는 고를 날짜가 없었다
        BookingSeries series = BookingSeries.create(CUSTOMER, "home", LocalDate.of(2026, 10, 5), LocalTime.of(10, 0), 6,
                "서울특별시 강남구 테헤란로 123", null, List.of(), List.of(), List.of(), null, CLOCK.now()); // term_end 2027-03-05
        Booking far = existing(BookingStatus.REQUESTED, series.getId(), LocalDate.of(2026, 12, 7)); // 오늘(9/15)부터 83일 — 기간(60일) 밖
        when(seriesRepo.findById(series.getId())).thenReturn(Optional.of(series));
        when(settings.integer(BookingSeriesService.SETTING_DISPATCH_LEAD_DAYS)).thenReturn(7);

        service.reschedule(CUSTOMER, far.getId(), new ScheduleChangeRequest("2026-12-08", "14:00"));
        assertThat(far.getServiceDate()).isEqualTo(LocalDate.of(2026, 12, 8));

        assertThatThrownBy(() -> service.reschedule(CUSTOMER, far.getId(), new ScheduleChangeRequest("2027-02-08", "14:00"))) // 12/8 + 62일
                .isInstanceOf(ApiException.class).hasMessage("지금 방문일(12월 8일)부터 60일 안의 날짜로만 바꿀 수 있어요");
    }

    // 지금과 같은 날짜·시각(11/2 10:00)으로 바꾸면 배정 매니저를 풀지 않고 콜 정리도 하지 않는다
    @Test
    void 같은_일정으로의_변경은_아무것도_바꾸지_않는다() {
        Booking b = existing(BookingStatus.SCHEDULED);
        service.reschedule(CUSTOMER, b.getId(), new ScheduleChangeRequest("2026-11-02", "10:00"));
        assertThat(b.getStatus()).isEqualTo(BookingStatus.SCHEDULED);
        assertThat(b.getManagerId()).isEqualTo(MANAGER);
        verify(dispatcher, never()).onRescheduled(any(), any());
    }

    // 시리즈 없는 requested 예약으로 줄여 확인한다. 매칭 시점을 60일로 넓혀 새 일정이 그 안에 들게 하고,
    // 직전 매니저 조회는 가짜가 빈 목록을 돌려주므로 일반 매칭(dispatch)이 불린다
    @Test
    void 매칭_전_회차라도_매칭_시점_안으로_당기면_바로_매칭을_시작한다() {
        Booking b = existing(BookingStatus.REQUESTED);
        when(settings.integer(BookingSeriesService.SETTING_DISPATCH_LEAD_DAYS)).thenReturn(60);
        // CLOCK 은 2026-09-15 — 11/2 는 60일 안 (매칭 시점 안)
        service.reschedule(CUSTOMER, b.getId(), new ScheduleChangeRequest("2026-11-02", "14:00"));
        assertThat(b.getStatus()).isEqualTo(BookingStatus.ASSIGNING);
        verify(dispatcher).dispatch(b, java.util.Set.of());
    }

    @Test
    void 일정_변경도_주말을_선택할_수_있다() {
        Booking b = existing(BookingStatus.ASSIGNING);
        service.reschedule(CUSTOMER, b.getId(), new ScheduleChangeRequest("2026-11-07", "14:00"));
        assertThat(b.getServiceDate()).isEqualTo(LocalDate.of(2026, 11, 7));
        verify(dispatcher).onRescheduled(b, null);
    }

    // 열린 예약 조회가 늦은 것(9/25)·이른 것(9/18) 순으로 돌려줘도 가장 이른 9/18 카드가 나오고, 전체 이력 조회는 부르지 않는다
    @Test
    void 홈의_다가오는_방문은_열린_예약만_읽어_가장_이른_것() { // 감사 M-09 — 끝난 이력 전체를 읽지 않는다
        UUID manager = UUID.randomUUID();
        Booking later = TestBookings.scheduled(CUSTOMER, manager, java.time.LocalDate.of(2026, 9, 25), java.time.LocalTime.of(10, 0));
        Booking sooner = TestBookings.scheduled(CUSTOMER, manager, java.time.LocalDate.of(2026, 9, 18), java.time.LocalTime.of(14, 0));
        when(bookings.findByCustomerIdAndStatusInOrderByStartsAt(CUSTOMER, BookingStatus.OPEN)).thenReturn(List.of(later, sooner));
        assertThat(service.nextVisit(CUSTOMER)).get().extracting(r -> r.date()).isEqualTo("2026-09-18");
        verify(bookings, never()).findByCustomerIdOrderByStartsAtDesc(any());
    }

    // 운영자가 변경·취소 기한을 48시간으로 바꿨다 — 내 예약 응답이 그 값을 싣는다.
    // 앱의 잠긴 카드 안내·정기 회차 취소 시트가 이 값으로 '방문 N시간 전' 을 말한다 (문제 목록 25번)
    @Test
    void 내_예약_응답은_설정된_변경_취소_기한_시간을_싣는다() {
        when(catalog.cancelDeadlineHours()).thenReturn(48);
        when(bookings.findByCustomerIdOrderByStartsAtDesc(CUSTOMER)).thenReturn(List.of());
        assertThat(service.reservations(CUSTOMER).changeDeadlineHours()).isEqualTo(48);
    }

    // 같은 시리즈 회차 둘(10/5·11/5)과 1회 예약 하나 — 순번은 방문일 순이라 목록 순서와 상관없이 11/5 가 2/2회, 1회 예약은 제목 그대로
    @Test
    void 정기청소_회차는_제목에_몇_번째_방문인지를_붙인다() { // 전체 점검 G2 — 같은 이름의 카드 7장이 늘어섰다
        UUID manager = UUID.randomUUID();
        UUID series = UUID.randomUUID();
        Booking first = TestBookings.scheduledSeries(CUSTOMER, manager, series, java.time.LocalDate.of(2026, 10, 5), java.time.LocalTime.of(10, 0));
        Booking second = TestBookings.scheduledSeries(CUSTOMER, manager, series, java.time.LocalDate.of(2026, 11, 5), java.time.LocalTime.of(10, 0));
        Booking once = TestBookings.scheduled(CUSTOMER, manager, java.time.LocalDate.of(2026, 9, 18), java.time.LocalTime.of(14, 0));
        when(bookings.findByCustomerIdOrderByStartsAtDesc(CUSTOMER)).thenReturn(List.of(second, first, once));
        when(bookings.findBySeriesIdOrderByServiceDate(series)).thenReturn(List.of(first, second));
        assertThat(service.reservations(CUSTOMER).reservations()).extracting(ReservationResponse::title)
                .containsExactly("가정/원룸 청소 · 정기 2/2회", "가정/원룸 청소 · 정기 1/2회", "가정/원룸 청소");
        verify(bookings, times(1)).findBySeriesIdOrderByServiceDate(series); // 시리즈당 한 번
    }

    @Test
    void 예약은_오늘부터_booking_horizon_days_안의_날짜만() { // 전체 재점검 B1 — 10년 뒤 예약도 만들어지고 오퍼가 나갔다
        // 오늘(9/15)부터 1년 뒤 — 예약 가능 기간(60일) 밖이라 SLOT_UNAVAILABLE 이고 저장하지 않는다
        CreateBookingRequest far = new CreateBookingRequest("home", "oneTime", "2027-09-15", "10:00", "서울특별시 강남구 테헤란로 123", "3층",
                List.of(), List.of(), List.of(), null, null, null, false);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.create(CUSTOMER, "k-far", far))
                .isInstanceOf(kr.ansimclean.api.common.error.ApiException.class).extracting("code").isEqualTo(kr.ansimclean.api.common.error.ErrorCode.SLOT_UNAVAILABLE);
        verify(bookings, never()).save(any());
    }
}
