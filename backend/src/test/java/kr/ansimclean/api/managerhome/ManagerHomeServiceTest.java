package kr.ansimclean.api.managerhome;

import kr.ansimclean.api.booking.Booking;
import kr.ansimclean.api.booking.BookingService;
import kr.ansimclean.api.booking.TestBookings;
import kr.ansimclean.api.catalog.CatalogService;
import kr.ansimclean.api.catalog.ServiceCategory;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.job.JobService;
import kr.ansimclean.api.managerhome.dto.HomeResponse;
import kr.ansimclean.api.managerhome.dto.ScheduleResponse;
import kr.ansimclean.api.offer.Offer;
import kr.ansimclean.api.offer.OfferService;
import kr.ansimclean.api.profile.Profile;
import kr.ansimclean.api.profile.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 매니저 홈·일정 조합 (BACKEND_PLAN.md §6.6) */
// ManagerHomeService 단위 테스트. 스프링·DB 없이 new 로 서비스를 만든다.
// 협력 서비스(예약·콜·작업·프로필·카탈로그)는 가짜(mock)이고, 예약·콜은 TestBookings·Offer.pending 으로 만든 진짜 엔티티다.
// 확인하는 것: 홈 응답(오늘 건수·진행 중 카드·새 콜 요약·남은 일정), 정지 안내, 빈 홈, 일정 화면의 날짜별 묶음.
class ManagerHomeServiceTest {
    static final Instant NOW = Instant.parse("2026-09-15T05:00:00Z"); // 화 14:00
    // 고정 시계(CODE_GUIDE.md 3-8) — '오늘' 과 '만료까지 남은 시간' 이 늘 같게 나온다
    static final AppClock CLOCK = new AppClock(Clock.fixed(NOW, AppClock.ZONE));
    static final LocalDate TODAY = LocalDate.of(2026, 9, 15);
    static final UUID CUSTOMER = UUID.randomUUID();
    static final UUID MANAGER = UUID.randomUUID();

    // when 으로 정하지 않은 호출은 기본값(null·false·0·빈 목록)을 돌려준다 — 예: 정지 안내 테스트 밖에서는 isApproved 가 false
    BookingService bookings = mock(BookingService.class);
    OfferService offers = mock(OfferService.class);
    JobService jobs = mock(JobService.class);
    ProfileService profiles = mock(ProfileService.class);
    CatalogService catalog = mock(CatalogService.class);
    ManagerHomeService service;

    /**
     * 테스트마다 먼저 돈다 (BeforeEach). 매니저 프로필·고객 이름·'가정/원룸 청소' 카테고리를 깔고,
     * 콜 없음·진행 중 작업 없음·예약 없음을 기본으로 둔다 — 각 테스트는 필요한 것만 다시 정한다
     */
    @BeforeEach
    void setUp() {
        service = new ManagerHomeService(bookings, offers, jobs, profiles, catalog, CLOCK);
        when(profiles.requireLive(MANAGER)).thenReturn(Profile.create(MANAGER, "manager", "김매니저", null, "+821099998888"));
        when(profiles.namesOf(any())).thenReturn(Map.of(CUSTOMER, "박지훈")); // any() = 인자가 무엇이든. 화면에는 가린 이름(박○○)이 나간다
        // 카테고리도 가짜 — 카드에 쓰는 이름(getLabel)만 있으면 된다
        ServiceCategory home = mock(ServiceCategory.class);
        when(home.getLabel()).thenReturn("가정/원룸 청소");
        when(catalog.requireCategory("home")).thenReturn(home);
        when(offers.pendingFor(MANAGER)).thenReturn(List.of());
        when(bookings.activeJob(MANAGER)).thenReturn(Optional.empty());
        when(bookings.managerBookings(any(), any(), any())).thenReturn(List.of());
    }

    // 오늘 예약 세 건(끝난 것·진행 중·예정)과 대기 콜 한 건을 준비하고 → 홈 카드 문구를 글자 그대로 확인한다
    @Test
    void 홈은_오늘_건수_진행_중_카드_새_콜_요약_남은_일정을_만든다() {
        Booking active = TestBookings.inProgress(CUSTOMER, MANAGER, TODAY, LocalTime.of(10, 0), Instant.parse("2026-09-15T03:02:00Z")); // 12:02
        Booking later = TestBookings.scheduled(CUSTOMER, MANAGER, TODAY, LocalTime.of(16, 30));
        Booking done = TestBookings.inProgress(CUSTOMER, MANAGER, TODAY, LocalTime.of(8, 0), Instant.parse("2026-09-14T23:00:00Z")); // 서울 9/15 08:00 시작
        done.complete(Instant.parse("2026-09-15T02:00:00Z")); // 끝난 건은 '오늘 예정' 에 세지 않는다 (18b49f5)
        when(bookings.managerBookings(MANAGER, TODAY, TODAY)).thenReturn(List.of(done, active, later));
        when(bookings.activeJob(MANAGER)).thenReturn(Optional.of(active));
        when(jobs.elapsed(active)).thenReturn(Duration.ofSeconds(3600 + 23 * 60 + 42)); // 1시간 23분 42초 → "01:23:42"
        Booking callBooking = TestBookings.assigning(CUSTOMER, LocalDate.of(2026, 9, 18), LocalTime.of(14, 0)); // 콜이 가리키는 매칭 중 예약
        Offer offer = Offer.pending(callBooking.getId(), MANAGER, 1, 85000, NOW.plusSeconds(4 * 60 + 32), false, NOW); // 4분 32초 뒤 만료, 수령액 85,000원
        when(offers.pendingFor(MANAGER)).thenReturn(List.of(offer));
        when(bookings.findAll(anyCollection())).thenReturn(List.of(callBooking)); // 콜들의 예약을 한 번에 읽는 호출

        HomeResponse res = service.home(MANAGER);

        assertThat(res.manager().name()).isEqualTo("김매니저");
        assertThat(res.today().dateLabel()).isEqualTo("9월 15일 (화)");
        assertThat(res.today().count()).isEqualTo(2); // 끝난 건은 빼고 진행 중·예정 두 건
        assertThat(res.inProgress().title()).isEqualTo("청소 진행 중");
        assertThat(res.inProgress().elapsed()).isEqualTo("01:23:42");
        assertThat(res.inProgress().since()).isEqualTo("12:02부터 진행 · 강남구 테헤란로");
        assertThat(res.inProgress().jobId()).isEqualTo(active.getId());
        assertThat(res.newCalls().count()).isEqualTo(1);
        assertThat(res.newCalls().expiry()).isEqualTo("4:32 후 만료");
        assertThat(res.newCalls().summary()).isEqualTo("가장 가까운 콜 · 9월 18일 오후 2:00 · 85,000원");
        assertThat(res.remaining()).hasSize(1); // 남은 일정 = 아직 시작 안 한 16:30 건뿐
        // extracting(이름들) = 객체에서 그 이름의 값들을 꺼내 순서대로 비교한다. 시간은 16:30 + 소요 3시간
        assertThat(res.remaining().get(0)).extracting("time", "status", "customer", "service").containsExactly("16:30 - 19:30", "scheduled", "박○○ 고객 · 강남구 테헤란로", "가정/원룸 청소");
        assertThat(res.remaining().get(0).jobId()).isEqualTo(later.getId());
    }

    // 승인됐고 정지 안내 문구가 있는 매니저 → approved 는 true 그대로, suspension 에 그 문구가 실린다
    @Test
    void 콜_배정이_정지된_매니저는_승인_대기가_아니라_정지_안내를_받는다() { // 자체 점검 #11
        when(profiles.isApproved(MANAGER)).thenReturn(true);
        when(profiles.suspensionNotice(MANAGER)).thenReturn("9/29 까지 콜을 받을 수 없어요");
        HomeResponse res = service.home(MANAGER);
        assertThat(res.manager().approved()).isTrue();
        assertThat(res.manager().suspension()).isEqualTo("9/29 까지 콜을 받을 수 없어요");
    }

    // setUp 의 기본값(콜·진행 중·예약 모두 없음) 그대로 → 카드 자리는 null, 남은 일정은 빈 목록
    @Test
    void 아무것도_없으면_null_과_빈_목록() {
        HomeResponse res = service.home(MANAGER);
        assertThat(res.today().count()).isZero();
        assertThat(res.inProgress()).isNull();
        assertThat(res.newCalls()).isNull();
        assertThat(res.remaining()).isEmpty();
    }

    // from·to 없이 부르면 오늘~+6일을 조회한다 (그 범위일 때만 목록을 돌려주게 정해 두어 기본 범위도 함께 확인된다).
    // 취소된 9/17 09:00 건은 빠지고, 예약이 있는 날(9/15·9/17)만 week 에 나온다
    @Test
    void 일정은_오늘_항목과_날짜별_묶음_취소는_뺀다() {
        Booking today = TestBookings.scheduled(CUSTOMER, MANAGER, TODAY, LocalTime.of(10, 0));
        Booking thu = TestBookings.scheduled(CUSTOMER, MANAGER, LocalDate.of(2026, 9, 17), LocalTime.of(13, 0));
        Booking cancelled = TestBookings.scheduled(CUSTOMER, MANAGER, LocalDate.of(2026, 9, 17), LocalTime.of(9, 0));
        cancelled.cancelBySystem("unassigned", NOW); // 시스템 취소 — 일정에서 빠져야 한다
        when(bookings.managerBookings(MANAGER, TODAY, TODAY.plusDays(6))).thenReturn(List.of(today, thu, cancelled));

        ScheduleResponse res = service.schedule(MANAGER, null, null);

        assertThat(res.items()).hasSize(1); // items = 오늘(9/15) 카드
        // Day::dateKey = 각 날의 dateKey 값을 꺼낸다 (메서드 참조)
        assertThat(res.week()).extracting(ScheduleResponse.Day::dateKey).containsExactly("2026-09-15", "2026-09-17");
        assertThat(res.week().get(1).label()).isEqualTo("9월 17일 (목)");
        assertThat(res.week().get(1).items()).hasSize(1);
    }
}
