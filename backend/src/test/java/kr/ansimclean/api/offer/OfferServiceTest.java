package kr.ansimclean.api.offer;

import kr.ansimclean.api.booking.Booking;
import kr.ansimclean.api.booking.BookingService;
import kr.ansimclean.api.booking.BookingStatus;
import kr.ansimclean.api.booking.TestBookings;
import kr.ansimclean.api.catalog.AppSettingsService;
import kr.ansimclean.api.catalog.CatalogService;
import kr.ansimclean.api.catalog.ServiceCategory;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.events.BookingAssigned;
import kr.ansimclean.api.common.events.MatchingFailed;
import kr.ansimclean.api.common.events.OffersDispatched;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.common.web.Idempotent;
import kr.ansimclean.api.job.JobService;
import kr.ansimclean.api.live.LiveSessionService;
import kr.ansimclean.api.offer.dto.AcceptResponse;
import kr.ansimclean.api.offer.dto.CallsResponse;
import kr.ansimclean.api.offer.dto.RejectResponse;
import kr.ansimclean.api.profile.ManagerProfile;
import kr.ansimclean.api.profile.ProfileService;
import kr.ansimclean.api.schedule.AvailabilityService;
import kr.ansimclean.api.schedule.dto.AvailabilityStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 매칭·수락·거절·만료 규칙 (BACKEND_PLAN.md §6.2). 협력자는 mock, 예약·오퍼 엔티티는 진짜 */
// OfferService 단위 테스트 (스프링·DB 없음). 저장소·다른 서비스는 Mockito 가짜, 예약(Booking)·콜(Offer)·매니저 프로필은 진짜 엔티티라
// 상태 전이 규칙이 그대로 돈다. DB 가 없으므로 '무엇을 저장했나' 는 offers.save 에 넘어간 인자를 ArgumentCaptor 로 붙잡아 본다.
// 기본 준비(setUp): 일반 콜 30분·우선 콜 10분·최대 3회차, 후보는 강남구 승인 매니저 A·B, 예약은 9/17(목) 10:00 매칭 중.
// verify(…) 에 횟수를 적지 않으면 정확히 1번 불렸는지 본다(CODE_GUIDE.md 3-12). never() = 한 번도, times(n) = 정확히 n번
class OfferServiceTest {
    static final Instant NOW = Instant.parse("2026-09-15T05:00:00Z"); // 화 14:00 서울
    static final AppClock CLOCK = new AppClock(Clock.fixed(NOW, AppClock.ZONE)); // 멈춘 시계 (서울 시간대)
    static final UUID CUSTOMER = UUID.randomUUID();
    static final UUID A = UUID.randomUUID();
    static final UUID B = UUID.randomUUID();
    static final UUID C = UUID.randomUUID();

    OfferRepository offers = mock(OfferRepository.class);
    BookingService bookings = mock(BookingService.class);
    ProfileService profiles = mock(ProfileService.class);
    AvailabilityService availability = mock(AvailabilityService.class);
    JobService jobs = mock(JobService.class);
    LiveSessionService live = mock(LiveSessionService.class);
    CatalogService catalog = mock(CatalogService.class);
    AppSettingsService settings = mock(AppSettingsService.class);
    OutboxService outbox = mock(OutboxService.class);
    Idempotent idempotent = mock(Idempotent.class);
    kr.ansimclean.api.profile.ManagerPenaltyService penalties = mock(kr.ansimclean.api.profile.ManagerPenaltyService.class);
    OfferService service;
    Booking booking; // 테스트마다 새로 만드는 9/17 10:00 매칭 중 예약 (3시간)

    /** 승인된 매니저 프로필(진짜 엔티티). area = '시도 시군구' 활동 지역, sido = 시·도, 활동 시작일 2024-03-01 */
    static ManagerProfile manager(UUID id, String area, String sido) {
        ManagerProfile m = ManagerProfile.create(id, NOW);
        m.approve(List.of(area), sido, LocalDate.of(2024, 3, 1), NOW);
        return m;
    }

    /**
     * 테스트마다 먼저 돈다. 서비스와 예약을 새로 만들고 운영 설정 값·후보 매니저·카탈로그를 깐다.
     * 근무 가능은 기본 true, 거절 사유는 무엇이든 유효로 둔다 — 테스트가 필요한 것만 다시 정한다
     */
    @BeforeEach
    @SuppressWarnings("unchecked") // 아래 Supplier 로의 형 변환 경고를 끈다
    void setUp() {
        service = new OfferService(offers, bookings, profiles, availability, jobs, live, catalog, settings, outbox, idempotent, CLOCK, penalties);
        booking = TestBookings.assigning(CUSTOMER, LocalDate.of(2026, 9, 17), LocalTime.of(10, 0));
        when(settings.integer(OfferService.SETTING_OFFER_TTL)).thenReturn(30);
        when(settings.integer(OfferService.SETTING_PREFERRED_TTL)).thenReturn(10);
        when(settings.integer(OfferService.SETTING_MAX_ROUNDS)).thenReturn(3);
        when(settings.get(OfferService.SETTING_ETA, String.class)).thenReturn("이동 시간은 배정 후 안내");
        // thenAnswer = 불릴 때마다 이 함수로 결과를 만든다 (inv = 그 호출, getArgument(n) = n번째 인자, 0부터).
        // 멱등 처리 가짜: 5번째 인자(실제 업무 람다)를 바로 실행한다 — 키 기록 없이 수락 로직이 그대로 돈다
        when(idempotent.run(any(), any(), any(), any(), any())).thenAnswer(inv -> ((Supplier<Object>) inv.getArgument(4)).get());
        when(offers.save(any())).thenAnswer(inv -> inv.getArgument(0)); // 저장은 받은 객체를 그대로 돌려준다
        when(profiles.approvedManagers()).thenReturn(List.of(manager(A, "서울특별시 강남구", "서울특별시"), manager(B, "서울특별시 강남구", "서울특별시")));
        when(profiles.lockManager(any())).thenAnswer(inv -> manager(inv.getArgument(0), "서울특별시 강남구", "서울특별시")); // 수락 시 잠근 프로필은 승인 상태
        when(availability.isAvailable(any(), any(), any(), any())).thenReturn(true);
        when(catalog.isReason(eq("reject"), anyString())).thenReturn(true);
        // 카테고리는 가짜 — 콜 카드 문구에 쓰는 이름(getLabel)만 정한다
        ServiceCategory home = mock(ServiceCategory.class);
        when(home.getLabel()).thenReturn("가정/원룸 청소");
        when(catalog.requireCategory("home")).thenReturn(home);
    }

    // A 의 대기 콜을 준비하고, 수락 때 잠그는 매니저 프로필을 미승인으로 돌려준다 → INVALID_STATE, 콜은 pending 그대로
    @Test
    void 승인이_취소된_매니저는_남은_오퍼를_수락할_수_없다() {
        Offer o = pendingOffer(A);
        ManagerProfile revoked = ManagerProfile.create(A, NOW);
        when(profiles.lockManager(A)).thenReturn(revoked); // approved=false
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.accept(A, o.getId(), null))
                .isInstanceOf(kr.ansimclean.api.common.error.ApiException.class).extracting("code").isEqualTo(kr.ansimclean.api.common.error.ErrorCode.INVALID_STATE);
        assertThat(o.getStatus()).isEqualTo(Offer.PENDING);
    }

    // 승인됐지만 오늘부터 7일 정지된 A → INVALID_STATE. 매니저 검사가 먼저라 예약은 잠그지도 않는다
    @Test
    void 콜_배정_정지_중인_매니저는_정지_전에_받은_오퍼도_수락할_수_없다() { // 감사 M-07
        Offer o = pendingOffer(A);
        ManagerProfile suspended = ManagerProfile.create(A, NOW);
        suspended.approve(List.of("서울특별시 강남구"), "서울특별시", CLOCK.today(), NOW);
        suspended.suspendUntil(CLOCK.today().plusDays(7), NOW);
        when(profiles.lockManager(A)).thenReturn(suspended);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.accept(A, o.getId(), null))
                .isInstanceOf(kr.ansimclean.api.common.error.ApiException.class).extracting("code").isEqualTo(kr.ansimclean.api.common.error.ErrorCode.INVALID_STATE);
        assertThat(o.getStatus()).isEqualTo(Offer.PENDING);
        verify(bookings, never()).lockForUpdate(any());
    }

    @Test
    void 매니저가_방문을_취소하면_그_수락을_무효로_하고_새_회차로_처음_콜_받았던_매니저에게도_다시_보낸다() { // 전체 재점검 O1
        // A 가 수락, B 의 같은 회차 오퍼는 수락 때 cancelled — 예전에는 A 의 accepted 가 남아 누구도 다시 수락하지 못했고(offers_one_accepted),
        // 같은 회차라 B 도 후보에서 빠져 '매칭 후보 없음' 이 됐다
        Offer accepted = Offer.pending(booking.getId(), A, 1, 76500, NOW.plusSeconds(600), false, NOW);
        accepted.accept(NOW);
        Offer cancelled = Offer.pending(booking.getId(), B, 1, 76500, NOW.plusSeconds(600), false, NOW);
        cancelled.cancel();
        when(offers.findByBookingId(booking.getId())).thenReturn(List.of(accepted, cancelled));
        booking.assign(A, NOW);
        booking.releaseByManager();

        service.onReleased(booking, A);

        assertThat(accepted.getStatus()).isEqualTo(Offer.SUPERSEDED);
        assertThat(booking.getDispatchRound()).isEqualTo(2);
        // ArgumentCaptor = 가짜 메서드에 넘어간 인자를 붙잡아 두었다가 getValue()·getAllValues() 로 꺼내 본다
        ArgumentCaptor<Offer> sent = ArgumentCaptor.forClass(Offer.class);
        verify(offers).save(sent.capture());
        assertThat(sent.getValue().getManagerId()).isEqualTo(B); // A(취소한 매니저)만 빠진다
        assertThat(sent.getValue().getRound()).isEqualTo(2);
    }

    // 네 매니저 중 지역·근무 가능·겹치는 작업 없음을 모두 통과하는 A 만 남는다.
    // 근무 가능 검사는 방문 날짜와 시작·끝 시각(10:00~13:00, 소요 3시간)으로 불린다
    @Test
    void 후보는_승인_지역_근무시간_겹침을_모두_통과한_매니저만() {
        UUID d = UUID.randomUUID();
        when(profiles.approvedManagers()).thenReturn(List.of(
                manager(A, "서울특별시 강남구", "서울특별시"),
                manager(B, "부산광역시 해운대구", "부산광역시"),   // 지역 밖
                manager(C, "서울특별시 강남구", "서울특별시"),   // 근무 불가
                manager(d, "서울특별시 강남구", "서울특별시"))); // 겹치는 작업
        when(availability.isAvailable(eq(C), any(), any(), any())).thenReturn(false);
        when(bookings.hasOverlappingWork(eq(d), any(), any())).thenReturn(true);

        assertThat(service.findCandidates(booking, Set.of())).containsExactly(A);
        verify(availability).isAvailable(A, LocalDate.of(2026, 9, 17), LocalTime.of(10, 0), LocalTime.of(13, 0));
    }

    // 시·도는 "서울특별시", 시군구는 null 인 예약 → 매니저의 시·도가 같으면 후보, 다르면 아니다
    @Test
    void 시군구가_없는_예약은_시도만_비교한다() {
        Booking noSigungu = Booking.create(CUSTOMER, null, "home", "oneTime", LocalDate.of(2026, 9, 17), LocalTime.of(10, 0), "서울 어딘가", null, "서울특별시", null,
                List.of(), List.of(), List.of(), null, null, TestBookings.QUOTE, NOW);
        assertThat(OfferService.inArea(manager(A, "서울특별시 강남구", "서울특별시"), noSigungu)).isTrue();
        assertThat(OfferService.inArea(manager(B, "부산광역시 해운대구", "부산광역시"), noSigungu)).isFalse();
    }

    @Test
    void 매칭은_후보_전원에게_30분_오퍼를_만들고_이벤트를_발행한다() {
        service.dispatch(booking, Set.of());

        ArgumentCaptor<Offer> saved = ArgumentCaptor.forClass(Offer.class);
        verify(offers, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Offer::getManagerId).containsExactly(A, B);
        assertThat(saved.getAllValues()).allSatisfy(o -> {
            assertThat(o.getRound()).isEqualTo(1);
            assertThat(o.getPayAmount()).isEqualTo(76500);
            assertThat(o.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
            assertThat(o.isPreferred()).isFalse();
        });
        verify(outbox).publish(new OffersDispatched(booking.getId(), List.of(A, B), false)); // 콜 알림 푸시용 아웃박스 이벤트 (false = 우선 콜 아님)
    }

    // 1회차에 A 가 이미 받았으면 B 에게만 보낸다. 이어서 B 까지 제외하면 후보 0명 = 매칭 실패 처리
    @Test
    void 같은_회차에_이미_오퍼를_받은_매니저와_제외_목록은_뺀다_후보가_없으면_아무것도_안_한다() {
        when(offers.findByBookingId(booking.getId())).thenReturn(List.of(Offer.pending(booking.getId(), A, 1, 76500, NOW.plusSeconds(60), false, NOW)));
        service.dispatch(booking, Set.of());
        ArgumentCaptor<Offer> saved = ArgumentCaptor.forClass(Offer.class);
        verify(offers).save(saved.capture());
        assertThat(saved.getValue().getManagerId()).isEqualTo(B);

        service.dispatch(booking, Set.of(B));
        verify(offers, times(1)).save(any()); // 더 저장되지 않았다
        // 후보 0명 = 매칭 실패: 예약에 표시하고 고객에게 한 번만 알린다 (검증 검토)
        assertThat(booking.getMatchingFailedAt()).isEqualTo(NOW);
        verify(outbox).publish(new MatchingFailed(booking.getId(), CUSTOMER, true, true)); // 9/17 10:00 예약 — 자동 재매칭(다음 실행 14:05)도 일정 변경(24시간 전)도 된다
        service.dispatch(booking, Set.of(B));
        verify(outbox, times(1)).publish(any(MatchingFailed.class)); // 두 번째는 알리지 않는다
    }

    // isApprovedManager(A) 가 false → 우선 콜을 건너뛰고 일반 매칭. 후보 목록(approvedManagers)은 setUp 그대로 A·B 다
    @Test
    void 탈퇴하거나_승인이_취소된_매니저에게는_우선_오퍼_대신_일반_매칭() {
        when(profiles.isApprovedManager(A)).thenReturn(false);
        service.dispatchPreferred(booking, A);
        ArgumentCaptor<Offer> saved = ArgumentCaptor.forClass(Offer.class);
        verify(offers, times(2)).save(saved.capture()); // 후보 A·B 전원에게 일반 오퍼
        assertThat(saved.getAllValues()).allMatch(o -> !o.isPreferred());
    }

    // A 가 그날 근무 불가 → 우선 콜을 보내지 않고 일반 매칭으로 넘어간다. A 는 일반 후보 조건에서도 빠져 B 만 받는다
    @Test
    void 우선_오퍼도_근무_불가나_겹치는_작업이_있으면_보내지_않고_바로_일반_매칭() { // 자체 점검 #21
        when(profiles.isApprovedManager(A)).thenReturn(true);
        when(availability.isAvailable(eq(A), any(), any(), any())).thenReturn(false);
        service.dispatchPreferred(booking, A);
        ArgumentCaptor<Offer> saved = ArgumentCaptor.forClass(Offer.class);
        verify(offers).save(saved.capture());
        assertThat(saved.getValue().getManagerId()).isEqualTo(B);
        assertThat(saved.getValue().isPreferred()).isFalse();
    }

    // onRescheduled 는 자기 회차라 정기청소 취소 이력(droppedSeries)을 보지 않는다 → A 에게 우선 콜, 결과 true.
    // B 로 부르면 승인 매니저가 아니라 우선 콜 없이 일반 매칭, 결과 false
    @Test
    void 일정_변경은_이_회차를_맡았던_매니저에게_정기청소_취소_이력과_무관하게_우선_오퍼하고_보냈는지_돌려준다() { // 자체 점검 #18
        when(profiles.isApprovedManager(A)).thenReturn(true);
        when(penalties.droppedSeries(eq(A), any())).thenReturn(true); // 다른 회차를 취소한 적이 있어도
        assertThat(service.onRescheduled(booking, A)).isTrue();
        ArgumentCaptor<Offer> saved = ArgumentCaptor.forClass(Offer.class);
        verify(offers).save(saved.capture());
        assertThat(saved.getValue().getManagerId()).isEqualTo(A);
        assertThat(saved.getValue().isPreferred()).isTrue();

        when(profiles.isApprovedManager(B)).thenReturn(false); // 정지·탈퇴면 우선 오퍼 없이 일반 매칭 → false
        assertThat(service.onRescheduled(booking, B)).isFalse();
    }

    // 일정 변경의 우선 콜은 콜 푸시(OffersDispatched)를 적지 않는다 — 원래 매니저에게는 BookingService 가 적는 '예약 일정이 변경됐어요 … 새 콜을 확인해주세요' 가 간다.
    // 정기청소 다음 회차의 우선 콜(dispatchPreferred)은 그대로 단골 고객 문구 푸시를 적는다
    @Test
    void 일정_변경의_우선_콜은_콜_푸시를_따로_보내지_않는다() { // 문제 목록 15번 (사용자 결정 A)
        when(profiles.isApprovedManager(A)).thenReturn(true);
        assertThat(service.onRescheduled(booking, A)).isTrue();
        verify(outbox, never()).publish(any(OffersDispatched.class));

        service.dispatchPreferred(booking, A);
        verify(outbox).publish(new OffersDispatched(booking.getId(), List.of(A), true));
    }

    // 1회차에 A 에게 보냈던 콜이 만료된 매칭 실패 예약 → 자동 재매칭은 B 에게만 2회차 콜을 보내고, 실패 표시를 지우고, 푸시 이벤트를 남긴다.
    // matchingFailedIds(기준 시각) = 약속이 그 시각보다 뒤인 매칭 실패 예약 id 목록. 서비스는 기준으로 '지금 + RETRY_MIN_LEAD' 를 넘긴다
    @Test
    void 자동_재매칭은_이_예약의_콜을_받지_않은_새_후보에게만_새_회차로_보낸다() { // 사용자 결정 2026-09-27 (MatchingRetry)
        booking.markMatchingFailed(NOW);
        Offer expiredToA = Offer.pending(booking.getId(), A, 1, 76500, NOW.minusSeconds(60), false, NOW.minusSeconds(3600));
        when(bookings.matchingFailedIds(NOW.plus(OfferService.RETRY_MIN_LEAD))).thenReturn(List.of(booking.getId()));
        when(bookings.lockForUpdate(booking.getId())).thenReturn(booking);
        when(offers.findByBookingId(booking.getId())).thenReturn(List.of(expiredToA));

        assertThat(service.retryFailedMatches()).isEqualTo(1);
        ArgumentCaptor<Offer> saved = ArgumentCaptor.forClass(Offer.class);
        verify(offers).save(saved.capture());
        assertThat(saved.getValue().getManagerId()).isEqualTo(B); // A 는 이미 받았다 — 되풀이하지 않는다
        assertThat(saved.getValue().getRound()).isEqualTo(2);
        assertThat(booking.getMatchingFailedAt()).isNull(); // 다시 실패하면 또 알린다
        verify(outbox).publish(new OffersDispatched(booking.getId(), List.of(B), false));
    }

    // 일정 변경으로 2회차부터 새 매칭 구간인 매칭 실패 예약: 1회차(옛 일정) 콜은 A, 2회차 콜은 B → 자동 재매칭은 A 에게만 보낸다
    @Test
    void 자동_재매칭은_지금_매칭_구간_전의_콜은_받은_적으로_세지_않는다() { // 옛 일정의 콜만 받은 매니저가 영영 빠지지 않게 (자체 점검 후속 검토)
        booking.reschedule(LocalDate.of(2026, 9, 18), LocalTime.of(10, 0), NOW, 24); // 회차 2 = 새 매칭 구간
        booking.markMatchingFailed(NOW);
        Offer oldScheduleToA = Offer.pending(booking.getId(), A, 1, 76500, NOW.minusSeconds(60), false, NOW.minusSeconds(7200)); // 옛 일정(1회차)의 콜
        Offer thisScheduleToB = Offer.pending(booking.getId(), B, 2, 76500, NOW.minusSeconds(60), false, NOW.minusSeconds(3600));
        when(bookings.matchingFailedIds(any())).thenReturn(List.of(booking.getId()));
        when(bookings.lockForUpdate(booking.getId())).thenReturn(booking);
        when(offers.findByBookingId(booking.getId())).thenReturn(List.of(oldScheduleToA, thisScheduleToB));

        assertThat(service.retryFailedMatches()).isEqualTo(1);
        ArgumentCaptor<Offer> saved = ArgumentCaptor.forClass(Offer.class);
        verify(offers).save(saved.capture());
        assertThat(saved.getValue().getManagerId()).isEqualTo(A); // B 는 이번 구간에 받았다
    }

    // A 배정 → A 방문 취소(2회차부터 새 구간). 1회차에 B 가 받은 콜은 A 수락 때 cancelled 로 닫혔다 →
    // redispatch 는 3회차로 B 에게 보낸다 (A 는 이 방문을 취소한 매니저라 빠진다)
    @Test
    void 거절_만료_뒤_재매칭도_지금_매칭_구간의_콜만_받은_적으로_센다() { // 방문 취소 전 다른 매니저 수락으로 닫힌 콜만 받은 B 가 새 구간에서 빠지지 않게 (자체 점검 후속 검토)
        booking.assign(A, NOW);
        booking.releaseByManager();
        booking.restartAfterRelease(); // 회차 2 = 새 매칭 구간
        Offer cancelledToB = Offer.pending(booking.getId(), B, 1, 76500, NOW.plusSeconds(600), false, NOW.minusSeconds(7200));
        cancelledToB.cancel(); // 1회차에서 A 가 먼저 수락해 닫혔다
        when(offers.findByBookingId(booking.getId())).thenReturn(List.of(cancelledToB));
        when(penalties.bookingDroppers(booking.getId())).thenReturn(java.util.Set.of(A)); // A 는 방문을 취소했다
        service.redispatch(booking);
        ArgumentCaptor<Offer> saved = ArgumentCaptor.forClass(Offer.class);
        verify(offers).save(saved.capture());
        assertThat(saved.getValue().getManagerId()).isEqualTo(B);
        assertThat(saved.getValue().getRound()).isEqualTo(3);
    }

    // A 는 이미 받았고, 남은 후보 B 는 이 방문을 취소한 적이 있다 → 저장·푸시 없음, 실패 표시와 회차(1)도 그대로
    @Test
    void 새_후보가_없거나_취소한_매니저뿐이면_아무것도_하지_않는다_고객에게_실패_알림을_반복하지_않게() {
        booking.markMatchingFailed(NOW);
        when(bookings.matchingFailedIds(any())).thenReturn(List.of(booking.getId()));
        when(bookings.lockForUpdate(booking.getId())).thenReturn(booking);
        when(offers.findByBookingId(booking.getId())).thenReturn(List.of(Offer.pending(booking.getId(), A, 1, 76500, NOW.minusSeconds(60), false, NOW.minusSeconds(3600))));
        when(penalties.bookingDroppers(booking.getId())).thenReturn(java.util.Set.of(B)); // B 는 이 방문을 취소했었다

        assertThat(service.retryFailedMatches()).isZero();
        verify(offers, never()).save(any());
        verify(outbox, never()).publish(any());
        assertThat(booking.getMatchingFailedAt()).isEqualTo(NOW);
        assertThat(booking.getDispatchRound()).isEqualTo(1);
    }

    // 오늘(9/15) 16:00 예약: 지금 14:00 + 3시간 = 17:00 보다 이르다. 가짜 조회가 id 를 돌려줘도 잠근 뒤 다시 보는 검사에서 걸러진다.
    // (이 테스트는 '3시간 안' 쪽만 확인한다)
    @Test
    void 잠그는_사이_배정됐거나_약속이_3시간_안이면_건너뛴다() {
        Booking soon = TestBookings.assigning(CUSTOMER, LocalDate.of(2026, 9, 15), LocalTime.of(16, 0));
        soon.markMatchingFailed(NOW);
        when(bookings.matchingFailedIds(any())).thenReturn(List.of(soon.getId()));
        when(bookings.lockForUpdate(soon.getId())).thenReturn(soon);
        assertThat(service.retryFailedMatches()).isZero();
        verify(offers, never()).save(any());
    }

    // 매칭 실패 예약에 A 의 대기 콜이 하나 남아 있다 → 운영자 재매칭: 옛 콜 cancelled, 실패 표시 지움, 2회차로 A·B 모두에게.
    // 이어서 예약이 배정되면(매칭 중이 아니면) INVALID_STATE
    @Test
    void 운영자_재매칭은_남은_오퍼를_닫고_표시를_지운_뒤_새_회차로_후보_전원에게_보낸다() { // 자체 점검 #5
        booking.markMatchingFailed(NOW);
        Offer old = Offer.pending(booking.getId(), A, 1, 76500, NOW.plusSeconds(60), false, NOW);
        when(bookings.lockForUpdate(booking.getId())).thenReturn(booking);
        when(offers.findByBookingIdAndStatus(booking.getId(), Offer.PENDING)).thenReturn(List.of(old));
        // 저장한 콜을 목록에 모으고 findByBookingId 는 그 목록을 돌려준다 — 진짜 DB 처럼 방금 저장한 콜이 조회돼 offered 를 셀 수 있다
        List<Offer> saved = new java.util.ArrayList<>();
        when(offers.save(any())).thenAnswer(inv -> { saved.add(inv.getArgument(0)); return inv.getArgument(0); });
        when(offers.findByBookingId(booking.getId())).thenAnswer(inv -> List.copyOf(saved));

        var res = service.redispatchByAdmin(booking.getId());

        assertThat(old.getStatus()).isEqualTo(Offer.CANCELLED);
        assertThat(booking.getMatchingFailedAt()).isNull();
        assertThat(booking.getDispatchRound()).isEqualTo(2);
        assertThat(saved).extracting(Offer::getManagerId).containsExactlyInAnyOrder(A, B);
        assertThat(res.offered()).isEqualTo(2);

        booking.assign(A, NOW);
        assertThatThrownBy(() -> service.redispatchByAdmin(booking.getId())).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.INVALID_STATE); // 매칭 중인 예약만
    }

    @Test
    void 우선_오퍼는_한_명에게_10분() {
        when(profiles.isApprovedManager(A)).thenReturn(true);
        service.dispatchPreferred(booking, A);
        ArgumentCaptor<Offer> saved = ArgumentCaptor.forClass(Offer.class);
        verify(offers).save(saved.capture());
        assertThat(saved.getValue().isPreferred()).isTrue();
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        verify(outbox).publish(new OffersDispatched(booking.getId(), List.of(A), true));
    }

    /**
     * managerId 에게 간 1회차 대기 콜(10분 뒤 만료)을 만들고, 수락·거절·상세가 부르는 저장소 조회와 예약 잠금이
     * 모두 이 콜과 setUp 의 예약을 돌려주게 정한다. 같은 테스트에서 다시 부르면 새 id 의 콜이 하나 더 준비된다(앞 콜의 조회도 남는다)
     */
    Offer pendingOffer(UUID managerId) {
        Offer o = Offer.pending(booking.getId(), managerId, 1, 76500, NOW.plusSeconds(600), false, NOW);
        when(offers.findBookingId(o.getId(), managerId)).thenReturn(Optional.of(booking.getId()));
        when(offers.findBookingIdById(o.getId())).thenReturn(Optional.of(booking.getId()));
        when(offers.findForUpdate(o.getId())).thenReturn(Optional.of(o));
        when(offers.findByIdAndManagerId(o.getId(), managerId)).thenReturn(Optional.of(o));
        when(bookings.lockForUpdate(booking.getId())).thenReturn(booking);
        return o;
    }

    // 내 대기 콜 → 상세가 나온다. B 가 A 의 콜을 보면 NOT_FOUND(B 로 찾는 호출은 정해 두지 않아 빈 값). 만료 시각이 지났거나 거절로 닫힌 콜은 OFFER_EXPIRED
    @Test
    void 콜_상세는_내_pending_오퍼만_보이고_닫혔거나_만료면_OFFER_EXPIRED_남의_것은_404() {
        Offer mine = pendingOffer(A);
        when(bookings.find(booking.getId())).thenReturn(Optional.of(booking));
        when(catalog.noticeLabels(eq("home"), any())).thenReturn(List.of("반려동물이 있어요"));
        assertThat(service.call(A, mine.getId()).id()).isEqualTo(mine.getId());
        assertThatThrownBy(() -> service.call(B, mine.getId())).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.NOT_FOUND);

        Offer expired = Offer.pending(booking.getId(), A, 1, 76500, NOW.minusSeconds(1), false, NOW.minusSeconds(1800));
        when(offers.findByIdAndManagerId(expired.getId(), A)).thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> service.call(A, expired.getId())).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.OFFER_EXPIRED);
        mine.reject("시간이 맞지 않아요", NOW);
        assertThatThrownBy(() -> service.call(A, mine.getId())).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.OFFER_EXPIRED);
    }

    @Test
    void 수락은_매니저_예약_오퍼_순서로_잠그고_배정_체크리스트_라이브_세션_이벤트를_만든다() {
        Offer mine = pendingOffer(A);
        Offer other = Offer.pending(booking.getId(), B, 1, 76500, NOW.plusSeconds(600), false, NOW);
        when(offers.findByBookingIdAndStatus(booking.getId(), Offer.PENDING)).thenReturn(List.of(other));

        AcceptResponse res = service.accept(A, mine.getId(), "key");

        // InOrder = 여러 가짜에 대한 호출 순서까지 확인한다: 매니저 → 예약 → 오퍼 순서로 잠갔는가
        InOrder order = inOrder(profiles, bookings, offers);
        order.verify(profiles).lockManager(A);
        order.verify(bookings).lockForUpdate(booking.getId());
        order.verify(offers).findForUpdate(mine.getId());
        assertThat(res.jobId()).isEqualTo(booking.getId());
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.SCHEDULED);
        assertThat(booking.getManagerId()).isEqualTo(A);
        assertThat(mine.getStatus()).isEqualTo(Offer.ACCEPTED);
        assertThat(other.getStatus()).isEqualTo(Offer.CANCELLED);
        verify(jobs).createChecklist(booking);
        verify(live).ensureCreated(booking.getId());
        verify(bookings).recordTransition(booking, BookingStatus.ASSIGNING, A, null);
        verify(outbox).publish(new BookingAssigned(booking.getId(), CUSTOMER, A));
        verify(offers).flush(); // 여기서 DB 제약 위반을 받는다 (아래 겹치는_작업이_있거나_DB_제약에_걸리면_OFFER_TAKEN 참고)
    }

    // 1초 전 만료된 콜 → OFFER_EXPIRED, 예약은 매칭 중 그대로. 고객이 예약을 취소한 뒤의 수락도 OFFER_EXPIRED 이고 체크리스트는 만들지 않는다
    @Test
    void 만료된_오퍼_수락은_OFFER_EXPIRED_예약이_이미_취소됐어도_OFFER_EXPIRED() {
        Offer stale = Offer.pending(booking.getId(), A, 1, 76500, NOW.minusSeconds(1), false, NOW);
        when(offers.findBookingId(stale.getId(), A)).thenReturn(Optional.of(booking.getId()));
        when(offers.findForUpdate(stale.getId())).thenReturn(Optional.of(stale));
        when(bookings.lockForUpdate(booking.getId())).thenReturn(booking);
        assertThatThrownBy(() -> service.accept(A, stale.getId(), null)).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.OFFER_EXPIRED);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.ASSIGNING);

        Offer mine = pendingOffer(A);
        booking.cancel("customer", NOW, 24);
        assertThatThrownBy(() -> service.accept(A, mine.getId(), null)).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.OFFER_EXPIRED);
        verify(jobs, never()).createChecklist(any());
    }

    // 콜을 받은 뒤 그 시간에 휴무·근무 불가를 넣었다(isAvailable = false) → 수락은 INVALID_STATE + 근무 일정 안내, 배정·알림 없음.
    // 검사는 매칭 후보 판정과 같은 인자(방문 날짜, 시작·끝 시각)로 불린다
    @Test
    void 콜을_받은_뒤_휴무나_근무_불가를_넣었으면_수락할_수_없다() { // 문제 목록 7번 (사용자 결정 ①)
        Offer mine = pendingOffer(A);
        when(availability.isAvailable(A, LocalDate.of(2026, 9, 17), LocalTime.of(10, 0), LocalTime.of(13, 0))).thenReturn(false);
        assertThatThrownBy(() -> service.accept(A, mine.getId(), null)).isInstanceOf(ApiException.class)
                .hasMessage("근무 일정과 맞지 않는 시간이라 수락할 수 없어요. 근무 일정 관리에서 확인해주세요").extracting("code").isEqualTo(ErrorCode.INVALID_STATE);
        assertThat(booking.getManagerId()).isNull();
        verify(outbox, never()).publish(any(BookingAssigned.class));
    }

    // A 가 이 예약의 방문을 취소한 기록(bookingDroppers)이 있으면 남은 대기 콜로도 수락 불가 → OFFER_TAKEN, 배정 없음
    @Test
    void 이_방문을_취소한_매니저는_남아_있던_옛_오퍼로도_다시_수락하지_못한다() { // 자체 점검 #6
        Offer mine = pendingOffer(A);
        when(penalties.bookingDroppers(booking.getId())).thenReturn(java.util.Set.of(A));
        assertThatThrownBy(() -> service.accept(A, mine.getId(), null)).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.OFFER_TAKEN);
        assertThat(booking.getManagerId()).isNull();
    }

    // 앞: 서비스의 겹침 검사(hasOverlappingWork)에 걸린다 → OFFER_TAKEN, 예약은 매칭 중 그대로.
    // 뒤: 그 검사는 지나지만 flush 에서 DB 제약 위반(DataIntegrityViolationException)이 난다 → 역시 OFFER_TAKEN
    @Test
    void 겹치는_작업이_있거나_DB_제약에_걸리면_OFFER_TAKEN() {
        Offer mine = pendingOffer(A);
        when(bookings.hasOverlappingWork(A, booking.getStartsAt(), booking.getEndsAt())).thenReturn(true);
        assertThatThrownBy(() -> service.accept(A, mine.getId(), null)).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.OFFER_TAKEN);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.ASSIGNING);

        when(bookings.hasOverlappingWork(A, booking.getStartsAt(), booking.getEndsAt())).thenReturn(false);
        Offer again = pendingOffer(A);
        // doThrow(…).when(가짜).메서드() = 반환값이 없는(void) 메서드가 예외를 던지게 정하는 모양
        doThrow(new DataIntegrityViolationException("bookings_manager_no_overlap")).when(offers).flush();
        assertThatThrownBy(() -> service.accept(A, again.getId(), null)).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.OFFER_TAKEN);
    }

    // 1회차에 A·B 가 받았고 A 의 거절로 대기 콜이 0건(개수 조회를 0 으로 정해 둠) → 2회차로 올라가 아직 받지 않은 C 에게만 보낸다
    @Test
    void 마지막_오퍼가_거절되면_다음_회차로_재매칭하되_이미_받은_매니저는_뺀다() {
        Offer mine = pendingOffer(A);
        when(offers.countByBookingIdAndRoundAndStatus(booking.getId(), 1, Offer.PENDING)).thenReturn(0L);
        when(offers.findByBookingId(booking.getId())).thenReturn(List.of(mine, Offer.pending(booking.getId(), B, 1, 76500, NOW, false, NOW)));
        when(profiles.approvedManagers()).thenReturn(List.of(manager(A, "서울특별시 강남구", "서울특별시"), manager(B, "서울특별시 강남구", "서울특별시"), manager(C, "서울특별시 강남구", "서울특별시")));

        RejectResponse res = service.reject(A, mine.getId(), "개인 사정");

        assertThat(res.reason()).isEqualTo("개인 사정");
        assertThat(mine.getStatus()).isEqualTo(Offer.REJECTED);
        assertThat(booking.getDispatchRound()).isEqualTo(2);
        ArgumentCaptor<Offer> saved = ArgumentCaptor.forClass(Offer.class);
        verify(offers).save(saved.capture());
        assertThat(saved.getValue().getManagerId()).isEqualTo(C);
        assertThat(saved.getValue().getRound()).isEqualTo(2);
    }

    // 우선 콜(preferred = true) 거절 → 회차는 1 그대로, A 를 빼고 B 에게 일반 콜
    @Test
    void 우선_오퍼가_거절되면_같은_회차에서_그_매니저만_빼고_일반_매칭() {
        Offer preferred = Offer.pending(booking.getId(), A, 1, 76500, NOW.plusSeconds(600), true, NOW);
        when(offers.findBookingId(preferred.getId(), A)).thenReturn(Optional.of(booking.getId()));
        when(offers.findForUpdate(preferred.getId())).thenReturn(Optional.of(preferred));
        when(bookings.lockForUpdate(booking.getId())).thenReturn(booking);
        when(offers.findByBookingId(booking.getId())).thenReturn(List.of(preferred));

        service.reject(A, preferred.getId(), "시간이 맞지 않아요");

        assertThat(booking.getDispatchRound()).isEqualTo(1);
        ArgumentCaptor<Offer> saved = ArgumentCaptor.forClass(Offer.class);
        verify(offers).save(saved.capture());
        assertThat(saved.getValue().getManagerId()).isEqualTo(B);
        assertThat(saved.getValue().isPreferred()).isFalse();
    }

    // 이미 3회차(= 설정한 최대)에서 마지막 콜이 거절되면 더 보내지 않는다 → 매칭 실패 표시 + 고객 알림 이벤트 한 건
    @Test
    void 재매칭은_max_dispatch_rounds_까지만_그_뒤는_운영자_로그() {
        booking.nextDispatchRound();
        booking.nextDispatchRound(); // 3회차
        Offer mine = Offer.pending(booking.getId(), A, 3, 76500, NOW.plusSeconds(600), false, NOW);
        when(offers.findBookingId(mine.getId(), A)).thenReturn(Optional.of(booking.getId()));
        when(offers.findForUpdate(mine.getId())).thenReturn(Optional.of(mine));
        when(bookings.lockForUpdate(booking.getId())).thenReturn(booking);
        when(offers.countByBookingIdAndRoundAndStatus(booking.getId(), 3, Offer.PENDING)).thenReturn(0L);

        service.reject(A, mine.getId(), "기타");

        assertThat(booking.getDispatchRound()).isEqualTo(3);
        verify(offers, never()).save(any());
        assertThat(booking.getMatchingFailedAt()).isEqualTo(NOW);
        verify(outbox).publish(new MatchingFailed(booking.getId(), CUSTOMER, true, true));
    }

    // 카탈로그에 없는 거절 사유는 VALIDATION (콜을 찾기 전에 거른다).
    // 이미 내가 거절한 콜을 다시 거절하면 오류 없이 저장된 사유를 돌려준다(응답이 늦어 다시 누른 경우). 재매칭은 다시 하지 않는다
    @Test
    void 모르는_거절_사유는_VALIDATION_이미_거절한_콜_거절은_저장된_사유로_그대로() {
        when(catalog.isReason("reject", "아무거나")).thenReturn(false);
        assertThatThrownBy(() -> service.reject(A, UUID.randomUUID(), "아무거나")).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.VALIDATION);

        Offer mine = pendingOffer(A);
        mine.reject("개인 사정", NOW);
        RejectResponse res = service.reject(A, mine.getId(), "시간이 맞지 않아요");
        assertThat(res.id()).isEqualTo(mine.getId());
        assertThat(res.reason()).isEqualTo("개인 사정");
        verify(offers, never()).save(any());
    }

    // 거절 시트를 연 사이 콜이 닫혔으면 앱이 '이미 마감된 요청이에요' 를 보이게 오류로 답한다 — 예전에는 200 이라 거절이 된 것처럼 보였다.
    // 다른 매니저 수락·고객 취소 = OFFER_TAKEN, 만료 = OFFER_EXPIRED, 내가 수락한 콜(수락 응답이 끊긴 경우) = INVALID_STATE. 재매칭은 하지 않는다
    @Test
    void 닫힌_콜의_거절은_마감_오류로_답하고_내가_수락한_콜은_INVALID_STATE() { // 문제 목록 10번 (사용자 결정 B)
        Offer taken = pendingOffer(A);
        taken.cancel(); // 다른 매니저가 먼저 수락했다
        assertThatThrownBy(() -> service.reject(A, taken.getId(), "개인 사정")).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.OFFER_TAKEN);

        Offer expired = pendingOffer(A);
        expired.expire(NOW);
        assertThatThrownBy(() -> service.reject(A, expired.getId(), "개인 사정")).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.OFFER_EXPIRED);

        Offer accepted = pendingOffer(A);
        accepted.accept(NOW);
        assertThatThrownBy(() -> service.reject(A, accepted.getId(), "개인 사정")).isInstanceOf(ApiException.class)
                .hasMessage("이미 수락한 콜이에요. 일정에서 확인해주세요").extracting("code").isEqualTo(ErrorCode.INVALID_STATE);
        verify(offers, never()).save(any());
    }

    // 만료 시각이 지난 A 의 콜 → expired 로 바꾸고, 그 회차 대기 콜이 0건이라 2회차 재매칭(이미 받은 A 는 빼고 B 에게)
    @Test
    void 만료_스케줄러는_예약을_잠근_뒤_오퍼를_expired_로_하고_재매칭을_시도한다() {
        Offer stale = Offer.pending(booking.getId(), A, 1, 76500, NOW.minusSeconds(1), false, NOW);
        when(offers.findExpiredPendingIds(NOW)).thenReturn(List.of(stale.getId()));
        when(offers.findBookingIdById(stale.getId())).thenReturn(Optional.of(booking.getId()));
        when(bookings.lockForUpdate(booking.getId())).thenReturn(booking);
        when(offers.findForUpdate(stale.getId())).thenReturn(Optional.of(stale));
        when(offers.countByBookingIdAndRoundAndStatus(booking.getId(), 1, Offer.PENDING)).thenReturn(0L);
        when(offers.findByBookingId(booking.getId())).thenReturn(List.of(stale));

        assertThat(service.expireDue()).isEqualTo(1);

        assertThat(stale.getStatus()).isEqualTo(Offer.EXPIRED);
        assertThat(booking.getDispatchRound()).isEqualTo(2);
        ArgumentCaptor<Offer> saved = ArgumentCaptor.forClass(Offer.class);
        verify(offers).save(saved.capture());
        assertThat(saved.getValue().getManagerId()).isEqualTo(B); // A 는 제외
    }

    // 4분 32초 뒤 만료되는 콜 한 건 → 카드 문구(만료·제목·위치·금액·이동 시간·특이사항)를 서버가 만든 글자 그대로 확인한다
    // (수령액 76,500원 = 85,000 − 수수료 8,500).
    // 이어서 같은 매니저가 미승인이면 수신 상태 카드가 '승인 대기 중' 으로 바뀐다
    @Test
    void 콜_목록은_카드_문구를_서버가_만든다() {
        Offer mine = Offer.pending(booking.getId(), A, 1, 76500, NOW.plus(Duration.ofMinutes(4)).plusSeconds(32), false, NOW);
        when(offers.findByManagerIdAndStatusOrderByExpiresAt(A, Offer.PENDING)).thenReturn(List.of(mine));
        when(bookings.findAll(anyCollection())).thenReturn(List.of(booking));
        when(catalog.noticeLabels(eq("home"), anyList())).thenReturn(List.of("반려동물이 있어요"));
        when(availability.status(eq(A), any())).thenReturn(new AvailabilityStatus("콜 수신 중", "오늘 09:00 - 18:00 근무 설정됨"));
        when(profiles.isApprovedManager(A)).thenReturn(true);

        CallsResponse res = service.calls(A);

        assertThat(res.tabBadge()).isEqualTo(1);
        assertThat(res.calls().get(0).expiry()).isEqualTo("4:32 후 만료");
        assertThat(res.calls().get(0).title()).isEqualTo("9월 17일 목요일 오전 10:00");
        assertThat(res.calls().get(0).location()).isEqualTo("서울특별시 강남구 테헤란로 · 가정/원룸 청소");
        assertThat(res.calls().get(0).pay()).isEqualTo("76,500원");
        assertThat(res.calls().get(0).eta()).isEqualTo("이동 시간은 배정 후 안내");
        assertThat(res.calls().get(0).note()).isEqualTo("특이사항: 반려동물이 있어요");
        assertThat(res.availability().status()).isEqualTo("콜 수신 중");
        assertThat(res.approved()).isTrue();

        // 미승인 매니저는 매칭 후보가 아니므로 '콜 수신 중' 이 아니라 '승인 대기 중' (검증 검토)
        when(profiles.isApprovedManager(A)).thenReturn(false);
        CallsResponse pendingRes = service.calls(A);
        assertThat(pendingRes.approved()).isFalse();
        assertThat(pendingRes.availability().status()).isEqualTo("승인 대기 중");
    }

    // A 가 이 정기청소를 취소한 적이 있다(droppedSeries = true) → dispatchPreferred 는 우선 콜 없이 일반 매칭만 한다
    @Test
    void 이_정기청소를_취소한_매니저에게는_어느_길로도_우선_오퍼를_보내지_않는다() { // 전체 재점검 B2 (일정 변경 경로가 이 검사를 건너뛰었다)
        when(penalties.droppedSeries(eq(A), any())).thenReturn(true);
        when(profiles.isApprovedManager(A)).thenReturn(true);
        service.dispatchPreferred(booking, A);
        ArgumentCaptor<Offer> sent = ArgumentCaptor.forClass(Offer.class);
        verify(offers, org.mockito.Mockito.atLeastOnce()).save(sent.capture()); // atLeastOnce = 한 번 이상 불렸는지만 본다
        assertThat(sent.getAllValues()).noneMatch(Offer::isPreferred); // 우선 제안은 없고
        assertThat(sent.getAllValues()).extracting(Offer::getManagerId).contains(A, B); // 일반 콜로는 A 도 받는다 (정책 §4 "일반 콜로는 계속 받을 수 있습니다", 자체 점검 #7)
    }

    // ── 정지·미승인·탈퇴 매니저의 대기 콜 (문제 목록 6번) ──

    /** closeBlockedOffers 가 이 콜(예약은 setUp 의 booking)을 찾아 잠그게 한다 — 콜을 가진 매니저는 그 콜의 매니저 한 명 */
    void blockedHolder(Offer o) {
        when(offers.findPendingManagerIds()).thenReturn(List.of(o.getManagerId()));
        when(offers.findPendingIdsByManagerIds(List.of(o.getManagerId()))).thenReturn(List.of(o.getId()));
        when(offers.findBookingIdById(o.getId())).thenReturn(Optional.of(booking.getId()));
        when(bookings.lockForUpdate(booking.getId())).thenReturn(booking);
        when(offers.findForUpdate(o.getId())).thenReturn(Optional.of(o));
    }

    // 1회차에 A·B 가 받았고 B 는 이미 거절했다. A 가 정지돼 후보 목록(approvedManagers)에서 빠졌다 →
    // 만료 스케줄러의 단계가 A 의 대기 콜을 닫고(cancelled), 그 회차 대기 콜이 0 이라 2회차로 아직 받지 않은 C 에게 보낸다
    @Test
    void 정지된_매니저의_대기_콜은_만료_스케줄러가_닫고_다음_후보에게_보낸다() { // 문제 목록 6번
        Offer toA = Offer.pending(booking.getId(), A, 1, 76500, NOW.plusSeconds(600), false, NOW);
        Offer toB = Offer.pending(booking.getId(), B, 1, 76500, NOW.plusSeconds(600), false, NOW);
        toB.reject("개인 사정", NOW);
        when(profiles.approvedManagers()).thenReturn(List.of(manager(B, "서울특별시 강남구", "서울특별시"), manager(C, "서울특별시 강남구", "서울특별시")));
        when(offers.findByBookingId(booking.getId())).thenReturn(List.of(toA, toB));
        blockedHolder(toA);

        assertThat(service.closeBlockedOffers()).isEqualTo(1);
        assertThat(toA.getStatus()).isEqualTo(Offer.CANCELLED);
        assertThat(booking.getDispatchRound()).isEqualTo(2);
        ArgumentCaptor<Offer> saved = ArgumentCaptor.forClass(Offer.class);
        verify(offers).save(saved.capture());
        assertThat(saved.getValue().getManagerId()).isEqualTo(C);
    }

    // 후보 목록에 없던 매니저가 잠그는 사이 다시 콜을 받을 수 있게 됐으면(정지 면제 등) 닫지 않는다. 콜을 가진 매니저가 모두 후보면 아무것도 안 한다.
    // 다시 볼 때는 DB 의 지금 값(isApprovedManagerNow)을 본다 — isApprovedManager 는 처음 목록을 읽을 때 올린 매니저 객체(1차 캐시)의 옛 정지 값을
    // 돌려줘 방금 풀린 매니저의 콜을 닫았다 (합친 38건 검토 R3). 여기서는 캐시가 옛 값(false)을, DB 가 새 값(true)을 준다고 정한다
    @Test
    void 잠그는_사이_다시_받을_수_있게_된_매니저의_콜은_닫지_않는다() {
        Offer toA = Offer.pending(booking.getId(), A, 1, 76500, NOW.plusSeconds(600), false, NOW);
        when(profiles.approvedManagers()).thenReturn(List.of(manager(B, "서울특별시 강남구", "서울특별시")));
        blockedHolder(toA);
        when(profiles.isApprovedManager(A)).thenReturn(false); // 먼저 올려 둔 객체의 옛 값
        when(profiles.isApprovedManagerNow(A)).thenReturn(true); // 면제로 정지가 풀려 커밋된 DB 값
        assertThat(service.closeBlockedOffers()).isZero();
        assertThat(toA.getStatus()).isEqualTo(Offer.PENDING);

        when(profiles.approvedManagers()).thenReturn(List.of(manager(A, "서울특별시 강남구", "서울특별시")));
        assertThat(service.closeBlockedOffers()).isZero();
        verify(offers, times(1)).findPendingIdsByManagerIds(any()); // 두 번째는 닫을 매니저가 없어 콜을 찾지도 않는다
    }

    // 밤에 정지 매니저의 콜을 닫았으면 이어지는 다음 회차 재매칭은 아침으로 미룬다 (2번 규칙)
    @Test
    void 밤에_닫은_정지_매니저_콜의_재매칭은_아침으로_미룬다() {
        Offer toA = Offer.pending(booking.getId(), A, 1, 76500, Instant.parse("2026-09-15T22:30:00+09:00"), false, NOW);
        when(profiles.approvedManagers()).thenReturn(List.of(manager(B, "서울특별시 강남구", "서울특별시")));
        when(offers.findByBookingId(booking.getId())).thenReturn(List.of(toA));
        blockedHolder(toA);
        assertThat(at("2026-09-15T22:00:00").closeBlockedOffers()).isEqualTo(1);
        assertThat(booking.isDispatchHeld()).isTrue();
        verify(offers, never()).save(any());
    }

    // 정지된 매니저(isApprovedManager = false)의 콜 탭: 정지 전에 받은 콜이 남아 있어도 수락할 수 없는 카드는 싣지 않고(배지 0), 정지 안내 카드를 보인다.
    // 홈 '새로운 청소 요청' 도 같은 pendingFor 를 써서 비어 있다
    @Test
    void 정지된_매니저의_콜_탭은_카드_없이_정지_안내를_보인다() { // 문제 목록 6번
        Offer mine = Offer.pending(booking.getId(), A, 1, 76500, NOW.plusSeconds(600), false, NOW);
        when(offers.findByManagerIdAndStatusOrderByExpiresAt(A, Offer.PENDING)).thenReturn(List.of(mine));
        when(bookings.findAll(anyCollection())).thenReturn(List.of(booking));
        when(profiles.isApprovedManager(A)).thenReturn(false);
        when(profiles.suspensionNotice(A)).thenReturn("10월 3일 까지 콜을 받을 수 없어요");
        when(catalog.noticeLabels(eq("home"), anyList())).thenReturn(List.of("반려동물이 있어요"));

        CallsResponse res = service.calls(A);
        assertThat(res.calls()).isEmpty();
        assertThat(res.tabBadge()).isZero();
        assertThat(res.availability().status()).isEqualTo("콜 배정 정지 중");
        assertThat(res.availability().detail()).isEqualTo("10월 3일 까지 콜을 받을 수 없어요");
        assertThat(service.pendingFor(A)).isEmpty();
    }

    // ── 밤에는 새 콜을 아침으로 미룬다 (문제 목록 2번, 사용자 결정 ③) ──

    /** 같은 가짜 협력자로 시계만 다른 서비스 — 서울 벽시계 글자("2026-09-15T22:00:00") 기준 */
    OfferService at(String seoulLocal) {
        Instant t = Instant.parse(seoulLocal + "+09:00");
        return new OfferService(offers, bookings, profiles, availability, jobs, live, catalog, settings, outbox, idempotent, new AppClock(Clock.fixed(t, AppClock.ZONE)), penalties);
    }

    /** 아침 보내기가 이 예약을 찾아 잠그게 한다 */
    void heldForMorning(Booking b) {
        when(bookings.dispatchHeldIds()).thenReturn(List.of(b.getId()));
        when(bookings.lockForUpdate(b.getId())).thenReturn(b);
    }

    // 화 22:00 에 모레(9/17 목) 10:00 예약의 매칭: 콜·콜 푸시 없이 보류 표시만 남는다(고객 카드는 배정 전 문구 그대로 — 실패 표시 없음).
    // 아침 08:05 보내기(releaseHeldDispatches)가 후보 전원에게 보내고 보류를 지운다
    @Test
    void 밤에는_새_콜을_보내지_않고_아침_첫_실행에_보낸다() {
        at("2026-09-15T22:00:00").dispatch(booking, Set.of());
        verify(offers, never()).save(any());
        verify(outbox, never()).publish(any());
        assertThat(booking.isDispatchHeld()).isTrue();
        assertThat(booking.getMatchingFailedAt()).isNull();

        heldForMorning(booking);
        assertThat(at("2026-09-16T08:05:00").releaseHeldDispatches()).isEqualTo(1);
        verify(outbox).publish(new OffersDispatched(booking.getId(), List.of(A, B), false));
        assertThat(booking.isDispatchHeld()).isFalse();
    }

    // 화 22:00 에 내일(9/16) 10:00 예약: 미루면 08:05 + 3시간 = 11:05 라 약속이 지난다 → 급한 예약이라 지금 보낸다
    @Test
    void 밤이어도_미루면_약속까지_3시간이_안_남는_예약은_지금_보낸다() {
        Booking soon = TestBookings.assigning(CUSTOMER, LocalDate.of(2026, 9, 16), LocalTime.of(10, 0));
        at("2026-09-15T22:00:00").dispatch(soon, Set.of());
        verify(offers, times(2)).save(any());
        assertThat(soon.isDispatchHeld()).isFalse();
    }

    // 밤에 정기청소 다음 회차의 우선 콜이 생겼다 → 아침에도 그 매니저(A)에게 먼저 우선 콜(단골 고객 문구 푸시)로 보낸다
    @Test
    void 밤의_정기청소_우선_콜은_아침에도_그_매니저에게_우선_콜로_보낸다() {
        when(profiles.isApprovedManager(A)).thenReturn(true);
        at("2026-09-15T23:30:00").dispatchPreferred(booking, A);
        verify(offers, never()).save(any());
        assertThat(booking.getDispatchHeldManagerId()).isEqualTo(A);

        heldForMorning(booking);
        at("2026-09-16T08:05:00").releaseHeldDispatches();
        ArgumentCaptor<Offer> saved = ArgumentCaptor.forClass(Offer.class);
        verify(offers).save(saved.capture());
        assertThat(saved.getValue().getManagerId()).isEqualTo(A);
        assertThat(saved.getValue().isPreferred()).isTrue();
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(Instant.parse("2026-09-16T08:15:00+09:00")); // 10분은 아침부터 센다
        verify(outbox).publish(new OffersDispatched(booking.getId(), List.of(A), true));
    }

    // 밤에 고객이 일정을 바꿨다 → 지금은 콜도, '예약 일정이 변경됐어요' 안내도 없다(onRescheduled 가 false, BookingService 는 보류면 보내지 않는다).
    // 아침에 원래 매니저 A 에게 우선 콜을 보내고, 그때 안내(BookingRescheduled, 우선 콜 보냄 = true)를 적는다 — 콜 푸시는 따로 없다(15번)
    @Test
    void 밤의_일정_변경은_원래_매니저의_우선_콜과_안내를_아침에_보낸다() {
        when(profiles.isApprovedManager(A)).thenReturn(true);
        assertThat(at("2026-09-15T22:00:00").onRescheduled(booking, A)).isFalse();
        assertThat(booking.isDispatchHeld()).isTrue();
        assertThat(booking.isDispatchHeldRescheduled()).isTrue();
        verify(offers, never()).save(any());

        heldForMorning(booking);
        at("2026-09-16T08:05:00").releaseHeldDispatches();
        ArgumentCaptor<Offer> saved = ArgumentCaptor.forClass(Offer.class);
        verify(offers).save(saved.capture());
        assertThat(saved.getValue().getManagerId()).isEqualTo(A);
        assertThat(saved.getValue().isPreferred()).isTrue();
        verify(outbox).publish(new kr.ansimclean.api.common.events.BookingRescheduled(booking.getId(), CUSTOMER, A, true));
        verify(outbox, never()).publish(any(OffersDispatched.class));
    }

    // 밤사이 일정을 두 번 바꿨다(두 번째는 이미 매칭 중이라 원래 매니저가 null 로 온다) → 원래 매니저 A 는 보류에 남아 아침에 우선 콜과 안내를 받는다.
    // 원래 매니저가 새 시간에 안 되면(근무 불가) 후보 전원에게 일반 콜, A 에게는 '다른 매니저에게 다시 배정돼요'(우선 콜 보냄 = false)
    @Test
    void 밤사이_두_번_바꾼_일정도_원래_매니저에게_아침에_한_번_알린다() {
        when(profiles.isApprovedManager(A)).thenReturn(true);
        OfferService night = at("2026-09-15T22:00:00");
        night.onRescheduled(booking, A);
        night.onRescheduled(booking, null);
        assertThat(booking.getDispatchHeldManagerId()).isEqualTo(A);

        when(availability.isAvailable(eq(A), any(), any(), any())).thenReturn(false);
        heldForMorning(booking);
        at("2026-09-16T08:05:00").releaseHeldDispatches();
        ArgumentCaptor<Offer> saved = ArgumentCaptor.forClass(Offer.class);
        verify(offers).save(saved.capture());
        assertThat(saved.getValue().getManagerId()).isEqualTo(B);
        verify(outbox).publish(new kr.ansimclean.api.common.events.BookingRescheduled(booking.getId(), CUSTOMER, A, false));
    }

    // 밤에 미룬 뒤 새 일정이 급해졌다(내일 10시로 당김) → 미뤄 둔 매칭을 아침 보내기와 같게 지금 보낸다(원래 매니저 A 에게 우선 콜 + 안내)
    @Test
    void 밤에_미룬_매칭이_일정_변경으로_급해지면_지금_보낸다() {
        when(profiles.isApprovedManager(A)).thenReturn(true);
        OfferService night = at("2026-09-15T22:00:00");
        night.onRescheduled(booking, A);
        booking.reschedule(LocalDate.of(2026, 9, 16), LocalTime.of(10, 0), Instant.parse("2026-09-15T22:10:00+09:00"), 0);
        assertThat(night.onRescheduled(booking, null)).isFalse();
        assertThat(booking.isDispatchHeld()).isFalse();
        ArgumentCaptor<Offer> saved = ArgumentCaptor.forClass(Offer.class);
        verify(offers).save(saved.capture());
        assertThat(saved.getValue().getManagerId()).isEqualTo(A);
        verify(outbox).publish(new kr.ansimclean.api.common.events.BookingRescheduled(booking.getId(), CUSTOMER, A, true));
    }

    // 밤에 A 의 콜이 만료돼 회차가 닫혔다 → 다음 회차(2) 재매칭도 아침으로. 아침에는 지금 매칭 구간에서 이미 받은 A 를 빼고 B 에게 2회차로
    @Test
    void 밤에_만료로_닫힌_회차의_재매칭도_아침으로_미루고_아침에는_이미_받은_매니저를_뺀다() {
        Offer stale = Offer.pending(booking.getId(), A, 1, 76500, Instant.parse("2026-09-15T22:59:00+09:00"), false, NOW);
        Instant late = Instant.parse("2026-09-15T23:00:00+09:00");
        when(offers.findExpiredPendingIds(late)).thenReturn(List.of(stale.getId()));
        when(offers.findBookingIdById(stale.getId())).thenReturn(Optional.of(booking.getId()));
        when(bookings.lockForUpdate(booking.getId())).thenReturn(booking);
        when(offers.findForUpdate(stale.getId())).thenReturn(Optional.of(stale));
        when(offers.findByBookingId(booking.getId())).thenReturn(List.of(stale));

        assertThat(at("2026-09-15T23:00:00").expireDue()).isEqualTo(1);
        assertThat(booking.getDispatchRound()).isEqualTo(2);
        assertThat(booking.isDispatchHeld()).isTrue();
        verify(offers, never()).save(any());

        heldForMorning(booking);
        at("2026-09-16T08:05:00").releaseHeldDispatches();
        ArgumentCaptor<Offer> saved = ArgumentCaptor.forClass(Offer.class);
        verify(offers).save(saved.capture());
        assertThat(saved.getValue().getManagerId()).isEqualTo(B);
        assertThat(saved.getValue().getRound()).isEqualTo(2);
    }

    // 운영자 재매칭은 밤에도 미루지 않고 바로 보내며 보류를 지운다. 고객이 취소한 예약은 보류를 지워 아침에 보내지 않는다
    @Test
    void 운영자_재매칭은_밤에도_바로_보내고_취소된_예약의_보류는_지운다() {
        OfferService night = at("2026-09-15T22:00:00");
        night.dispatch(booking, Set.of());
        when(bookings.lockForUpdate(booking.getId())).thenReturn(booking);
        night.redispatchByAdmin(booking.getId());
        verify(offers, times(2)).save(any());
        assertThat(booking.isDispatchHeld()).isFalse();

        Booking other = TestBookings.assigning(CUSTOMER, LocalDate.of(2026, 9, 18), LocalTime.of(10, 0));
        night.dispatch(other, Set.of());
        other.cancel("customer", Instant.parse("2026-09-15T22:10:00+09:00"), 24);
        night.onCancelled(other);
        assertThat(other.isDispatchHeld()).isFalse();
    }

    // 밤에 A 가 맡던 예약의 일정이 바뀌어(보류: A, 일정 변경) 운영자가 밤에 다시 매칭했다 → A·B 모두 일반 콜을 받지만,
    // A 에게는 '새 콜이 도착했어요'(OffersDispatched) 없이 '예약 일정이 변경됐어요 … 새 콜을 확인해주세요'(BookingRescheduled, 콜 받음 = true) 하나만 간다.
    // 예전에는 A 가 두 푸시를 함께 받았다 — 낮의 일정 변경·아침 보내기와 같게 (합친 38건 검토 R2·R17, 문제 목록 15번 결정 A)
    @Test
    void 운영자_재매칭도_밤에_미룬_일정_변경_전_매니저에게는_일정_변경_푸시_하나만_보낸다() {
        when(profiles.isApprovedManager(A)).thenReturn(true);
        OfferService night = at("2026-09-15T22:00:00");
        night.onRescheduled(booking, A);
        when(bookings.lockForUpdate(booking.getId())).thenReturn(booking);
        List<Offer> saved = new java.util.ArrayList<>();
        when(offers.save(any())).thenAnswer(inv -> { saved.add(inv.getArgument(0)); return inv.getArgument(0); });
        when(offers.findByBookingId(booking.getId())).thenAnswer(inv -> List.copyOf(saved));

        night.redispatchByAdmin(booking.getId());

        assertThat(saved).extracting(Offer::getManagerId).containsExactlyInAnyOrder(A, B);
        verify(outbox).publish(new OffersDispatched(booking.getId(), List.of(B), false)); // A 는 콜 푸시에서 빠진다
        verify(outbox).publish(new kr.ansimclean.api.common.events.BookingRescheduled(booking.getId(), CUSTOMER, A, true));
        verify(outbox, times(1)).publish(any(OffersDispatched.class));
    }

    // A 가 이 예약의 방문을 취소한 기록이 있으면 제외 목록이 비어 있어도 일반 매칭에서 빠진다 → B 에게만
    @Test
    void 이_방문을_취소한_매니저는_회차가_바뀌어도_같은_예약의_콜을_다시_받지_않는다() { // 자체 점검 #6
        when(penalties.bookingDroppers(booking.getId())).thenReturn(java.util.Set.of(A));
        service.dispatch(booking, java.util.Set.of());
        ArgumentCaptor<Offer> sent = ArgumentCaptor.forClass(Offer.class);
        verify(offers, org.mockito.Mockito.atLeastOnce()).save(sent.capture());
        assertThat(sent.getAllValues()).extracting(Offer::getManagerId).containsExactly(B);
    }
}
