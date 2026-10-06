package kr.ansimclean.api.managerhome;

import kr.ansimclean.api.booking.Booking;
import kr.ansimclean.api.booking.BookingService;
import kr.ansimclean.api.booking.BookingStatus;
import kr.ansimclean.api.catalog.CatalogService;
import kr.ansimclean.api.common.format.KoreanFormat;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.job.JobService;
import kr.ansimclean.api.managerhome.dto.HomeResponse;
import kr.ansimclean.api.managerhome.dto.ScheduleItem;
import kr.ansimclean.api.managerhome.dto.ScheduleResponse;
import kr.ansimclean.api.offer.Offer;
import kr.ansimclean.api.offer.OfferService;
import kr.ansimclean.api.profile.ProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** 매니저 홈·일정 (BACKEND_PLAN.md §6.6) — booking·offer·job·profile 을 읽기만 한다 */
// 부르는 곳: ManagerHomeController (GET /manager/home, /manager/schedule).
// 쓰기가 없어 클래스 전체가 readOnly 묶음이다. '오늘' 은 clock.today()(서울 날짜, CODE_GUIDE.md 3-8)
@Service
@Transactional(readOnly = true)
public class ManagerHomeService {
    static final int WEEK_DAYS = 6; // 일정 기본 범위: 시작일 + 6일 (7일치)
    static final String TITLE_IN_PROGRESS = "청소 진행 중"; // 홈 진행 중 카드 제목
    static final String NEAREST_CALL = "가장 가까운 콜"; // 홈 새 콜 카드 요약의 앞말

    private final BookingService bookings;
    private final OfferService offers;
    private final JobService jobs;
    private final ProfileService profiles;
    private final CatalogService catalog;
    private final AppClock clock;

    public ManagerHomeService(BookingService bookings, OfferService offers, JobService jobs, ProfileService profiles, CatalogService catalog, AppClock clock) {
        this.bookings = bookings;
        this.offers = offers;
        this.jobs = jobs;
        this.profiles = profiles;
        this.catalog = catalog;
        this.clock = clock;
    }

    /**
     * 매니저 홈 응답. manager = 이름·승인 여부·정지 안내, today = 날짜 표시·오늘 건수, inProgress = 진행 중 작업 카드(없으면 null),
     * newCalls = 새 콜 카드(없으면 null), remaining = 오늘 아직 시작 안 한(scheduled) 작업 카드.
     * 탈퇴한 매니저면 profiles.requireLive 가 404 를 던진다
     */
    public HomeResponse home(UUID managerId) {
        LocalDate today = clock.today();
        List<Booking> todays = bookings.managerBookings(managerId, today, today); // 오늘 방문일인 내 예약 전부(상태 무관, 약속 시각 순)
        int count = (int) todays.stream().filter(b -> b.getStatus() == BookingStatus.SCHEDULED || BookingStatus.ACTIVE.contains(b.getStatus())).count(); // '예정' = 아직 안 끝난 것. DONE 을 세면 다 끝낸 날 '예정 1건' 과 '예정된 업무가 없어요' 가 같이 뜬다

        // 진행 중(in_progress·paused) 작업은 매니저당 최대 1건. 날짜와 상관없이 찾는다(자정을 넘긴 작업도 보인다).
        // elapsed = 중단 시간을 뺀 경과 "01:23:42", since = "14:02부터 진행 · 강남구 역삼동", jobId = 예약 id.
        Optional<Booking> active = bookings.activeJob(managerId);
        HomeResponse.InProgress inProgress = active.map(b -> new HomeResponse.InProgress(TITLE_IN_PROGRESS, KoreanFormat.hms(jobs.elapsed(b)),
                KoreanFormat.clock(b.getStartedAt()) + "부터 진행 · " + KoreanFormat.addressArea(b.getRoadAddress()), b.getId(),
                b.getStatus() == BookingStatus.PAUSED ? "paused" : "inProgress", b.getStartedAt().toString(), jobs.pausedSecondsOf(b))).orElse(null);

        HomeResponse.NewCalls newCalls = newCalls(managerId);

        List<Booking> remaining = todays.stream().filter(b -> b.getStatus() == BookingStatus.SCHEDULED).toList(); // 남은 일정 = 오늘 아직 시작 안 한 것만
        return new HomeResponse(new HomeResponse.Manager(profiles.requireLive(managerId).getName(), profiles.isApproved(managerId), profiles.suspensionNotice(managerId)), new HomeResponse.Today(KoreanFormat.dateLabel(today), count),
                inProgress, newCalls, items(remaining));
    }

    /** from~to 사이의 내 작업. 기본 오늘~+6일. items = 오늘, week = 날짜별 */
    public ScheduleResponse schedule(UUID managerId, LocalDate from, LocalDate to) {
        LocalDate today = clock.today();
        LocalDate start = from == null ? today : from;
        LocalDate end = to == null ? start.plusDays(WEEK_DAYS) : to;
        List<Booking> list = bookings.managerBookings(managerId, start, end).stream().filter(b -> b.getStatus() != BookingStatus.CANCELLED).toList(); // 취소된 것만 빼고 완료(done)까지 보인다
        // 날짜별로 묶는다. LinkedHashMap = 넣은 순서를 지키는 Map — 목록이 약속 시각 순이라 날짜도 앞에서부터 쌓인다.
        // computeIfAbsent = 그 날짜의 목록이 없으면 새로 만들고, 있으면 있던 목록을 꺼낸다. 예약이 없는 날은 week 에 나오지 않는다
        Map<LocalDate, List<Booking>> byDate = new LinkedHashMap<>();
        for (Booking b : list) byDate.computeIfAbsent(b.getServiceDate(), d -> new ArrayList<>()).add(b);
        List<ScheduleResponse.Day> week = byDate.entrySet().stream()
                .map(e -> new ScheduleResponse.Day(e.getKey().toString(), KoreanFormat.dateLabel(e.getKey()), items(e.getValue()))).toList();
        // Day = (dateKey "2026-09-15", label "9월 15일 (화)", 카드들). items 는 오늘 카드 — 범위에 오늘이 없으면 빈 목록
        return new ScheduleResponse(items(byDate.getOrDefault(today, List.of())), week);
    }

    /**
     * 홈 '새로운 청소 요청' 카드. count = 살아 있는 콜 수, expiry = 가장 먼저 끝나는 콜까지 남은 시간("4:32 후 만료"),
     * summary = 방문 시각이 가장 가까운 콜 "가장 가까운 콜 · 9월 18일 오후 2:00 · 85,000원". 콜이 없으면 null
     */
    private HomeResponse.NewCalls newCalls(UUID managerId) {
        List<Offer> pending = offers.pendingFor(managerId); // 만료 시각이 지난 콜은 이미 빠져 있다
        if (pending.isEmpty()) return null;
        Instant now = clock.now();
        Instant earliest = pending.stream().map(Offer::getExpiresAt).min(Comparator.naturalOrder()).orElseThrow(); // 위에서 빈 목록을 걸렀으니 값이 늘 있다
        // 콜들의 예약을 한 번에 읽어 id → 예약 표로 만든다. 예약을 못 찾은 콜은 '가장 가까운 콜' 후보에서 빠진다(그러면 summary 는 "")
        Map<UUID, Booking> byId = new LinkedHashMap<>();
        for (Booking b : bookings.findAll(pending.stream().map(Offer::getBookingId).toList())) byId.put(b.getId(), b);
        Optional<Offer> nearest = pending.stream().filter(o -> byId.containsKey(o.getBookingId()))
                .min(Comparator.comparing(o -> byId.get(o.getBookingId()).getStartsAt()));
        String summary = nearest.map(o -> {
            Booking b = byId.get(o.getBookingId());
            return NEAREST_CALL + " · " + KoreanFormat.dateTimeShort(b.getServiceDate(), b.getServiceTime()) + " · " + KoreanFormat.won(o.getPayAmount());
        }).orElse("");
        return new HomeResponse.NewCalls(pending.size(), KoreanFormat.untilExpiry(earliest, now), summary);
    }

    /**
     * 예약 목록 → 일정 카드 목록(약속 시각 순). 고객 이름은 한 번에 모아 읽고(namesOf) 가려서 "김○○ 고객 · 지역" 으로.
     * time = "09:00 - 11:00"(방문 시각 + 소요 시간), status = 상태 배지 값(JobService.badge),
     * service = 카테고리 이름. id·jobId 는 둘 다 예약 id
     */
    private List<ScheduleItem> items(List<Booking> list) {
        Set<UUID> customerIds = new HashSet<>();
        for (Booking b : list) customerIds.add(b.getCustomerId());
        Map<UUID, String> names = profiles.namesOf(customerIds);
        return list.stream().sorted(Comparator.comparing(Booking::getStartsAt)).map(b -> new ScheduleItem(b.getId(), b.getId(),
                KoreanFormat.timeRange(b.getServiceTime(), b.getServiceTime().plusMinutes(b.getDurationMinutes())), JobService.badge(b.getStatus()),
                KoreanFormat.maskName(names.getOrDefault(b.getCustomerId(), "")) + " 고객 · " + KoreanFormat.addressArea(b.getRoadAddress()),
                catalog.requireCategory(b.getCategoryKey()).getLabel())).toList();
    }
}
