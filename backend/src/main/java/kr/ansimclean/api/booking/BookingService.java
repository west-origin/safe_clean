package kr.ansimclean.api.booking;

import kr.ansimclean.api.booking.dto.BookingResponse;
import kr.ansimclean.api.booking.dto.CancelRequest;
import kr.ansimclean.api.booking.dto.CreateBookingRequest;
import kr.ansimclean.api.booking.dto.QuoteRequest;
import kr.ansimclean.api.booking.dto.QuoteResponse;
import kr.ansimclean.api.booking.dto.ReservationResponse;
import kr.ansimclean.api.booking.dto.ReservationsResponse;
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
import kr.ansimclean.api.common.format.KoreanFormat;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.common.web.Idempotent;
import kr.ansimclean.api.profile.AddressRegion;
import kr.ansimclean.api.profile.ProfileService;
import org.springframework.context.annotation.Lazy;
import kr.ansimclean.api.schedule.ManagerWorkLookup;
import kr.ansimclean.api.common.web.RateLimiter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 고객 예약: 견적·생성·조회·변경·취소 (BACKEND_PLAN.md §6.1 §6.4 §6.8). 상태는 Booking 의 메서드로만 바뀐다.
 * 매칭은 BookingDispatcher(B4 offer) 로, 외부 효과(푸시)는 아웃박스로 나간다. 정기청소 묶음 동작은 BookingSeriesService.
 * 고객 쪽 입구는 BookingController(견적·생성·상세·목록·일정 변경·취소)와 CustomerScreenService(홈의 '다가오는 방문').
 * 아래쪽 절반은 다른 기능 패키지(예: offer·job)가 예약을 읽고 잠그는 창구다 —
 * 다른 패키지는 BookingRepository 를 직접 쓰지 않고 이 서비스를 부른다.
 * 트랜잭션(CODE_GUIDE.md 3-3): 다른 기능의 트랜잭션 안에서 부르면 그 묶음에 합쳐지므로, 여기서 던진 ApiException 은
 * 기본으로 바깥 묶음까지 되돌린다. 예외: requireManagerBooking 은 noRollbackFor 라 404 를 던져도 바깥 묶음
 * (LiveService.pause/resume)을 되돌림으로 표시하지 않는다.
 */
@Service
@Transactional
public class BookingService implements ManagerWorkLookup {
    // app_settings 테이블의 키 — 문구·기간 값은 코드가 아니라 DB 행에 있다
    public static final String SETTING_PAYMENT_NOTE = "payment_note"; // 견적 응답의 결제 안내 문구
    public static final String SETTING_SERIES_TERM_MONTHS = CatalogService.SETTING_SERIES_TERM_MONTHS; // 정기청소 개월 수 = 회차 수 (V021 에서 6). 키는 앱 안내에도 내려 주는 CatalogService 에 있다
    public static final String SETTING_CLEANER_PENDING = "cleaner_pending_label"; // 매니저 칸: 아직 배정 전 문구
    public static final String SETTING_CLEANER_MATCHING_FAILED = "cleaner_matching_failed_label"; // 매칭 실패(후보 없음·회차 소진) 카드 문구 (V015)
    /** 자동 재매칭이 약속 전에 더 볼 수 없는 매칭 실패 카드 문구 — '다시 찾아요' 라고 하지 않는다 (V044, 자체 점검 후속 검토) */
    public static final String SETTING_CLEANER_MATCHING_FAILED_FINAL = "cleaner_matching_failed_final_label";
    public static final String SETTING_RESERVATIONS_HEADER = "reservations_header"; // 내 예약 목록 응답의 머리글
    public static final String SETTING_RESERVATION_TABS = "reservation_tabs"; // 내 예약 목록 응답의 탭 목록
    /** 취소 이유 (booking_status_history.reason / bookings.cancel_reason) */
    public static final String REASON_CUSTOMER = "customer";
    public static final String REASON_SERIES = "series_cancelled";
    /** 시스템이 정한 취소 — 고객 요청으로 되풀이되지 않는다(이미 이렇게 취소됐으면 고객 취소 재시도도 INVALID_STATE) */
    static final Set<String> SYSTEM_CANCEL_REASONS = Set.of(StaleBookingCleaner.REASON_UNASSIGNED, StaleBookingCleaner.REASON_NOT_STARTED);
    /** 고객이 보내면 안 되는 사유 값 — 'not_started' 를 넣으면 매니저·고객에게 미방문 푸시가 갔다 (자체 점검 #57) */
    static final Set<String> RESERVED_REASONS = Set.of(StaleBookingCleaner.REASON_UNASSIGNED, StaleBookingCleaner.REASON_NOT_STARTED, REASON_SERIES);

    private final BookingRepository bookings;
    private final BookingSeriesRepository seriesRepo;
    private final BookingStatusHistoryRepository history;
    private final CatalogService catalog;
    private final PricingService pricing;
    private final AppSettingsService settings;
    private final ProfileService profiles;
    private final BookingDispatcher dispatcher; // 매칭(콜) 쪽 입구 — 구현은 offer.OfferService
    private final RecordingLookup recordings; // 녹화본 준비 여부 — 구현은 live 패키지
    private final OutboxService outbox;
    private final Idempotent idempotent;
    private final AppClock clock;
    private final RateLimiter rateLimiter; // 사용자별 시간당 횟수 제한 — 메모리라 트랜잭션이 되돌려져도 센 횟수는 남는다
    private final RepairPhotoService photoUploads; // 첨부 확인(붙은 수·만료)은 사진 서비스 한 곳에서 (재감사 2차 #3)

    public BookingService(BookingRepository bookings, BookingSeriesRepository seriesRepo, BookingStatusHistoryRepository history,
                          CatalogService catalog, PricingService pricing, AppSettingsService settings, ProfileService profiles,
                          @Lazy BookingDispatcher dispatcher, RecordingLookup recordings, OutboxService outbox, Idempotent idempotent, AppClock clock,
                          RateLimiter rateLimiter, RepairPhotoService photoUploads) {
        this.rateLimiter = rateLimiter;
        this.photoUploads = photoUploads;
        // dispatcher 는 @Lazy: 구현체(offer/OfferService)가 이 서비스를 쓰므로 생성자 순환을 프록시로 끊는다
        this.bookings = bookings;
        this.seriesRepo = seriesRepo;
        this.history = history;
        this.catalog = catalog;
        this.pricing = pricing;
        this.settings = settings;
        this.profiles = profiles;
        this.dispatcher = dispatcher;
        this.recordings = recordings;
        this.outbox = outbox;
        this.idempotent = idempotent;
        this.clock = clock;
    }

    // ── 견적 ──

    // 부르는 곳: BookingController POST /bookings/quote. 결과 = 회당 금액 내역 + 방문 날짜 목록 + 총액 + 결제 안내·정기 할인 문구
    /** 확인 화면 "요금" 행. 저장하지 않는다. 생성 때 같은 계산을 다시 하므로 앱이 본 금액과 다를 수 없다 */
    @Transactional(readOnly = true)
    public QuoteResponse quote(QuoteRequest req) {
        // 생성(doCreate)과 같은 카테고리·날짜·시각·옵션 검사를 먼저 한다 (주소·안내 항목·'수리는 1회만' 검사는 생성에서만)
        catalog.requireCategory(req.category());
        LocalDate date = parseDate(req.date());
        LocalTime time = parseTime(req.time());
        requireBookableSchedule(date, time);
        List<CatalogOption> options = catalog.requireOptions(req.category(), distinct(req.freeOptions()), distinct(req.paidOptions()));
        PriceQuote q = pricing.quote(req.category(), req.frequency(), options);
        List<LocalDate> dates = "recurring".equals(req.frequency()) ? seriesDates(date) : List.of(date); // 정기청소면 회차 날짜 전부, 1회면 그날 하나
        long total = q.total() * dates.size(); // 총액 = 회당 금액 × 회차 수
        // 요금을 총액 한 줄로만 보여 줘서 옵션이 얼마인지·정기 할인이 얼마인지 알 수 없었다 (사용성 검토 2026-09-22)
        QuoteResponse.PerVisit perVisit = new QuoteResponse.PerVisit(q.base(), q.options(), q.total(), KoreanFormat.won(q.total()),
                KoreanFormat.won(q.base()), q.options() > 0 ? "+" + KoreanFormat.won(q.options()) : null);
        return new QuoteResponse(perVisit,
                dates.stream().map(d -> new QuoteResponse.Visit(d.toString(), KoreanFormat.dateLongWeekday(d))).toList(),
                dates.size(), total, KoreanFormat.won(total), settings.get(SETTING_PAYMENT_NOTE, String.class), savingNote(req.category(), req.frequency(), q.base()));
    }

    /** 정기청소가 1회 청소보다 싼 만큼 — 배너는 '정기 이용 할인' 을 말하는데 확인 화면은 아무 말도 안 했다 (사용성 검토 2026-09-22) */
    private String savingNote(String category, String frequency, long base) {
        if (!"recurring".equals(frequency)) return null;
        // 1회 기본요금이 있고 정기 기본요금보다 비쌀 때만 문구를 만들고, 아니면 null
        return pricing.baseAmount(category, "oneTime").filter(oneTime -> oneTime > base)
                .map(oneTime -> "1회 청소보다 회당 " + KoreanFormat.won(oneTime - base) + " 저렴해요").orElse(null);
    }

    // ── 생성 ──

    // 부르는 곳: BookingController POST /bookings (멱등 키: CODE_GUIDE.md 3-7)
    /** Idempotency-Key 가 있으면 같은 키·같은 본문의 재요청은 저장된 응답을 돌려준다 (§6.9) */
    public BookingResponse create(UUID customerId, String idempotencyKey, CreateBookingRequest req) {
        return idempotent.run(idempotencyKey, customerId, req, BookingResponse.class, () -> doCreate(customerId, req));
    }

    /**
     * 예약 생성 본체. 순서: 탈퇴와 줄 서기 → 주소·횟수 제한 → 입력 검증 → 가격 계산 → (정기면 시리즈 행) →
     * 회차별 예약 행 + 상태 이력 → 수리 사진 첨부 확정 → 첫 회차 매칭 시작(콜 푸시는 아웃박스) → 주소 기억.
     * 중간에 오류가 나면 DB 변경(예: 시리즈·예약·콜·멱등 키 기록)은 모두 되돌려지지만 메모리의 횟수 제한은 센 채로 남는다 —
     * 그래서 고쳐서 다시 보낼 수 있는 주소 검사를 횟수 제한보다 먼저 한다. 결과는 첫 회차 기준 응답 + 회차 목록
     */
    private BookingResponse doCreate(UUID customerId, CreateBookingRequest req) {
        // 탈퇴(lockLive, FOR UPDATE)와 같은 행을 잡는다(FOR SHARE) — 탈퇴 가드 검사 뒤에 예약이 끼어들어 탈퇴한 고객의 예약이 남지 않게 (감사 M-06)
        profiles.holdLive(customerId);
        AddressRegion.requireMatchable(req.roadAddress()); // 시·도가 없으면 후보 0명 — 받기 전에 막는다. 한도 전이라 고쳐 다시 보내도 횟수가 줄지 않는다 (직접 사용 점검 U1)
        rateLimiter.check("booking", customerId.toString(), RateLimiter.BOOKING_PER_USER_PER_HOUR); // 예약 한 건 = 후보 전원 오퍼·푸시 (감사 M-05). 같은 키 재시도는 첫 요청이 성공했으면 여기 오지 않는다(실패해 되돌려졌으면 다시 센다)
        ServiceCategory category = catalog.requireCategory(req.category());
        if ("repair".equals(req.category()) && !"oneTime".equals(req.frequency())) throw ApiException.validation("수리 예약은 1회만 가능해요"); // api.md: 수리는 oneTime 만 — 정기 회차·0원 시리즈가 생기지 않게 (검수 3차)
        LocalDate date = parseDate(req.date());
        LocalTime time = parseTime(req.time());
        requireBookableSchedule(date, time);
        List<String> freeOptions = distinct(req.freeOptions()); // 같은 key 두 번 → 한 번 (검증·가격·저장 전부 같은 목록)
        List<String> paidOptions = distinct(req.paidOptions());
        List<CatalogOption> options = catalog.requireOptions(req.category(), freeOptions, paidOptions);
        catalog.requireNoticeKeys(req.category(), req.notices());
        PriceQuote quote = pricing.quote(req.category(), req.frequency(), options); // 회당 금액·수수료·소요 시간 — 아래에서 모든 회차 행에 그대로 고정한다
        Instant now = clock.now();
        String road = req.roadAddress().trim();
        String detail = blankToNull(req.detailAddress());
        AddressRegion region = AddressRegion.parse(road); // 도로명주소에서 시·도, 시·군·구를 뽑는다 (예약 행에 따로 저장)
        String request = blankToNull(req.request());

        // 정기청소면 시리즈 행을 먼저 저장하고 그 회차 날짜 수만큼 예약 행을 만든다. 1회면 날짜 하나, seriesId 는 null
        UUID seriesId = null;
        List<LocalDate> dates = List.of(date);
        int termMonths = settings.integer(SETTING_SERIES_TERM_MONTHS);
        if ("recurring".equals(req.frequency())) {
            BookingSeries series = seriesRepo.save(BookingSeries.create(customerId, req.category(), date, time, termMonths, road, detail,
                    freeOptions, paidOptions, req.notices(), request, now));
            seriesId = series.getId();
            dates = series.visitDates();
        }
        List<Booking> visits = new ArrayList<>();
        for (LocalDate d : dates) {
            Booking b = Booking.create(customerId, seriesId, req.category(), req.frequency(), d, time, road, detail,
                    region.sido(), region.sigungu(), freeOptions, paidOptions, req.notices(), request, blankToNull(req.repairType()), quote, now);
            if ("repair".equals(req.category()) && req.repair() != null) {
                b.describeRepair(blankToNull(req.repair().appliance()), blankToNull(req.repair().symptom()), blankToNull(req.repair().applianceKey()), blankToNull(req.repair().symptomKey()),
                        blankToNull(req.repair().detail()), ownPhotos(customerId, req.repair().photos()));
            }
            b = bookings.save(b);
            record(b, null, BookingStatus.REQUESTED, customerId, null, now); // 상태 이력 첫 줄: (없음) → requested
            visits.add(b);
        }
        List<String> photos = visits.get(0).getRepairPhotos(); // 수리는 1회 예약만 되므로 첫 회차 것이 전부다 (수리가 아니면 빈 목록)
        photoUploads.attach(customerId, photos); // 예약에 붙은 사진은 24시간 정리에서 빠진다. 정리로 이미 지워졌으면 VALIDATION — 없는 파일을 가리키는 예약을 만들지 않는다 (감사 M-04·재감사 2차 #3)
        // 첫 회차만 즉시 매칭. 나머지 회차는 SeriesDispatcher 가 방문 7일 전에 (§6.8)
        startAssigning(visits.get(0), customerId, null);
        if (req.saveAddress()) profiles.rememberAddress(customerId, road, detail);
        // 결제는 별도 CheckoutService에서 고객 동작으로 시작한다. 예약 생성은 청구하지 않고 unpaid로 둔다.
        return BookingMapper.toBooking(visits.get(0), category.getLabel(), unassignedLabel(visits.get(0)), visits); // 후보가 없어 바로 매칭 실패했으면 실패 안내 (목록과 같게)
    }

    // 부르는 곳 예: doCreate(첫 회차), BookingSeriesService.dispatchDue(매일 09:00 정기 회차).
    // 밤(서울 21시~08시)이면 dispatcher 가 콜을 보내지 않고 아침 08:05 로 미룬다 — 미뤄도 약속까지 3시간 넘게 남는 예약만 (MatchingRetryWindow.holdUntilMorning)
    // 부작용: 상태 이력 한 줄 + BookingDispatcher 가 후보에게 오퍼를 만들고 콜 알림을 아웃박스에 적는다 (같은 트랜잭션).
    // 후보가 한 명도 없으면 dispatcher 쪽에서 매칭 실패로 표시하고 고객에게 알린다. dispatch 의 Set.of() = 콜에서 뺄 매니저 없음
    /** requested → assigning + 매칭 시작. preferredManagerId 가 있으면 그 매니저에게 먼저(정기 회차) */
    public void startAssigning(Booking booking, UUID actorId, UUID preferredManagerId) {
        BookingStatus from = booking.getStatus();
        booking.beginAssigning();
        record(booking, from, booking.getStatus(), actorId, null, clock.now());
        if (preferredManagerId != null) dispatcher.dispatchPreferred(booking, preferredManagerId);
        else dispatcher.dispatch(booking, Set.of());
    }

    // ── 조회 ──

    /** 예약 상세 (BookingController GET /bookings/{id}). 정기청소면 같은 시리즈의 회차 전부를 날짜순으로 같이 싣는다. 남의 예약은 NOT_FOUND */
    @Transactional(readOnly = true)
    public BookingResponse get(UUID customerId, UUID id) {
        Booking b = require(customerId, id);
        List<Booking> visits = b.getSeriesId() == null ? List.of(b) : bookings.findBySeriesIdOrderByServiceDate(b.getSeriesId());
        // 매니저 칸은 목록 카드와 같은 규칙(BookingMapper.managerLabel): "이름 매니저" / 매니저 없이 취소됐으면 '배정 전 취소' /
        // 그 밖에는 배정 전·매칭 실패 문구(unassignedLabel). 이 응답을 쓰는 곳 예: 고객 앱 예약 완료 화면(다시 열거나 새로고침할 때)
        Map<UUID, String> names = b.getManagerId() == null ? Map.of() : profiles.namesOf(List.of(b.getManagerId()));
        String cleaner = BookingMapper.managerLabel(b, names, unassignedLabel(b));
        return BookingMapper.toBooking(b, catalog.requireCategory(b.getCategoryKey()).getLabel(), cleaner, visits);
    }

    /**
     * 내 예약 화면 (BookingController GET /reservations): 머리글·탭(app_settings) + 이 고객의 모든 예약 카드 (약속 시각이 늦은 것부터)
     * + 변경·취소 기한 시간(카드의 changeable 을 정한 것과 같은 cancel_deadline_hours — 앱의 잠김 안내 문구가 쓴다, 문제 목록 25번)
     */
    @Transactional(readOnly = true)
    public ReservationsResponse reservations(UUID customerId) {
        List<Booking> list = bookings.findByCustomerIdOrderByStartsAtDesc(customerId);
        return new ReservationsResponse(settings.node(SETTING_RESERVATIONS_HEADER), settings.node(SETTING_RESERVATION_TABS), rows(list), catalog.cancelDeadlineHours());
    }

    /**
     * 예약 목록 → 카드 목록. 매니저 이름·소개, 설정 문구, 녹화본 준비 여부를 먼저 한 번씩 모아 읽고
     * 카드마다 그 값을 넘긴다. 목록 화면과 홈(nextVisit)이 같이 쓴다
     */
    private List<ReservationResponse> rows(List<Booking> list) {
        Set<UUID> managerIds = new HashSet<>();
        for (Booking b : list) if (b.getManagerId() != null) managerIds.add(b.getManagerId());
        Map<UUID, String> names = profiles.namesOf(managerIds);
        Map<UUID, String> intros = profiles.managerIntros(managerIds); // 고객이 누가 오는지 알 수 있게 (사용성 검토 2026-09-22)
        String pending = settings.get(SETTING_CLEANER_PENDING, String.class);
        String failedLabel = settings.get(SETTING_CLEANER_MATCHING_FAILED, String.class);
        String failedFinalLabel = settings.get(SETTING_CLEANER_MATCHING_FAILED_FINAL, String.class);
        LocalDate today = clock.today();
        Instant now = clock.now();
        int deadline = catalog.cancelDeadlineHours();
        // 완료된 예약 id 만 골라 녹화본이 준비된 것을 한 번에 묻는다 (구현은 live 패키지)
        Set<UUID> ready = recordings.readyBookingIds(list.stream().filter(b -> b.getStatus() == BookingStatus.DONE).map(Booking::getId).toList());
        Map<UUID, List<Booking>> seriesVisits = new HashMap<>(); // title() 이 채우는 시리즈별 회차 캐시
        return list.stream()
                .map(b -> BookingMapper.toReservation(b, title(b, seriesVisits), names, intros, cleanerLabel(b, now, pending, failedLabel, failedFinalLabel), today, ready.contains(b.getId()), now, deadline)).toList();
    }

    /** 매니저가 없는 예약의 매니저 칸 — 예약 상세·생성 응답도 목록과 같은 기준 (예전에는 매칭 실패여도 '배정 후 알려드려요', 자체 점검 후속 검토) */
    private String unassignedLabel(Booking b) {
        return cleanerLabel(b, clock.now(), settings.get(SETTING_CLEANER_PENDING, String.class), settings.get(SETTING_CLEANER_MATCHING_FAILED, String.class),
                settings.get(SETTING_CLEANER_MATCHING_FAILED_FINAL, String.class));
    }

    /** 매니저 칸 문구: 배정 전 / 매칭 실패(자동 재매칭이 아직 볼 수 있으면 '다시 찾아요', 못 보면 문의 안내) */
    static String cleanerLabel(Booking b, Instant now, String pending, String failedLabel, String failedFinalLabel) {
        if (b.getMatchingFailedAt() == null) return pending;
        return MatchingRetryWindow.retryPossible(now, b.getStartsAt()) || failedFinalLabel == null ? failedLabel : failedFinalLabel;
    }

    /**
     * 카드 제목. 정기청소 회차면 "가정/원룸 청소 · 정기 2/6회" — 같은 이름의 카드가 7장 늘어서
     * 어느 것이 정기청소의 몇 번째 방문인지 알 수 없었다 (전체 점검 G2). 순번은 방문일 순, 취소된 회차도 센다(6회 약속 기준)
     * @param seriesVisits 시리즈별 회차 캐시 — 한 응답 안에서 시리즈당 한 번만 읽는다
     */
    private String title(Booking b, Map<UUID, List<Booking>> seriesVisits) {
        String label = catalog.requireCategory(b.getCategoryKey()).getLabel();
        if (b.getSeriesId() == null) return label;
        List<Booking> visits = seriesVisits.computeIfAbsent(b.getSeriesId(), bookings::findBySeriesIdOrderByServiceDate);
        for (int i = 0; i < visits.size(); i++) {
            if (visits.get(i).getId().equals(b.getId())) return label + " · 정기 " + (i + 1) + "/" + visits.size() + "회";
        }
        return label;
    }

    /**
     * 홈에 띄울 '다가오는 방문' 한 건 — 가장 이른 예정/진행 중 예약. 없으면 empty.
     * 홈이 예약을 하나도 보여 주지 않아, 내일 방문이 있어도 배너만 보였다 (사용성 검토 2026-09-22).
     * 부르는 곳: customer.CustomerScreenService (홈 응답의 다가오는 방문 칸, 없으면 null 로 싣는다).
     */
    @Transactional(readOnly = true)
    public Optional<ReservationResponse> nextVisit(UUID customerId) {
        // 끝난 이력 전체를 읽지 않고 열린 예약만 (감사 M-09 — 홈은 앱을 열 때마다 부른다)
        // 카드로 바꾼 뒤 '예정' 탭 카드 중 방문 날짜, 같으면 시각이 가장 이른 한 장 (min = 가장 작은 것)
        return rows(bookings.findByCustomerIdAndStatusInOrderByStartsAt(customerId, BookingStatus.OPEN)).stream()
                .filter(r -> r.tab().equals("upcoming"))
                .min(Comparator.comparing((ReservationResponse r) -> r.date()).thenComparing(ReservationResponse::time));
    }

    /** 고객 예약 카드 한 장 (남의 것은 NOT_FOUND). 예: BookingSeriesService.cancelWhole 이 응답으로 쓴다 */
    @Transactional(readOnly = true)
    public ReservationResponse reservation(UUID customerId, UUID id) {
        return toReservation(require(customerId, id));
    }

    // ── 변경·취소 ──

    /**
     * 일정 변경 (BookingController PATCH /reservations/{id}/schedule). 검증 → Booking.reschedule(기한·상태 검사) → 이력 → 다시 매칭.
     * 배정돼 있던 매니저는 풀리고, 그 매니저에게 '일정 변경' 푸시가 간다(우선 콜은 dispatcher 가 보낼 수 있을 때만. 밤이라 매칭을 아침으로 미뤘으면 푸시도 아침에 — 그 전에 취소되면 cancelOne 이 '예약이 취소됐어요' 를 보낸다). 결과 = 바뀐 카드 한 장
     * requested(아직 매칭 전인 정기 회차)는 새 날짜가 매칭 시점 안으로 들어왔을 때만 바로 매칭을 시작한다.
     */
    public ReservationResponse reschedule(UUID customerId, UUID id, ScheduleChangeRequest req) {
        Booking b = lockCustomerBooking(customerId, id); // 매니저 수락·재매칭과 경합하면 마지막 커밋이 상대 결과를 덮는다 → 잠그고 읽는다
        LocalDate date = parseDate(req.date());
        LocalTime time = parseTime(req.time());
        LocalDate current = b.getServiceDate();
        requireBookableSchedule(date, time, current.isAfter(clock.today()) ? current : clock.today()); // 예약 가능 기간은 지금 방문일(이미 지났으면 오늘)부터 센다
        if (date.equals(b.getServiceDate()) && time.equals(b.getServiceTime())) return toReservation(b); // 같은 일정이면 매니저를 풀지 않는다
        if (b.getSeriesId() != null) {
            BookingSeries s = seriesRepo.findById(b.getSeriesId()).orElseThrow();
            if (date.isAfter(s.getTermEnd())) throw new ApiException(ErrorCode.SLOT_UNAVAILABLE, "정기청소 기간(" + KoreanFormat.dateShort(s.getTermEnd()) + "까지) 안에서만 바꿀 수 있어요"); // 기간 밖으로 미루면 시리즈가 먼저 끝나 영영 매칭되지 않는다
        }
        rateLimiter.check("reschedule", customerId.toString(), RateLimiter.RESCHEDULE_PER_USER_PER_HOUR); // 바꿀 때마다 후보 전원에게 새 콜 푸시 — 번갈아 바꿔 매니저들을 괴롭히지 못하게 (자체 점검 #20). 같은 일정·날짜 검증 실패는 세지 않는다(기한·상태 검사는 이 뒤 Booking.reschedule 에서라 센다)
        BookingStatus from = b.getStatus();
        Instant now = clock.now();
        UUID previousManager = b.reschedule(date, time, now, catalog.cancelDeadlineHours()); // scheduled 였으면 매니저를 비우고 assigning 으로 되돌린다. 반환 = 원래 매니저(없으면 null)
        record(b, from, b.getStatus(), customerId, "rescheduled", now); // assigning→assigning(회차 +1) 도 남긴다
        if (from == BookingStatus.REQUESTED) {
            // 정기 회차: 매칭 시점(방문 dispatch_lead_days 일 전) 안으로 당겨졌으면 바로 매칭을 시작한다 — 09:00 스케줄러만 기다리면 다음 날 아침까지 늦어진다
            // (내일 방문이면 방문 당일 09:00 에야 콜이 나간다). 밤이면 OfferService 가 아침 08:05 로 미룬다(급한 예약은 바로)
            if (!date.isAfter(clock.today().plusDays(settings.integer(BookingSeriesService.SETTING_DISPATCH_LEAD_DAYS)))) {
                // 직전 매니저가 이 시리즈를 취소·미방문했으면 OfferService.dispatchPreferred 가 우선 콜을 빼고 일반 매칭으로 보낸다 (스케줄러 경로와 같은 결과)
                List<UUID> previous = bookings.findPreviousManagerIds(b.getSeriesId(), date); // 스케줄러 매칭과 같이 직전 매니저 우선 (§6.8)
                startAssigning(b, customerId, previous.isEmpty() ? null : previous.get(0));
            }
        } else {
            // 매칭 중이거나 배정돼 있던 예약: 옛 콜을 정리하고 원래 매니저가 있으면 그에게 먼저 제안한다.
            // 원래 매니저가 없거나 조건이 안 맞으면 후보 전원에게 보낸다 (BookingDispatcher.onRescheduled)
            boolean offered = dispatcher.onRescheduled(b, previousManager);
            // 원래 매니저에게 푸시 — 우선 콜을 보냈는지(offered)에 따라 문구가 갈린다.
            // 밤이라 새 콜을 아침으로 미뤘으면(isDispatchHeld) 지금 보내지 않는다 — 콜이 없는데 '새 콜을 확인해주세요' 가 가지 않게, 아침에 우선 콜을 보낼 때 OfferService 가 보낸다 (문제 목록 2번)
            if (previousManager != null && !b.isDispatchHeld()) outbox.publish(new BookingRescheduled(b.getId(), customerId, previousManager, offered));
        }
        return toReservation(b);
    }

    /** 회차 하나(또는 1회 예약) 취소. 정기청소 전체는 BookingSeriesService.cancelWhole */
    public ReservationResponse cancel(UUID customerId, UUID id, CancelRequest req) {
        UUID seriesId = seriesIdOf(customerId, id); // 엔티티를 올리지 않고 — 잠금 뒤의 실제 상태(그 사이 수락됐으면 매니저 있음)를 보게
        if (seriesId != null) seriesRepo.lockById(seriesId); // 시리즈 → 예약 순서 (cancelWhole·endSeriesIfNothingLeft 와 같다)
        Booking b = lockCustomerBooking(customerId, id);
        // 이미 고객이 취소했으면 같은 결과를 돌려준다 — 응답이 늦어 다시 누르면 '지금은 할 수 없는 동작이에요' 가 떴다 (자체 점검 #58). 시스템 취소는 그대로 INVALID_STATE
        if (b.getStatus() == BookingStatus.CANCELLED && !SYSTEM_CANCEL_REASONS.contains(b.getCancelReason())) return toReservation(b);
        // 사유가 비었으면 'customer'. 시스템이 쓰는 사유 값(unassigned·not_started·series_cancelled)을 보내면 'customer' 로 바꿔 저장한다
        String reason = blankToNull(req.reason()) == null ? REASON_CUSTOMER : req.reason().trim();
        cancelOne(b, customerId, RESERVED_REASONS.contains(reason) ? REASON_CUSTOMER : reason);
        return toReservation(b);
    }

    /** 고객 취소의 공통 부분 (기한 검사는 Booking.cancel 안). 시리즈 전체 취소도 회차마다 이걸 부른다 */
    void cancelOne(Booking b, UUID customerId, String reason) {
        BookingStatus from = b.getStatus();
        Instant now = clock.now();
        // 밤에 일정을 바꿔 '예약 일정이 변경됐어요' 를 아침으로 미뤄 둔 원래 매니저 — 일정 변경이 매니저 칸을 비웠고, 아래 onCancelled 가 보류를 지우면 아침 안내도 사라진다.
        // 이 매니저에게는 취소 안내를 지금 보낸다(취소 안내는 새 콜이 아니라 밤에도 간다 — 밤에 배정된 예약을 취소할 때와 같다) (합친 38건 검토 R1·R14).
        // 정기청소 다음 회차의 우선 매니저 보류(rescheduled = false)는 이 예약을 맡은 적이 없어 알리지 않는다
        UUID heldPrevious = b.isDispatchHeldRescheduled() ? b.getDispatchHeldManagerId() : null;
        b.cancel(reason, now, catalog.cancelDeadlineHours());
        record(b, from, BookingStatus.CANCELLED, customerId, reason, now);
        dispatcher.onCancelled(b);
        // 이벤트에는 사유 코드만 싣는다 — 자유 입력(번호·주소가 들어갈 수 있다)이 지우지 않는 아웃박스 원장에 남아 5년 파기를 비켜 갔다.
        // 푸시는 not_started 인지만 본다(고객 취소 경로로는 오지 않는다) (자체 점검 후속 검토)
        UUID notify = b.getManagerId() != null ? b.getManagerId() : heldPrevious; // 배정된 매니저, 없으면 밤에 미뤄 둔 일정 변경 전 매니저
        if (notify != null) outbox.publish(new BookingCancelled(b.getId(), customerId, notify, REASON_SERIES.equals(reason) ? REASON_SERIES : REASON_CUSTOMER));
        if (b.getSeriesId() != null) endSeriesIfNothingLeft(b.getSeriesId(), now);
    }

    /** 사진 경로는 이 고객이 올린 것(<customerId>/…)만 — 남의 사진을 가리키지 못하게 */
    static List<String> ownPhotos(UUID customerId, List<String> photos) {
        for (String p : photos) {
            if (p == null || !p.startsWith(customerId + "/") || p.contains("..")) throw ApiException.validation("첨부한 사진을 확인할 수 없어요. 다시 첨부해주세요");
        }
        return distinct(photos);
    }

    /** 중복을 뺀 목록 (처음 나온 순서 유지 — LinkedHashSet 은 넣은 순서를 기억하는 집합). null 이면 빈 목록 */
    static List<String> distinct(List<String> keys) {
        return keys == null ? List.of() : new ArrayList<>(new java.util.LinkedHashSet<>(keys));
    }

    /**
     * 남은 회차가 없는 정기청소는 시리즈도 닫는다 — active 로 남으면 term_end 까지 탈퇴가 막힌다.
     * 회차 취소뿐 아니라 매니저의 마지막 회차 완료 뒤에도 부른다(한 회차를 미리 취소하고 나머지를 다 마친 경우, 자체 점검 #8).
     * 마친 회차가 있으면 '끝남', 전부 취소면 '취소'
     * 방문일이 지난 예약을 시스템이 취소할 때(StaleBookingCleaner)도 부른다.
     */
    public void endSeriesIfNothingLeft(UUID seriesId, Instant now) {
        BookingSeries s = seriesRepo.lockById(seriesId).orElse(null); // 잠근 뒤에 회차를 읽어야 동시 취소가 서로를 본다
        if (s == null || !s.isActive()) return;
        List<Booking> visits = bookings.findBySeriesIdOrderByServiceDate(seriesId);
        boolean remaining = visits.stream().anyMatch(v -> v.getStatus() != BookingStatus.CANCELLED && v.getStatus() != BookingStatus.DONE);
        if (remaining) return;
        if (visits.stream().anyMatch(v -> v.getStatus() == BookingStatus.DONE)) s.end();
        else s.cancel(now);
    }

    /** 완료처럼 시리즈 마감이 따라오는 매니저 작업: 시리즈 → 예약 순서로 잠근다 (고객 회차 취소와 같은 순서 — 교착 방지, 자체 점검 #8) */
    public Booking lockManagerBookingWithSeries(UUID managerId, UUID id) {
        Booking b = lockWithSeries(id);
        if (!managerId.equals(b.getManagerId())) throw ApiException.notFound();
        return b;
    }

    /** 운영자 완료용 (매니저 소유권 검사 없음) — 순서는 같다: 시리즈 → 예약 */
    public Booking lockWithSeries(UUID id) {
        bookings.findSeriesIdById(id).ifPresent(seriesRepo::lockById); // 정기 회차면 시리즈 행을 먼저 잠근다 (1회 예약이면 건너뛴다)
        return lockForUpdate(id);
    }

    // BookingDeletionGuard 가 '취소한 뒤' 대신 '청소가 끝난 뒤' 탈퇴 문구를 고를 때 쓴다 (진행 중 청소는 고객이 취소할 수 없다)
    /**
     * 고객에게 진행 중(in_progress/paused) 작업이 있는가 — 방문일은 보지 않는다. 방문일이 지났는데
     * '청소 완료' 를 누르지 않은 작업도 여기 걸린다 (StaleBookingCleaner 가 운영자에게 알린다, 자체 점검 #17)
     */
    @Transactional(readOnly = true)
    public boolean hasActiveJobsAsCustomer(UUID customerId) {
        return bookings.existsByCustomerIdAndStatusIn(customerId, BookingStatus.ACTIVE);
    }

    // ── 매니저 쪽(offer·job·settlement·managerhome)이 쓰는 읽기·잠금 ──

    /** id 로 예약 하나 (없으면 빈 Optional). 소유권 검사·잠금 없음 — 예: offer·live 패키지가 읽기용으로 쓴다 */
    @Transactional(readOnly = true)
    public Optional<Booking> find(UUID id) {
        return bookings.findById(id);
    }

    /** 탈퇴한 고객의 예약 전부 — 녹화본 정리용 (live.AccountMediaDeletionHandler. 수리 사진은 RepairPhotoService.deleteAllOf 가 원장·폴더로 따로 찾는다) */
    @Transactional(readOnly = true)
    public List<Booking> customerBookings(UUID customerId) {
        return bookings.findByCustomerIdOrderByStartsAtDesc(customerId);
    }

    /** 여러 id 의 예약을 한 번에 (id 가 없으면 DB 를 부르지 않고 빈 목록). 예: offer·settlement 패키지가 쓴다 */
    @Transactional(readOnly = true)
    public List<Booking> findAll(Collection<UUID> ids) {
        return ids.isEmpty() ? List.of() : bookings.findAllById(ids);
    }

    /** 고객 자신의 예약을 잠그고 돌려준다 (취소·변경용). 남의 것은 NOT_FOUND */
    Booking lockCustomerBooking(UUID customerId, UUID id) {
        Booking b = lockForUpdate(id);
        if (!customerId.equals(b.getCustomerId())) throw ApiException.notFound();
        return b;
    }

    /** 고객 자신의 예약만 (남의 것은 NOT_FOUND). live 패키지가 쓴다 */
    @Transactional(readOnly = true)
    public Booking requireCustomerBooking(UUID customerId, UUID id) {
        return require(customerId, id);
    }

    /** 자동 재매칭 대상 id (offer/MatchingRetry) */
    @Transactional(readOnly = true)
    public List<UUID> matchingFailedIds(Instant startsAfter) {
        return bookings.findMatchingFailedIds(startsAfter);
    }

    /** 밤이라 새 콜을 아침으로 미뤄 둔 예약 id, id 순 (offer/MatchingRetry 가 아침에 보낸다, 문제 목록 2번) */
    @Transactional(readOnly = true)
    public List<UUID> dispatchHeldIds() {
        return bookings.findDispatchHeldIds();
    }

    // 없는 id 면 NOT_FOUND. 잠금 순서(CODE_GUIDE.md 3-4): 시리즈도 잠그면 시리즈를 먼저(lockWithSeries·cancel),
    // 여러 예약을 잠그면 id 오름차순(예: cancelWhole·OfferExpirer). 예약만 잠그는 곳(예: 콜 거절)은 바로 부른다
    /** select … for update (§6.2-3). 매니저 행도 잠그는 콜 수락(OfferService.doAccept)은 매니저 행을 먼저 잠근 뒤 부른다 */
    public Booking lockForUpdate(UUID id) {
        return bookings.findForUpdate(id).orElseThrow(ApiException::notFound);
    }

    /** 매니저 자신의 작업을 잠그고 돌려준다 (상태를 바꾸는 작업 API 용 — 같은 작업의 동시 요청이 줄을 선다). 남의 것은 NOT_FOUND */
    public Booking lockManagerBooking(UUID managerId, UUID id) {
        Booking b = lockForUpdate(id);
        if (!managerId.equals(b.getManagerId())) throw ApiException.notFound();
        return b;
    }

    /** 매니저 자신의 작업만 (남의 것은 NOT_FOUND, §11-8).
     *  noRollbackFor: LiveService.pause/resume(noRollbackFor) 안에서 참여 트랜잭션으로 불린다 — 여기서 던진 404 가 rollback-only 를 남기면 바깥 커밋이 UnexpectedRollbackException(500) 이 된다 (검수 3차) */
    @Transactional(readOnly = true, noRollbackFor = ApiException.class)
    public Booking requireManagerBooking(UUID managerId, UUID id) {
        return bookings.findByIdAndManagerId(id, managerId).orElseThrow(ApiException::notFound);
    }

    // ManagerWorkLookup(schedule 패키지의 인터페이스) 구현 — schedule 은 booking 을 직접 모르고 이 인터페이스로만 부른다.
    /** [from, to) 와 겹치는 맡은 작업이 있는가 — schedule.AvailabilityService 가 근무 불가 시간을 등록하기 전에 확인한다 */
    @Override
    @Transactional(readOnly = true)
    public boolean hasWorkBetween(UUID managerId, Instant from, Instant to) {
        return hasOverlappingWork(managerId, from, to);
    }

    /** from~to 날짜의 배정·진행 중 작업 시간대 — schedule.AvailabilityService 가 근무시간을 바꾸기 전에 새 시간 밖 작업이 있는지 본다 */
    @Override
    @Transactional(readOnly = true)
    public List<ManagerWorkLookup.WorkSlot> upcomingWork(UUID managerId, LocalDate from, LocalDate to) {
        return managerBookings(managerId, from, to).stream()
                .filter(b -> b.getStatus() == BookingStatus.SCHEDULED || BookingStatus.ACTIVE.contains(b.getStatus()))
                .map(b -> new ManagerWorkLookup.WorkSlot(b.getServiceDate(), b.getServiceTime(), b.getEndsAt())).toList();
    }

    /** 그 매니저에게 [startsAt, endsAt) 과 겹치는 scheduled/in_progress/paused 작업이 있는가 */
    @Transactional(readOnly = true)
    public boolean hasOverlappingWork(UUID managerId, Instant startsAt, Instant endsAt) {
        return bookings.existsByManagerIdAndStatusInAndStartsAtLessThanAndEndsAtGreaterThan(managerId, List.of(BookingStatus.SCHEDULED, BookingStatus.IN_PROGRESS, BookingStatus.PAUSED), endsAt, startsAt);
    }

    /** 그 매니저의 방문일 from~to 예약 (상태 무관, 약속 시각 순) — 예: managerhome.ManagerHomeService, 위 upcomingWork */
    @Transactional(readOnly = true)
    public List<Booking> managerBookings(UUID managerId, LocalDate from, LocalDate to) {
        return bookings.findByManagerIdAndServiceDateBetweenOrderByStartsAt(managerId, from, to);
    }

    /** 진행 중(in_progress/paused) 작업 — 매니저당 최대 1건 (부분 유니크 인덱스) */
    @Transactional(readOnly = true)
    public Optional<Booking> activeJob(UUID managerId) {
        return bookings.findFirstByManagerIdAndStatusIn(managerId, BookingStatus.ACTIVE);
    }

    /** 그 매니저가 가장 최근에 끝낸(done) 작업 — job.JobService 가 쓴다 */
    @Transactional(readOnly = true)
    public Optional<Booking> lastCompleted(UUID managerId) {
        return bookings.findFirstByManagerIdAndStatusOrderByEndedAtDesc(managerId, BookingStatus.DONE);
    }

    /** 다른 기능 패키지가 엔티티 메서드로 상태를 바꾼 뒤 이력을 남길 때 (to = 현재 상태) */
    public void recordTransition(Booking b, BookingStatus from, UUID actorId, String reason) {
        record(b, from, b.getStatus(), actorId, reason, clock.now());
    }

    // ── 탈퇴 가드가 쓰는 조회 ──

    // 이 절의 조회는 BookingDeletionGuard.blockReason 이 부른다 (위 hasActiveJobsAsCustomer 도). true 면 탈퇴를 막는다
    /** 고객에게 끝나지 않은 예약(requested·assigning·scheduled·in_progress·paused)이 있는가 */
    @Transactional(readOnly = true)
    public boolean hasOpenBookings(UUID customerId) {
        return bookings.existsByCustomerIdAndStatusIn(customerId, BookingStatus.OPEN);
    }

    /** 고객에게 active 정기청소 시리즈가 있는가 */
    @Transactional(readOnly = true)
    public boolean hasActiveSeries(UUID customerId) {
        return seriesRepo.existsByCustomerIdAndStatus(customerId, BookingSeries.ACTIVE);
    }

    /** 매니저에게 맡은 채 끝나지 않은 예약이 있는가 (manager_id 가 그 매니저인 OPEN 상태 예약) */
    @Transactional(readOnly = true)
    public boolean hasOpenJobs(UUID managerId) {
        return bookings.existsByManagerIdAndStatusIn(managerId, BookingStatus.OPEN);
    }

    // ── 규칙 ──

    /** §6.1 생성 조건: 내일 이후 · booking_horizon_days 안 · 평일 · time_slots 안. 아니면 SLOT_UNAVAILABLE */
    void requireBookableSchedule(LocalDate date, LocalTime time) {
        requireBookableSchedule(date, time, clock.today());
    }

    /**
     * @param horizonFrom 예약 가능 기간(booking_horizon_days)을 세기 시작하는 날. 새 예약은 오늘,
     *   일정 변경은 오늘과 지금 방문일 중 늦은 날 — 오늘 기준으로만 세면 60일 뒤의 정기청소 회차(2~6회)는
     *   '예약 변경' 버튼이 있어도 고를 날짜가 하나도 없고 서버도 거부했다 (전체 점검 G3)
     */
    void requireBookableSchedule(LocalDate date, LocalTime time, LocalDate horizonFrom) {
        if (!date.isAfter(clock.today())) throw new ApiException(ErrorCode.SLOT_UNAVAILABLE, "내일부터 예약할 수 있어요");
        int horizon = catalog.bookableDays();
        if (date.isAfter(horizonFrom.plusDays(horizon))) {
            throw new ApiException(ErrorCode.SLOT_UNAVAILABLE, horizonFrom.equals(clock.today())
                    ? "오늘부터 " + horizon + "일 안의 날짜만 예약할 수 있어요" // 전체 재점검 B1
                    : "지금 방문일(" + KoreanFormat.dateShort(horizonFrom) + ")부터 " + horizon + "일 안의 날짜로만 바꿀 수 있어요");
        }
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) throw new ApiException(ErrorCode.SLOT_UNAVAILABLE, "주말은 예약할 수 없어요");
        if (!catalog.isTimeSlot(KoreanFormat.hhmm(time))) throw new ApiException(ErrorCode.SLOT_UNAVAILABLE, "선택할 수 없는 시간이에요");
    }

    /** 정기청소 회차 날짜 (§6.8: 첫 방문일부터 매월 같은 날짜 1회씩 series_term_months 회. 주말이면 다음 평일) */
    List<LocalDate> seriesDates(LocalDate first) {
        return BookingSeries.monthlyDates(first, settings.integer(SETTING_SERIES_TERM_MONTHS)); // 매월 1회 × termMonths (사용자 결정 2026-09-22)
    }

    /** 소유권 확인 + 시리즈 id (없으면 null). 엔티티를 영속성 컨텍스트에 올리지 않으므로 뒤이은 lockForUpdate 가 최신 상태를 읽는다 */
    UUID seriesIdOf(UUID customerId, UUID id) {
        return bookings.findSeriesRef(id, customerId).orElseThrow(ApiException::notFound).getSeriesId();
    }

    /** 고객 자신의 예약을 잠그지 않고 읽는다. 없거나 남의 것이면 NOT_FOUND */
    Booking require(UUID customerId, UUID id) {
        return bookings.findByIdAndCustomerId(id, customerId).orElseThrow(ApiException::notFound); // 남의 예약은 존재를 숨긴다 (§11-8)
    }

    /** 상태 이력(booking_status_history) 한 줄 저장. from = null 이면 처음 만든 것, actorId = 바꾼 사람(스케줄러·운영자 처리는 null) */
    void record(Booking b, BookingStatus from, BookingStatus to, UUID actorId, String reason, Instant at) {
        history.save(BookingStatusHistory.of(b.getId(), from, to, actorId, reason, at));
    }

    /** 예약 하나 → 카드 한 장. 목록(rows)과 같은 매니저 칸·녹화본·취소 가능 규칙을 한 건에 대해 적용한다 */
    private ReservationResponse toReservation(Booking b) {
        Map<UUID, String> names = b.getManagerId() == null ? Map.of() : profiles.namesOf(List.of(b.getManagerId()));
        Map<UUID, String> intros = b.getManagerId() == null ? Map.of() : profiles.managerIntros(List.of(b.getManagerId()));
        boolean ready = b.getStatus() == BookingStatus.DONE && !recordings.readyBookingIds(List.of(b.getId())).isEmpty();
        return BookingMapper.toReservation(b, title(b, new HashMap<>()), names, intros, cleanerLabel(b, clock.now(), settings.get(SETTING_CLEANER_PENDING, String.class), settings.get(SETTING_CLEANER_MATCHING_FAILED, String.class), settings.get(SETTING_CLEANER_MATCHING_FAILED_FINAL, String.class)), clock.today(), ready, clock.now(), catalog.cancelDeadlineHours());
    }

    /** 앱이 보낸 "YYYY-MM-DD" → 날짜. 형식이 틀리면 VALIDATION 오류(400)로 바꿔 던진다 */
    static LocalDate parseDate(String s) {
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            throw ApiException.validation("date 는 YYYY-MM-DD 형식이에요");
        }
    }

    /** 앱이 보낸 "HH:mm" → 시각. 형식이 틀리면 VALIDATION 오류로 바꿔 던진다 */
    static LocalTime parseTime(String s) {
        try {
            return LocalTime.parse(s);
        } catch (DateTimeParseException e) {
            throw ApiException.validation("time 은 HH:mm 형식이에요");
        }
    }

    /** null·빈칸·공백뿐이면 null, 아니면 앞뒤 공백을 뺀 글자 */
    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
