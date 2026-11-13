package kr.ansimclean.api.offer;

import kr.ansimclean.api.booking.Booking;
import kr.ansimclean.api.booking.BookingService;
import kr.ansimclean.api.booking.BookingStatus;
import kr.ansimclean.api.booking.TestBookings;
import kr.ansimclean.api.common.activity.ActivityLogService;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.events.AdminAssigned;
import kr.ansimclean.api.common.events.BookingAssigned;
import kr.ansimclean.api.common.events.CameraReleased;
import kr.ansimclean.api.common.events.CancelRequestDecided;
import kr.ansimclean.api.common.events.ManagerReleased;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.security.AccessPolicy;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.Roles;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.job.JobCancelRequest;
import kr.ansimclean.api.job.JobService;
import kr.ansimclean.api.live.LiveSessionService;
import kr.ansimclean.api.profile.ManagerPenalty;
import kr.ansimclean.api.profile.ManagerPenaltyService;
import kr.ansimclean.api.profile.ManagerProfile;
import kr.ansimclean.api.profile.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 어드민의 방문 취소 승인·거절과 직접 배정 (docs/domain.md '매니저 방문 취소 승인·재배정'). 협력자는 mock, 예약·요청·콜 엔티티는 진짜.
 * 재배정 콜을 실제로 보내는 규칙(startReassign·awaitAdmin)은 OfferServiceTest, DB 잠금·제약을 거치는 흐름은 ReassignIT 가 본다.
 */
class ReassignServiceTest {
    static final Instant NOW = Instant.parse("2026-09-15T05:00:00Z"); // 화 14:00 서울
    static final UUID CUSTOMER = UUID.randomUUID();
    static final UUID A = UUID.randomUUID(); // 방문을 취소한 매니저
    static final UUID B = UUID.randomUUID(); // 다른 매니저

    OfferService offers = mock(OfferService.class);
    OfferRepository offerRows = mock(OfferRepository.class);
    BookingService bookings = mock(BookingService.class);
    JobService jobs = mock(JobService.class);
    ProfileService profiles = mock(ProfileService.class);
    ManagerPenaltyService penalties = mock(ManagerPenaltyService.class);
    LiveSessionService live = mock(LiveSessionService.class);
    OutboxService outbox = mock(OutboxService.class);
    ActivityLogService activity = mock(ActivityLogService.class);
    ReassignService service = new ReassignService(offers, offerRows, bookings, jobs, profiles, penalties, live, outbox, activity, new AccessPolicy(),
            new AppClock(Clock.fixed(NOW, AppClock.ZONE)));
    CurrentUser admin = new CurrentUser(UUID.randomUUID(), Roles.ADMIN, true, null, true);

    Booking booking; // 9/17 10:00, A 에게 배정됨
    JobCancelRequest request; // A 의 대기 중 취소 요청

    @BeforeEach
    void setUp() {
        booking = TestBookings.scheduled(CUSTOMER, A, LocalDate.of(2026, 9, 17), LocalTime.of(10, 0));
        request = JobCancelRequest.pending(booking.getId(), A, "sick", "몸이 아파요", NOW.minusSeconds(600));
        when(jobs.cancelRequestBookingId(request.getId())).thenReturn(booking.getId());
        when(bookings.lockForUpdate(booking.getId())).thenReturn(booking);
        when(jobs.lockCancelRequest(request.getId())).thenReturn(request);
        when(penalties.reasons()).thenReturn(JsonMapper.builder().build().readTree("[{\"key\":\"sick\",\"label\":\"질병·부상\"}]"));
    }

    @Test
    void 승인하면_배정을_풀고_0점_기록을_남긴_뒤_재배정을_시작하고_고객_매니저에게_알린다() {
        when(offers.startReassign(booking, A, request.getId())).thenAnswer(inv -> {
            booking.restartAfterRelease();
            booking.startReassign(request.getId(), NOW.plusSeconds(86400));
            return true;
        });

        var res = service.approve(admin, request.getId());

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.ASSIGNING);
        assertThat(booking.getManagerId()).isNull();
        assertThat(request.getStatus()).isEqualTo(JobCancelRequest.APPROVED);
        assertThat(request.getDecidedBy()).isEqualTo(admin.id());
        verify(bookings).recordTransition(booking, BookingStatus.SCHEDULED, admin.id(), ReassignService.HISTORY_CANCEL_APPROVED);
        verify(jobs).clearChecklist(booking.getId());
        verify(penalties).record(A, booking.getId(), booking.getSeriesId(), ManagerPenalty.CANCEL_APPROVED, "sick", "몸이 아파요"); // 점수 없음(0점 행)
        verify(offers).startReassign(booking, A, request.getId());
        verify(activity).record(eq(admin), eq(ActivityLogService.CANCEL_REQUEST_APPROVE), eq("booking"), eq(booking.getId().toString()), any(), any(), eq("sick"));
        verify(outbox).publish(new ManagerReleased(booking.getId(), CUSTOMER));
        verify(outbox).publish(new CancelRequestDecided(request.getId(), booking.getId(), A, true));
        verify(outbox).publish(new CameraReleased(booking.getId(), A));
        assertThat(res.status()).isEqualTo("approved");
        assertThat(res.next()).isEqualTo("calling");
        assertThat(res.reassignUntil()).isEqualTo(NOW.plusSeconds(86400).toString());
    }

    // 두 어드민이 거의 동시에 승인 — 뒤 사람은 잠금을 기다린 뒤 이미 approved 인 요청을 본다 → 같은 응답, 두 번째 재배정·알림 없음
    @Test
    void 이미_승인한_요청을_다시_승인하면_같은_응답이고_아무것도_다시_하지_않는다() {
        booking.releaseByManager();
        booking.restartAfterRelease();
        booking.awaitAdminAssign(request.getId(), NOW, OfferService.AWAIT_IMMINENT);
        request.approve(UUID.randomUUID(), NOW);

        var res = service.approve(admin, request.getId());

        assertThat(res.status()).isEqualTo("approved");
        assertThat(res.next()).isEqualTo("awaitingAdmin");
        verify(offers, never()).startReassign(any(), any(), any());
        verify(penalties, never()).record(any(), any(), any(), any(), any(), any());
        verify(outbox, never()).publish(any());
    }

    @Test
    void 거절된_요청이나_상태가_바뀐_예약은_승인할_수_없다() {
        request.reject(admin.id(), null, NOW);
        assertThatThrownBy(() -> service.approve(admin, request.getId())).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.INVALID_STATE);

        // 요청은 대기 중인데 예약이 이미 다른 매니저에게 갔다(예: 일정 변경 뒤 재배정) — 승인하면 남의 배정을 풀게 된다
        JobCancelRequest stale = JobCancelRequest.pending(booking.getId(), B, "sick", null, NOW.minusSeconds(60));
        when(jobs.cancelRequestBookingId(stale.getId())).thenReturn(booking.getId());
        when(jobs.lockCancelRequest(stale.getId())).thenReturn(stale);
        assertThatThrownBy(() -> service.approve(admin, stale.getId())).isInstanceOf(ApiException.class).hasMessage(ReassignService.BOOKING_CHANGED);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.SCHEDULED);
        verify(offers, never()).startReassign(any(), any(), any());
    }

    @Test
    void 거절하면_예약은_그대로이고_매니저에게_알린다_대기_중이_아니면_INVALID_STATE() {
        var res = service.reject(admin, request.getId(), "  통화했어요  ");

        assertThat(res.status()).isEqualTo("rejected");
        assertThat(request.getDecisionNote()).isEqualTo("통화했어요");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.SCHEDULED);
        assertThat(booking.getManagerId()).isEqualTo(A);
        verify(outbox).publish(new CancelRequestDecided(request.getId(), booking.getId(), A, false));
        verify(activity).record(eq(admin), eq(ActivityLogService.CANCEL_REQUEST_REJECT), eq("booking"), anyString(), any(), any(), eq("통화했어요"));

        assertThatThrownBy(() -> service.reject(admin, request.getId(), null)).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.INVALID_STATE);
        assertThatThrownBy(() -> service.reject(admin, request.getId(), "가".repeat(501))).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.VALIDATION);
    }

    @Test
    void 어드민_MFA_가_아니면_서비스에서도_막는다() {
        CurrentUser noMfa = new CurrentUser(admin.id(), Roles.ADMIN, true, null, false);
        CurrentUser manager = new CurrentUser(A, Roles.MANAGER, true, "active", true);
        assertThatThrownBy(() -> service.approve(noMfa, request.getId())).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.FORBIDDEN);
        assertThatThrownBy(() -> service.assign(manager, booking.getId(), A)).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.FORBIDDEN);
        assertThatThrownBy(() -> service.reassignments(manager)).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(request.isPending()).isTrue();
    }

    // ── 직접 배정 ──

    /** 관리자 배정 대기 예약(A 가 취소, 재배정 콜 무응답)과 강남구 매니저 B */
    private Booking waiting() {
        Booking b = TestBookings.assigning(CUSTOMER, LocalDate.of(2026, 9, 17), LocalTime.of(10, 0));
        b.awaitAdminAssign(request.getId(), NOW, OfferService.AWAIT_NO_ACCEPT);
        when(bookings.lockForUpdate(b.getId())).thenReturn(b);
        when(bookings.find(b.getId())).thenReturn(Optional.of(b));
        when(profiles.exists(B)).thenReturn(true);
        when(profiles.lockManager(B)).thenReturn(OfferServiceTest.manager(B, "서울특별시 강남구", "서울특별시"));
        when(profiles.isApprovedManagerNow(B)).thenReturn(true);
        when(penalties.bookingDroppers(b.getId())).thenReturn(Set.of(A));
        when(offerRows.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return b;
    }

    @Test
    void 직접_배정하면_수락된_콜을_새_회차로_만들고_예약을_그_매니저에게_배정한다() {
        Booking b = waiting();
        int round = b.getDispatchRound();

        var res = service.assign(admin, b.getId(), B);

        assertThat(res.managerId()).isEqualTo(B);
        assertThat(b.getStatus()).isEqualTo(BookingStatus.SCHEDULED);
        assertThat(b.getManagerId()).isEqualTo(B);
        assertThat(b.getAdminAssignPendingAt()).isNull(); // 대기 목록에서 빠진다
        ArgumentCaptor<Offer> saved = ArgumentCaptor.forClass(Offer.class);
        verify(offerRows).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(Offer.ACCEPTED);
        assertThat(saved.getValue().getRound()).isEqualTo(round + 1);
        assertThat(saved.getValue().getManagerId()).isEqualTo(B);
        verify(jobs).createChecklist(b);
        verify(live).ensureCreated(b.getId());
        verify(bookings).recordTransition(b, BookingStatus.ASSIGNING, admin.id(), ReassignService.HISTORY_ADMIN_ASSIGNED);
        verify(activity).record(eq(admin), eq(ActivityLogService.BOOKING_ASSIGN), eq("booking"), eq(b.getId().toString()), any(), any(), any());
        verify(outbox).publish(new BookingAssigned(b.getId(), CUSTOMER, B));
        verify(outbox).publish(new AdminAssigned(b.getId(), B));
    }

    @Test
    void 취소한_매니저_지역_밖_겹치는_작업_정지된_매니저_배정이_끝난_예약은_직접_배정할_수_없다() {
        Booking b = waiting();
        // 이 방문을 취소한 매니저
        when(profiles.exists(A)).thenReturn(true);
        when(profiles.lockManager(A)).thenReturn(OfferServiceTest.manager(A, "서울특별시 강남구", "서울특별시"));
        when(profiles.isApprovedManagerNow(A)).thenReturn(true);
        assertThatThrownBy(() -> service.assign(admin, b.getId(), A)).hasMessage(ReassignService.MANAGER_DROPPED);
        // 활동 지역 밖
        when(profiles.lockManager(B)).thenReturn(OfferServiceTest.manager(B, "부산광역시 해운대구", "부산광역시"));
        assertThatThrownBy(() -> service.assign(admin, b.getId(), B)).hasMessage(ReassignService.MANAGER_OUT_OF_AREA);
        // 그 시간에 다른 작업
        when(profiles.lockManager(B)).thenReturn(OfferServiceTest.manager(B, "서울특별시 강남구", "서울특별시"));
        when(bookings.hasOverlappingWork(B, b.getStartsAt(), b.getEndsAt())).thenReturn(true);
        assertThatThrownBy(() -> service.assign(admin, b.getId(), B)).hasMessage(ReassignService.MANAGER_BUSY);
        // 정지·미승인
        when(profiles.isApprovedManagerNow(B)).thenReturn(false);
        assertThatThrownBy(() -> service.assign(admin, b.getId(), B)).hasMessage(ReassignService.MANAGER_UNAVAILABLE);
        // 이미 배정된 예약
        Booking done = TestBookings.scheduled(CUSTOMER, A, LocalDate.of(2026, 9, 17), LocalTime.of(10, 0));
        when(bookings.lockForUpdate(done.getId())).thenReturn(done);
        assertThatThrownBy(() -> service.assign(admin, done.getId(), B)).hasMessage(ReassignService.NOT_ASSIGNING);
        verify(offerRows, never()).save(any());
        assertThat(b.getStatus()).isEqualTo(BookingStatus.ASSIGNING);
    }

    @Test
    void 배정할_수_있는_매니저는_취소한_매니저와_지역_밖_겹치는_작업을_빼고_근무시간_안인_사람이_먼저() {
        Booking b = waiting();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();
        UUID e = UUID.randomUUID();
        when(profiles.approvedManagers()).thenReturn(List.of(
                OfferServiceTest.manager(A, "서울특별시 강남구", "서울특별시"),   // 취소한 매니저
                OfferServiceTest.manager(c, "서울특별시 강남구", "서울특별시"),   // 근무시간 밖
                OfferServiceTest.manager(B, "서울특별시 강남구", "서울특별시"),   // 근무시간 안
                OfferServiceTest.manager(d, "부산광역시 해운대구", "부산광역시"), // 지역 밖
                OfferServiceTest.manager(e, "서울특별시 강남구", "서울특별시"))); // 겹치는 작업
        when(bookings.hasOverlappingWork(e, b.getStartsAt(), b.getEndsAt())).thenReturn(true);
        when(offers.worksThen(B, b)).thenReturn(true);
        when(offers.worksThen(c, b)).thenReturn(false);
        when(profiles.namesOf(any())).thenReturn(Map.of(B, "박매니저", c, "최매니저"));
        when(profiles.phonesOf(any())).thenReturn(Map.of(B, "+821022223333"));

        var list = service.assignableManagers(admin, b.getId());

        assertThat(list).extracting("id").containsExactly(B, c);
        assertThat(list.get(0).available()).isTrue();
        assertThat(list.get(0).phone()).isEqualTo("010-2222-3333");
        assertThat(list.get(1).available()).isFalse();
        assertThat(list.get(1).phone()).isNull();
    }

    @Test
    void 관리자_배정_대기_목록에는_고객_방문_일시_주소_취소한_매니저_요청_승인_시각이_있다() {
        Booking b = waiting();
        request.approve(admin.id(), NOW.minusSeconds(60));
        when(bookings.adminAssignPending()).thenReturn(List.of(b));
        when(jobs.latestCancelRequest(b.getId())).thenReturn(Optional.of(request));
        when(profiles.namesOf(any())).thenReturn(Map.of(CUSTOMER, "이고객", A, "김매니저"));
        when(profiles.contactPhone(CUSTOMER)).thenReturn("+821012345678");

        var item = service.reassignments(admin).get(0);

        assertThat(item.bookingId()).isEqualTo(b.getId());
        assertThat(item.reason()).isEqualTo(OfferService.AWAIT_NO_ACCEPT);
        assertThat(item.customer().name()).isEqualTo("이고객");
        assertThat(item.customer().phone()).isEqualTo("010-1234-5678");
        assertThat(item.visitAt()).isEqualTo(b.getStartsAt().toString());
        assertThat(item.address()).containsExactly(b.getRoadAddress(), b.getDetailAddress());
        assertThat(item.cancelledManager().id()).isEqualTo(A);
        assertThat(item.cancelledManager().name()).isEqualTo("김매니저");
        assertThat(item.requestedAt()).isEqualTo(NOW.minusSeconds(600).toString());
        assertThat(item.approvedAt()).isEqualTo(NOW.minusSeconds(60).toString());
        assertThat(item.cancelReason()).isEqualTo("질병·부상");
    }
}
