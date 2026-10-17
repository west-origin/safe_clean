package kr.ansimclean.api.job;

import kr.ansimclean.api.booking.Booking;
import kr.ansimclean.api.booking.BookingService;
import kr.ansimclean.api.booking.BookingStatus;
import kr.ansimclean.api.booking.TestBookings;
import kr.ansimclean.api.catalog.AppSettingsService;
import kr.ansimclean.api.catalog.CatalogService;
import kr.ansimclean.api.catalog.ServiceCategory;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.events.BookingCompleted;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.common.web.Idempotent;
import kr.ansimclean.api.job.dto.ActiveJobResponse;
import kr.ansimclean.api.job.dto.CompletedJobResponse;
import kr.ansimclean.api.job.dto.ContactResponse;
import kr.ansimclean.api.job.dto.JobResponse;
import kr.ansimclean.api.job.dto.PauseResponse;
import kr.ansimclean.api.job.dto.StartResponse;
import kr.ansimclean.api.live.LiveSessionService;
import kr.ansimclean.api.profile.Profile;
import kr.ansimclean.api.profile.ProfileService;
import kr.ansimclean.api.settlement.Settlement;
import kr.ansimclean.api.settlement.SettlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 매니저 작업 전이의 조건·부수 효과 (BACKEND_PLAN.md §6.1·§6.3·§8.2.1) */
// 서비스 단위 테스트(CODE_GUIDE.md 3-12). JobService 가 기대는 부품은 전부 가짜다.
// 예약(Booking)은 가짜가 아니라 TestBookings 로 만든 진짜 엔티티라, 상태 전이(start·pause·complete)는 실제 규칙대로 바뀐다.
// verify(a, never()) = 한 번도 안 불림, times(n) = n번. any()/eq() 는 인자 조건이다.
class JobServiceTest {
    static final Instant NOW = Instant.parse("2026-09-15T05:00:00Z"); // 화 14:00 서울
    static final AppClock CLOCK = new AppClock(Clock.fixed(NOW, AppClock.ZONE)); // 멈춘 시계 — 서비스의 clock.now() 는 늘 NOW
    static final LocalDate TODAY = LocalDate.of(2026, 9, 15); // NOW 의 서울 날짜
    static final UUID CUSTOMER = UUID.randomUUID();
    static final UUID MANAGER = UUID.randomUUID();

    JobChecklistItemRepository checklist = mock(JobChecklistItemRepository.class);
    JobChecklistEventRepository checklistEvents = mock(JobChecklistEventRepository.class);
    JobPauseRepository pauses = mock(JobPauseRepository.class);
    BookingService bookings = mock(BookingService.class);
    ProfileService profiles = mock(ProfileService.class);
    CatalogService catalog = mock(CatalogService.class);
    LiveSessionService live = mock(LiveSessionService.class);
    SettlementService settlements = mock(SettlementService.class);
    AppSettingsService settings = mock(AppSettingsService.class);
    OutboxService outbox = mock(OutboxService.class);
    Idempotent idempotent = mock(Idempotent.class);
    kr.ansimclean.api.common.supabase.SupabaseStorageClient storage = mock(kr.ansimclean.api.common.supabase.SupabaseStorageClient.class);
    JobService service;
    Booking booking;

    kr.ansimclean.api.profile.ManagerPenaltyService penalties = mock(kr.ansimclean.api.profile.ManagerPenaltyService.class);
    kr.ansimclean.api.booking.BookingDispatcher dispatcher = mock(kr.ansimclean.api.booking.BookingDispatcher.class);

    // 테스트마다: 오늘 10:00 에 배정된(scheduled) 예약 하나를 만들고, 그 예약을 읽기·잠금 조회 모두 이 객체로 돌려주게 꾸민다.
    // 기본값: 진행 중 작업 없음, 모든 중단 사유는 목록에 있음, 중단 구간 없음
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new JobService(checklist, checklistEvents, pauses, bookings, profiles, catalog, live, settlements, settings, outbox, idempotent, CLOCK, storage, penalties, dispatcher);
        booking = TestBookings.scheduled(CUSTOMER, MANAGER, TODAY, LocalTime.of(10, 0));
        when(bookings.requireManagerBooking(MANAGER, booking.getId())).thenReturn(booking);
        when(bookings.lockManagerBooking(MANAGER, booking.getId())).thenReturn(booking); // 상태를 바꾸는 API 는 잠그고 읽는다
        when(bookings.lockManagerBookingWithSeries(MANAGER, booking.getId())).thenReturn(booking); // 완료는 시리즈부터 잠근다
        when(bookings.activeJob(MANAGER)).thenReturn(Optional.empty());
        when(catalog.isReason(eq("pause"), anyString())).thenReturn(true);
        when(pauses.findByBookingIdOrderByPausedAt(any())).thenReturn(List.of());
        when(pauses.save(any())).thenAnswer(inv -> inv.getArgument(0)); // 저장은 받은 객체를 그대로 돌려준다
        // 가짜 멱등 처리: 키를 저장·비교하지 않고 넘겨받은 실제 작업(5번째 인자, Supplier)을 곧바로 실행한다.
        // 그래서 여기서 '재요청' 은 저장된 응답이 아니라 서비스 자체의 '이미 그 상태면 그대로' 규칙으로 확인된다
        when(idempotent.run(any(), any(), any(), any(), any())).thenAnswer(inv -> ((Supplier<Object>) inv.getArgument(4)).get());
        // 예약 카테고리 "home" 의 화면 라벨 — 작업 상세 정보 행·완료 화면 부제에 쓰인다
        ServiceCategory home = mock(ServiceCategory.class);
        when(home.getLabel()).thenReturn("가정/원룸 청소");
        when(catalog.requireCategory("home")).thenReturn(home);
    }

    // 시작하면 in_progress 가 되고 이력 한 줄(scheduled → in_progress). 다시 부르면(키 없이) 상태를 또 바꾸지 않고
    // 처음 시작 시각을 돌려주며 이력도 늘지 않는다
    @Test
    void 시작은_오늘_scheduled_만_되고_이력을_남긴다_재요청은_같은_시각() {
        StartResponse res = service.start(MANAGER, booking.getId(), "k");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.IN_PROGRESS);
        assertThat(res.startedAt()).isEqualTo(NOW.toString());
        verify(bookings).recordTransition(booking, BookingStatus.SCHEDULED, MANAGER, null);

        StartResponse again = service.start(MANAGER, booking.getId(), null);
        assertThat(again.startedAt()).isEqualTo(res.startedAt());
        verify(bookings).recordTransition(any(), any(), any(), any()); // 한 번만
    }

    @Test
    void 다른_날짜_작업이나_다른_활성_작업이_있으면_INVALID_STATE() {
        // 내일 방문 예약은 오늘 시작할 수 없다 (Booking.start 가 막는다)
        Booking tomorrow = TestBookings.scheduled(CUSTOMER, MANAGER, TODAY.plusDays(1), LocalTime.of(10, 0));
        when(bookings.lockManagerBooking(MANAGER, tomorrow.getId())).thenReturn(tomorrow);
        // assertThatThrownBy(…).extracting("code") = 던진 예외의 code 필드를 꺼내 비교한다
        assertThatThrownBy(() -> service.start(MANAGER, tomorrow.getId(), null)).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.INVALID_STATE);

        // 매니저에게 이미 진행 중인 다른 작업이 있으면 오늘 예약도 시작하지 못하고 상태가 그대로다
        Booking other = TestBookings.inProgress(CUSTOMER, MANAGER, TODAY, LocalTime.of(9, 0), NOW.minusSeconds(3600));
        when(bookings.activeJob(MANAGER)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.start(MANAGER, booking.getId(), null)).isInstanceOf(ApiException.class)
                .hasMessage("이미 진행 중인 작업이 있어요");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.SCHEDULED);
    }

    @Test
    void 취소된_예약의_고객_연락처는_주지_않는다() {
        booking.cancelBySystem("customer", NOW); // 배정 뒤 취소 — manager_id 는 남지만 더 볼 수 없다
        assertThatThrownBy(() -> service.contact(MANAGER, booking.getId())).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void 취소된_예약의_작업_상세는_열지_않는다() {
        // 배정 뒤 고객이 취소하면 manager_id 는 남는다 — 배지에 '취소' 가 없어(Figma) '예정·현장 도착' 으로 보이던 문제
        booking.cancelBySystem("customer", NOW);
        assertThatThrownBy(() -> service.job(MANAGER, booking.getId())).isInstanceOf(ApiException.class)
                .hasMessage(JobService.CANCELLED_JOB).extracting("code").isEqualTo(ErrorCode.INVALID_STATE);
    }

    // 라이브가 아직 영상을 내보낼 수 있으면(mayBeStreaming = true) 작업 일시정지를 409 로 거절하고,
    // 상태·중단 구간 어느 것도 바꾸지 않는다 — '영상이 나가는데 상태는 일시정지' 인 순간을 막는다
    @Test
    void 영상이_나가는_중이면_일시정지할_수_없다() {
        booking.start(NOW, TODAY);
        when(live.mayBeStreaming(booking.getId())).thenReturn(true); // live 또는 paused+unmute_pending
        assertThatThrownBy(() -> service.pause(MANAGER, booking.getId(), "고객 요청")).isInstanceOf(ApiException.class)
                .hasMessage("영상을 먼저 중단해주세요").extracting("code").isEqualTo(ErrorCode.INVALID_STATE);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.IN_PROGRESS);
        verify(pauses, never()).save(any());
    }

    // 1) 첫 일시정지: paused + 중단 구간 저장 + 이력(사유) + 라이브 세션에 일시정지 시각 기록
    // 2) 이미 paused 인데 다시 부르면: 새 구간을 만들지 않고 열린 구간(1분 전, 화장실 이용)의 사유·시각을 돌려준다
    // 3) 목록에 없는 사유는 VALIDATION
    @Test
    void 일시정지는_중단_기록을_남기고_이미_중단이면_그대로() {
        booking.start(NOW, TODAY);
        when(live.isStreaming(booking.getId())).thenReturn(false);
        PauseResponse res = service.pause(MANAGER, booking.getId(), "화장실 이용");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PAUSED);
        assertThat(res.since()).isEqualTo("14:00부터 중단됨");
        verify(pauses).save(any());
        verify(bookings).recordTransition(booking, BookingStatus.IN_PROGRESS, MANAGER, "화장실 이용");
        verify(live).jobPaused(booking.getId(), NOW); // 재조정기가 '재개 뒤 영상만 막힘' 과 '작업 일시정지 실패' 를 구분한다 (자체 점검 #15)

        JobPause open = JobPause.open(booking.getId(), "화장실 이용", NOW.minusSeconds(60));
        when(pauses.findByBookingIdOrderByPausedAt(booking.getId())).thenReturn(List.of(open));
        PauseResponse again = service.pause(MANAGER, booking.getId(), "기타");
        assertThat(again.reason()).isEqualTo("화장실 이용");
        assertThat(again.since()).isEqualTo("13:59부터 중단됨");

        when(catalog.isReason("pause", "아무거나")).thenReturn(false);
        assertThatThrownBy(() -> service.pause(MANAGER, booking.getId(), "아무거나")).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.VALIDATION);
    }

    // 5분 전에 열린 중단 구간이 있는 paused 작업을 재개 → in_progress, 구간의 재개 시각 = NOW.
    // 한 번 더 재개해도 이미 진행 중이라 그대로 — 이력은 한 번만 남는다
    @Test
    void 재개는_열린_중단을_닫고_진행_중이면_그대로() {
        booking.start(NOW, TODAY);
        booking.pause();
        JobPause open = JobPause.open(booking.getId(), "고객 요청", NOW.minusSeconds(300));
        when(pauses.findByBookingIdOrderByPausedAt(booking.getId())).thenReturn(List.of(open));
        service.resume(MANAGER, booking.getId());
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.IN_PROGRESS);
        assertThat(open.getResumedAt()).isEqualTo(NOW);
        service.resume(MANAGER, booking.getId());
        verify(bookings).recordTransition(any(), any(), any(), any()); // verify 기본 = 정확히 한 번
        verify(live).jobResumed(booking.getId()); // 매니저가 재개했다 — 영상을 멈춰 둔 표시를 푼다 (live/resume 이 닿지 않아도 재조정기가 다시 켠다)
    }

    // 일시정지 중인 작업을 완료 → done, 열린 중단 구간도 닫히고, 정산 생성·매니저 완료 수 +1·완료 이벤트(아웃박스)가 한 번씩.
    // 두 번째 완료는 이미 done 이라 정산을 또 만들지 않는다
    @Test
    void 완료는_정산_생성_완료_수_증가_고객_푸시를_같이_한다_이미_완료면_아무것도_안_한다() {
        booking.start(NOW, TODAY);
        booking.pause();
        JobPause open = JobPause.open(booking.getId(), "고객 요청", NOW.minusSeconds(300));
        when(pauses.findByBookingIdOrderByPausedAt(booking.getId())).thenReturn(List.of(open));

        service.complete(MANAGER, booking.getId(), "k");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.DONE);
        assertThat(open.getResumedAt()).isEqualTo(NOW);
        verify(settlements).createFor(booking);
        verify(profiles).incrementCompleted(MANAGER);
        verify(outbox).publish(new BookingCompleted(booking.getId(), CUSTOMER, MANAGER));

        service.complete(MANAGER, booking.getId(), null);
        verify(settlements).createFor(any()); // 한 번만
    }

    // 정기청소 회차(seriesId 있음)를 완료하면 endSeriesIfNothingLeft(시리즈, 지금) 가 불린다. 1회 예약(booking)을 완료할 때는
    // 불리지 않아 전체 호출 수가 1번인지 본다
    @Test
    void 정기청소_회차를_마치면_남은_회차가_없는지_보고_시리즈를_닫는다() { // 자체 점검 #8
        UUID seriesId = UUID.randomUUID();
        Booking visit = TestBookings.scheduledSeries(CUSTOMER, MANAGER, seriesId, TODAY, LocalTime.of(10, 0));
        visit.start(NOW, TODAY);
        when(bookings.lockManagerBookingWithSeries(MANAGER, visit.getId())).thenReturn(visit);
        service.complete(MANAGER, visit.getId(), null);
        verify(bookings).endSeriesIfNothingLeft(seriesId, NOW);

        booking.start(NOW, TODAY);
        service.complete(MANAGER, booking.getId(), null); // 1회 예약은 시리즈를 보지 않는다
        verify(bookings, org.mockito.Mockito.times(1)).endSeriesIfNothingLeft(any(), any());
    }

    // 운영자 완료는 매니저 소유권 없이 lockWithSeries 로 잠근다. 매니저 완료와 같은 부수 효과가 나고,
    // 이력의 행위자는 null·사유는 "admin" 이다. 두 번째 호출은 이미 done 이라 정산이 한 번만 생긴다
    @Test
    void 운영자는_청소_완료를_누르지_않은_작업을_매니저_완료와_같게_닫는다() { // 자체 점검 #17
        booking.start(NOW, TODAY);
        when(bookings.lockWithSeries(booking.getId())).thenReturn(booking);
        service.completeByAdmin(booking.getId());
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.DONE);
        verify(settlements).createFor(booking);
        verify(profiles).incrementCompleted(MANAGER);
        verify(bookings).recordTransition(booking, BookingStatus.IN_PROGRESS, null, JobService.REASON_ADMIN_COMPLETE);
        verify(outbox).publish(new BookingCompleted(booking.getId(), CUSTOMER, MANAGER));

        service.completeByAdmin(booking.getId()); // 이미 끝났으면 그대로
        verify(settlements).createFor(any());
    }

    // scheduled 상태에서 완료를 부르면 Booking.complete 가 409 로 막고 정산은 만들지 않는다
    @Test
    void 시작_전에는_완료할_수_없다() {
        assertThatThrownBy(() -> service.complete(MANAGER, booking.getId(), null)).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.INVALID_STATE);
        verify(settlements, never()).createFor(any());
    }

    // 준비: 12:30 시작, 13:00~13:10 닫힌 중단(600초) + 13:55 부터 열린 중단(지금까지 300초).
    // 확인: 중단 합 900초, 경과 = 90분 − 15분 = 01:15:00, 항목 3개가 묶음 이름별(거실 2·주방 1)로 순서대로 모이는지,
    // 요청사항 = 주의사항 라벨 다음에 고객 요청 문구(TestBookings 의 "잘 부탁드려요")
    @Test
    void 진행_중_화면은_중단_시간을_뺀_경과와_체크리스트_묶음을_준다() {
        Booking b = TestBookings.inProgress(CUSTOMER, MANAGER, TODAY, LocalTime.of(10, 0), NOW.minusSeconds(90 * 60)); // 12:30 시작
        b.pause();
        when(bookings.activeJob(MANAGER)).thenReturn(Optional.of(b));
        JobPause closed = JobPause.open(b.getId(), "고객 요청", NOW.minusSeconds(60 * 60));
        closed.close(NOW.minusSeconds(50 * 60));
        JobPause open = JobPause.open(b.getId(), "기타", NOW.minusSeconds(5 * 60));
        when(pauses.findByBookingIdOrderByPausedAt(b.getId())).thenReturn(List.of(closed, open));
        when(live.isStreaming(b.getId())).thenReturn(false);
        when(checklist.findByBookingIdOrderBySort(b.getId())).thenReturn(List.of(
                JobChecklistItem.of(b.getId(), "거실", "바닥 청소", 1), JobChecklistItem.of(b.getId(), "거실", "먼지 제거", 2), JobChecklistItem.of(b.getId(), "주방", "싱크대", 11)));
        when(catalog.noticeLabels(eq("home"), any())).thenReturn(List.of("반려동물이 있어요"));

        ActiveJobResponse res = service.activeJob(MANAGER);

        assertThat(res.pausedSeconds()).isEqualTo(900);
        assertThat(res.elapsed()).isEqualTo("01:15:00");
        assertThat(res.since()).isEqualTo("12:30부터 진행 · 카메라 연결 대기 중");
        assertThat(res.checklist()).extracting(ActiveJobResponse.Group::group).containsExactly("거실", "주방");
        assertThat(res.checklist().get(0).items()).hasSize(2);
        assertThat(res.requests()).containsExactly("반려동물이 있어요", "잘 부탁드려요");
        assertThat(res.title()).isEqualTo("청소 진행 중");
    }

    @Test
    void 오늘이_아닌_작업은_날짜를_붙이고_today_false() { // 직접 사용 점검 U9 — 내일 작업이 "10:00 시작" 으로만 보여 오늘처럼 읽혔다
        Booking tomorrow = TestBookings.scheduled(CUSTOMER, MANAGER, TODAY.plusDays(1), LocalTime.of(14, 0));
        when(bookings.requireManagerBooking(MANAGER, tomorrow.getId())).thenReturn(tomorrow);
        JobResponse job = service.job(MANAGER, tomorrow.getId());
        assertThat(job.startLabel()).isEqualTo("9월 16일 (수) 14:00 시작");
        assertThat(job.today()).isFalse();
    }

    // 작업 상세: 배지·시작 문구·주소 두 줄·정보 행(고객 이름은 '김○○' 로 가림)·정산 예정액(85,000 − 수수료 8,500)·작업 범위·요청사항.
    // 거주 정보가 있으면 정보 행이 4줄이 된다. 연락처: 오늘 방문은 번호를 010-…-… 모양으로 주고, 이틀 뒤 방문은 404
    @Test
    void 작업_상세와_연락처_매핑() {
        when(profiles.namesOf(any())).thenReturn(Map.of(CUSTOMER, "김하늘"));
        when(catalog.optionLabels("home", List.of("dishes"))).thenReturn(List.of("설거지"));
        when(catalog.optionLabels("home", List.of("pet"))).thenReturn(List.of("반려동물 배변 청소"));
        when(catalog.noticeLabels("home", List.of("pet"))).thenReturn(List.of("반려동물이 있어요"));

        JobResponse job = service.job(MANAGER, booking.getId());
        assertThat(job.status()).isEqualTo("scheduled");
        assertThat(job.startLabel()).isEqualTo("10:00 시작");
        assertThat(job.today()).isTrue();
        assertThat(job.address()).containsExactly(TestBookings.ROAD, "3층");
        assertThat(job.info()).extracting(JobResponse.Row::value).containsExactly("김○○", "가정/원룸 청소", "3시간");

        // 가입 때 받은 거주 정보가 있으면 한 줄 더 (검증 검토: 저장만 하고 아무도 읽지 않았다)
        when(profiles.housingSummary(CUSTOMER)).thenReturn("아파트·빌라 · 30평 · 방 3 · 반려동물 있음");
        assertThat(service.job(MANAGER, booking.getId()).info()).extracting(JobResponse.Row::label).containsExactly("고객", "청소 유형", "예상 소요", "주거 정보");
        assertThat(job.pay()).isEqualTo("76,500원");
        assertThat(job.scope()).containsExactly("설거지", "반려동물 배변 청소");
        assertThat(job.requests()).containsExactly("반려동물이 있어요", "잘 부탁드려요");

        when(profiles.requireLive(CUSTOMER)).thenReturn(Profile.create(CUSTOMER, "customer", "김하늘", null, "+821012345678"));
        ContactResponse contact = service.contact(MANAGER, booking.getId());
        assertThat(contact.phone()).isEqualTo("010-1234-5678");
        assertThat(contact.mapQuery()).isEqualTo(TestBookings.ROAD);

        Booking later = TestBookings.scheduled(CUSTOMER, MANAGER, TODAY.plusDays(2), LocalTime.of(10, 0));
        when(bookings.requireManagerBooking(MANAGER, later.getId())).thenReturn(later);
        assertThatThrownBy(() -> service.contact(MANAGER, later.getId())).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.NOT_FOUND);
    }

    // 준비: 14:02 시작 → 17:25 완료, 항목 2개 중 1개 체크, 녹화 ready, 정산 행(85,000 − 8,500).
    // 확인: 요약 4행의 값·색(tone), 정산 예정액 = 정산 행의 최종 금액. 끝의 두 줄은 recordingRow 를 따로 불러 실패의 색(issue)·없음의 문구를 본다
    @Test
    void 완료_화면은_청소_시간_소요_체크리스트_촬영_기록_정산을_요약한다() {
        Booking b = TestBookings.inProgress(CUSTOMER, MANAGER, TODAY, LocalTime.of(10, 0), Instant.parse("2026-09-15T05:02:00Z"));
        b.complete(Instant.parse("2026-09-15T08:25:00Z"));
        when(bookings.lastCompleted(MANAGER)).thenReturn(Optional.of(b));
        JobChecklistItem done = JobChecklistItem.of(b.getId(), "거실", "바닥 청소", 1);
        done.mark(true, NOW);
        when(checklist.findByBookingIdOrderBySort(b.getId())).thenReturn(List.of(done, JobChecklistItem.of(b.getId(), "거실", "먼지 제거", 2)));
        when(live.recordingStatus(b.getId())).thenReturn("ready");
        when(settlements.findByBooking(b.getId())).thenReturn(Optional.of(Settlement.pending(b.getId(), MANAGER, 85000, 8500, NOW)));
        when(settings.get(JobService.SETTING_COMPLETE_TITLE, String.class)).thenReturn("청소가 완료되었습니다");
        when(settings.get(JobService.SETTING_SETTLEMENT_NOTE, String.class)).thenReturn("정산은 다음 정산일에 지급됩니다.");

        CompletedJobResponse res = service.lastCompleted(MANAGER);

        assertThat(res.title()).isEqualTo("청소가 완료되었습니다");
        assertThat(res.subtitle()).isEqualTo("강남구 테헤란로 · 가정/원룸 청소");
        assertThat(res.summary()).extracting(CompletedJobResponse.SummaryRow::value).containsExactly("14:02 - 17:25", "3시간 23분", "2개 중 1개 완료", "정상 업로드");
        assertThat(res.summary()).extracting(CompletedJobResponse.SummaryRow::tone).containsExactly("default", "default", "default", "success");
        assertThat(res.payout().amount()).isEqualTo("76,500원");
        assertThat(JobService.recordingRow("failed").tone()).isEqualTo("issue");
        assertThat(JobService.recordingRow("none").value()).isEqualTo("기록 없음");
    }

    // 시작 전(scheduled)에는 409. 시작한 뒤에는 항목이 체크되고 체크 시각(doneAt)이 NOW 로 남는다
    @Test
    void 체크리스트는_진행_중일_때만_바꿀_수_있다() {
        JobChecklistItem item = JobChecklistItem.of(booking.getId(), "거실", "바닥 청소", 1);
        when(checklist.findByIdAndBookingId(item.getId(), booking.getId())).thenReturn(Optional.of(item));
        assertThatThrownBy(() -> service.setChecklistItem(MANAGER, booking.getId(), item.getId(), true)).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.INVALID_STATE);
        booking.start(NOW, TODAY);
        service.setChecklistItem(MANAGER, booking.getId(), item.getId(), true);
        assertThat(item.isDone()).isTrue();
        assertThat(item.getDoneAt()).isEqualTo(NOW);
    }

    @Test
    void 같은_체크는_시각과_이력을_보존하고_해제_재체크는_남긴다() {
        JobChecklistItem item = JobChecklistItem.of(booking.getId(), "거실", "바닥 청소", 1);
        when(checklist.findByIdAndBookingId(item.getId(), booking.getId())).thenReturn(Optional.of(item));
        booking.start(NOW, TODAY);
        service.setChecklistItem(MANAGER, booking.getId(), item.getId(), true);
        service.setChecklistItem(MANAGER, booking.getId(), item.getId(), true);
        assertThat(item.getDoneAt()).isEqualTo(NOW);
        service.setChecklistItem(MANAGER, booking.getId(), item.getId(), false);
        assertThat(item.getDoneAt()).isNull();
        service.setChecklistItem(MANAGER, booking.getId(), item.getId(), true);
        var saved = org.mockito.ArgumentCaptor.forClass(JobChecklistEvent.class);
        verify(checklistEvents, org.mockito.Mockito.times(3)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(JobChecklistEvent::isDone).containsExactly(true, false, true);
        assertThat(saved.getAllValues()).allSatisfy(event -> {
            assertThat(event.getManagerId()).isEqualTo(MANAGER);
            assertThat(event.getLabel()).isEqualTo("바닥 청소");
            assertThat(event.getChangedAt()).isEqualTo(NOW);
        });
        verify(bookings, org.mockito.Mockito.times(4)).lockManagerBooking(MANAGER, booking.getId());
    }

    @Test
    void 고객_체크리스트는_현재_완료만_정확한_녹화_기준으로_계산한다() {
        when(bookings.requireCustomerBooking(CUSTOMER, booking.getId())).thenReturn(booking);
        when(catalog.noticeLabels(eq("home"), any())).thenReturn(List.of());
        Instant rec = NOW.minusSeconds(600);
        JobChecklistItem floor = JobChecklistItem.of(booking.getId(), "거실", "바닥 청소", 1);
        JobChecklistItem dust = JobChecklistItem.of(booking.getId(), "거실", "먼지 제거", 2);
        JobChecklistItem sink = JobChecklistItem.of(booking.getId(), "주방", "싱크대", 3);
        floor.mark(true, rec.plusSeconds(165));
        sink.mark(true, rec.minusSeconds(30));
        when(checklist.findByBookingIdOrderBySort(booking.getId())).thenReturn(List.of(floor, dust, sink));
        when(checklistEvents.findLatestByBookingId(booking.getId())).thenReturn(List.of(
                JobChecklistEvent.of(floor, true, floor.getDoneAt(), MANAGER),
                JobChecklistEvent.of(dust, false, rec.plusSeconds(190), MANAGER),
                JobChecklistEvent.of(sink, true, sink.getDoneAt(), MANAGER)));
        var session = kr.ansimclean.api.live.LiveSession.create(booking.getId());
        session.noteRecordingStart(rec, false);
        when(live.find(booking.getId())).thenReturn(Optional.of(session));
        var provisional = service.customerChecklist(CUSTOMER, booking.getId());
        assertThat(provisional.timeline()).allSatisfy(mark -> assertThat(mark.offsetSeconds()).isNull());
        session.noteRecordingStart(rec, true);
        var res = service.customerChecklist(CUSTOMER, booking.getId());
        assertThat(res.done()).isEqualTo(2);
        assertThat(res.total()).isEqualTo(3);
        assertThat(res.percent()).isEqualTo(66);
        assertThat(res.groups()).extracting(kr.ansimclean.api.job.dto.CustomerChecklistResponse.Group::group).containsExactly("거실", "주방");
        assertThat(res.timeline()).extracting(kr.ansimclean.api.job.dto.CustomerChecklistResponse.Mark::offsetSeconds).containsExactly(null, 165L);
        assertThat(res.requests()).containsExactly("잘 부탁드려요");
        // 과거 항목은 현재 완료 수를 유지하지만 없던 이력을 만들지 않는다.
        when(checklistEvents.findLatestByBookingId(booking.getId())).thenReturn(List.of());
        assertThat(service.customerChecklist(CUSTOMER, booking.getId()).timeline()).allSatisfy(mark -> assertThat(mark.offsetSeconds()).isNull());
    }

    @Test
    void 배정_전은_빈_체크리스트이고_영상_위치는_길이를_넘지_않는다() {
        when(bookings.requireCustomerBooking(CUSTOMER, booking.getId())).thenReturn(booking);
        when(catalog.noticeLabels(eq("home"), any())).thenReturn(List.of());
        var res = service.customerChecklist(CUSTOMER, booking.getId());
        assertThat(res.total()).isZero();
        assertThat(res.percent()).isZero();
        assertThat(res.timeline()).isEmpty();
        assertThat(JobService.offsetSeconds(NOW.plusSeconds(700), NOW, 630)).isEqualTo(630L);
        assertThat(JobService.offsetSeconds(NOW.minusSeconds(1), NOW, 630)).isNull();
    }

    // 1) 알려진 사유("sick")로 취소: 멱등 키가 run 에 넘어가고, 예약은 assigning(매니저 비움)으로 돌아가 다시 콜을 보내며,
    //    패널티 한 줄과 아웃박스 이벤트 두 개(고객 안내·바디캠 정리)가 남는다
    // 2) 사유 목록에 없는 key 는 거부 3) 컷오프 안(cancelCategory 가 null)이면 패널티 서비스가 준 오류 문구로 거부
    @Test
    void 매니저_방문_취소는_예약을_다시_매칭하고_패널티를_남긴다_컷오프_안이면_거부() {
        // 사유 목록·구간은 패널티 서비스가 판단한다 (docs/manager-policy.md)
        when(penalties.reasons()).thenReturn(tools.jackson.databind.json.JsonMapper.builder().build().readTree("[{\"key\":\"sick\"}]"));
        when(penalties.cancelCategory(any(), any())).thenReturn(kr.ansimclean.api.profile.ManagerPenalty.CANCEL_LATE);

        service.cancelByManager(MANAGER, booking.getId(), "sick", "몸이 아파요", "k");
        verify(idempotent).run(eq("k"), eq(MANAGER), any(), eq(kr.ansimclean.api.booking.dto.ReservationResponse.class), any()); // 재시도는 저장된 응답 (자체 점검 #44)

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.ASSIGNING); // 취소가 아니라 재매칭
        assertThat(booking.getManagerId()).isNull();
        verify(penalties).record(MANAGER, booking.getId(), booking.getSeriesId(), kr.ansimclean.api.profile.ManagerPenalty.CANCEL_LATE, "sick", "몸이 아파요");
        verify(dispatcher).onReleased(booking, MANAGER); // 새 회차로 다시 콜, 취소한 매니저만 뺀다 (전체 재점검 O1)
        verify(outbox).publish(new kr.ansimclean.api.common.events.ManagerReleased(booking.getId(), CUSTOMER));
        verify(outbox).publish(new kr.ansimclean.api.common.events.CameraReleased(booking.getId(), MANAGER)); // 바디캠을 대기 방으로 (전체 재점검 J1)

        // 모르는 사유는 막는다
        Booking other = TestBookings.scheduled(CUSTOMER, MANAGER, TODAY, LocalTime.of(10, 0));
        when(bookings.lockManagerBooking(MANAGER, other.getId())).thenReturn(other);
        assertThatThrownBy(() -> service.cancelByManager(MANAGER, other.getId(), "unknown", null, null)).isInstanceOf(ApiException.class);

        // 컷오프(4시간) 안이면 앱에서 취소 불가
        Booking soon = TestBookings.scheduled(CUSTOMER, MANAGER, TODAY, LocalTime.of(10, 0));
        when(bookings.lockManagerBooking(MANAGER, soon.getId())).thenReturn(soon);
        when(penalties.cancelCategory(any(), any())).thenReturn(null);
        when(penalties.cutoffException()).thenReturn(new ApiException(ErrorCode.INVALID_STATE, "고객센터로 전화해주세요"));
        assertThatThrownBy(() -> service.cancelByManager(MANAGER, soon.getId(), "sick", null, null))
                .isInstanceOf(ApiException.class).hasMessage("고객센터로 전화해주세요");
    }

    // 방문 취소 뒤 다시 매칭할 후보가 한 명도 없어 디스패처가 곧바로 매칭 실패로 표시했다 → 고객에게는 실패 안내(MatchingFailed, 디스패처가 적는다) 하나만.
    // '다른 매니저를 찾고 있어요'(ManagerReleased)는 적지 않는다. 바디캠 정리(CameraReleased)는 그대로
    @Test
    void 방문_취소_뒤_재매칭이_바로_실패하면_찾고_있어요_알림은_보내지_않는다() { // 문제 목록 5번
        when(penalties.reasons()).thenReturn(tools.jackson.databind.json.JsonMapper.builder().build().readTree("[{\"key\":\"sick\"}]"));
        when(penalties.cancelCategory(any(), any())).thenReturn(kr.ansimclean.api.profile.ManagerPenalty.CANCEL_EARLY);
        org.mockito.Mockito.doAnswer(inv -> ((Booking) inv.getArgument(0)).markMatchingFailed(NOW)).when(dispatcher).onReleased(any(), any());

        service.cancelByManager(MANAGER, booking.getId(), "sick", null, null);

        verify(outbox, never()).publish(any(kr.ansimclean.api.common.events.ManagerReleased.class));
        verify(outbox).publish(new kr.ansimclean.api.common.events.CameraReleased(booking.getId(), MANAGER));
    }

    // 밤이라 디스패처가 새 콜을 아침으로 미뤘으면(보류 — 실패 표시 없음) 다시 찾는 중이므로 '다른 매니저를 찾고 있어요' 가 맞다 (문제 목록 2번과 함께)
    @Test
    void 방문_취소_뒤_재매칭을_아침으로_미뤘으면_찾고_있어요_알림을_보낸다() {
        when(penalties.reasons()).thenReturn(tools.jackson.databind.json.JsonMapper.builder().build().readTree("[{\"key\":\"sick\"}]"));
        when(penalties.cancelCategory(any(), any())).thenReturn(kr.ansimclean.api.profile.ManagerPenalty.CANCEL_EARLY);
        org.mockito.Mockito.doAnswer(inv -> {
            ((Booking) inv.getArgument(0)).holdDispatch(NOW, null, false);
            return null;
        }).when(dispatcher).onReleased(any(), any());

        service.cancelByManager(MANAGER, booking.getId(), "sick", null, null);

        verify(outbox).publish(new kr.ansimclean.api.common.events.ManagerReleased(booking.getId(), CUSTOMER));
    }

    // 지각 기준 30분으로 두고 10:00 예약을 14:00(NOW)에 시작 → TARDY 패널티가 이 매니저·예약으로 한 줄 기록되는지 본다
    @Test
    void 약속_시간보다_30분_넘게_늦게_시작하면_지각_패널티() {
        when(penalties.tardyMinutes()).thenReturn(30);
        Booking late = TestBookings.scheduled(CUSTOMER, MANAGER, TODAY, LocalTime.of(10, 0)); // NOW 는 서울 14:00 → 4시간 지각
        when(bookings.lockManagerBooking(MANAGER, late.getId())).thenReturn(late);

        service.start(MANAGER, late.getId(), "k2");

        verify(penalties).record(eq(MANAGER), eq(late.getId()), any(), eq(kr.ansimclean.api.profile.ManagerPenalty.TARDY), isNull(), anyString());
    }

    // 경계: 지각 기준 30분, NOW(14:00)에 시작. 13:30:00 예약 = 정확히 30분 0초 늦음 → '30분을 넘겨' 가 아니라 기록하지 않는다.
    // 13:29:30 예약 = 30분 30초 늦음 → 기록한다. 고객 지각 안내(LateStartNotifier)는 약속 + 30분이 되도록 시작하지 않았으면 보내므로
    // 안내를 받은 예약은 모두 약속 + 30분보다 뒤에 시작한다 — 두 기준이 같다 (문제 목록 26번, 예전: 분 아래를 버려 31분부터)
    @Test
    void 지각은_약속_시간_30분_0초는_아니고_30분_30초부터다() {
        when(penalties.tardyMinutes()).thenReturn(30);
        Booking onTheLine = TestBookings.scheduled(CUSTOMER, MANAGER, TODAY, LocalTime.of(13, 30));        // 30분 0초 늦음
        Booking justOver = TestBookings.scheduled(CUSTOMER, MANAGER, TODAY, LocalTime.of(13, 29, 30));     // 30분 30초 늦음
        when(bookings.lockManagerBooking(MANAGER, onTheLine.getId())).thenReturn(onTheLine);
        when(bookings.lockManagerBooking(MANAGER, justOver.getId())).thenReturn(justOver);

        service.start(MANAGER, onTheLine.getId(), "k3");
        verify(penalties, never()).record(any(), eq(onTheLine.getId()), any(), any(), any(), any());

        when(bookings.activeJob(MANAGER)).thenReturn(Optional.empty()); // 앞 작업은 가짜 조회라 진행 중으로 잡히지 않는다
        service.start(MANAGER, justOver.getId(), "k4");
        verify(penalties).record(eq(MANAGER), eq(justOver.getId()), any(), eq(kr.ansimclean.api.profile.ManagerPenalty.TARDY), isNull(), eq("30분 늦게 시작"));
    }
}
