package kr.ansimclean.api.live;

import kr.ansimclean.api.booking.Booking;
import kr.ansimclean.api.booking.BookingService;
import kr.ansimclean.api.booking.TestBookings;
import kr.ansimclean.api.common.events.CameraOffline;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.live.LiveKitGateway.EgressInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static kr.ansimclean.api.live.LiveTestSupport.CUSTOMER;
import static kr.ansimclean.api.live.LiveTestSupport.MANAGER;
import static kr.ansimclean.api.live.LiveTestSupport.NOW;
import static kr.ansimclean.api.live.LiveTestSupport.camera;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 웹훅 없이 LiveKit 조회만으로 상태를 맞춘다 (BACKEND_PLAN.md §2.5 LiveReconciler) */
// LiveReconciler.reconcile 단위 테스트. 스케줄을 기다리지 않고 reconcile() 을 직접 부른다 (첫 테스트만 스케줄러 입구 run()).
// 확인하는 것: 세션마다 트랜잭션 하나, 남이 잠근 세션 건너뛰기, 카메라 붙음/끊김 보정과 끊김·5분 대기 푸시,
// 세션 상태·작업 상태·실제 트랙 mute 가 어긋났을 때의 mute/unmute 재시도, 끝난 세션의 녹화 마무리·방 삭제·바디캠 잠금 정리.
// LiveService·LiveWebhookService 는 목이라 그쪽 동작(cameraAttached·setMuted 등)은 '불렸는지' 와 목이 돌려준 값으로만 본다.
// 세션·예약·기기는 실제 객체라 상태 변화(getStatus 등)를 그대로 확인한다
class LiveReconcilerTest {
    LiveSessionRepository sessions = mock(LiveSessionRepository.class);
    ManagerDeviceRepository devices = mock(ManagerDeviceRepository.class);
    LiveKitGateway livekit = mock(LiveKitGateway.class);
    LiveService live = mock(LiveService.class);
    LiveWebhookService webhooks = mock(LiveWebhookService.class);
    BookingService bookings = mock(BookingService.class);
    OutboxService outbox = mock(OutboxService.class);
    AppClock clock = new AppClock(Clock.fixed(NOW, AppClock.ZONE));
    // 트랜잭션 관리자 목: 실제 DB 없이 TransactionTemplate 이 연 트랜잭션(getTransaction)·커밋(commit) 횟수만 기록한다
    PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    LiveReconciler reconciler;
    Booking booking;
    LiveSession session;
    ManagerDevice device;
    String room;

    // 기본 준비: 10분 전에 시작한 진행 중 작업, 10분 전부터 카메라를 기다리는(waiting_camera) 세션 한 개, MANAGER 의 바디캠.
    // '끝나지 않은 세션 id' 조회는 이 세션 하나를 주고, 방 참가자는 비어 있다. 다른 조회(끝난 세션·잠긴 기기)는 목 기본값인 빈 목록이다
    @BeforeEach
    void setUp() {
        reconciler = new LiveReconciler(sessions, devices, livekit, live, webhooks, bookings, outbox, clock, new TransactionTemplate(txManager));
        booking = TestBookings.inProgress(CUSTOMER, MANAGER, LocalDate.of(2026, 9, 15), LocalTime.of(10, 0), NOW.minusSeconds(600));
        room = "booking-" + booking.getId();
        session = LiveSession.create(booking.getId());
        session.startWaiting("ing-1", NOW.minusSeconds(600));
        device = LiveTestSupport.device();
        when(sessions.findIdsByStatusIn(any())).thenReturn(List.of(booking.getId()));
        when(sessions.lockByIdIfFree(booking.getId())).thenReturn(Optional.of(session)); // 재조정기는 id 를 읽은 뒤 행을 잠근다
        when(bookings.find(booking.getId())).thenReturn(Optional.of(booking));
        when(devices.findById(MANAGER)).thenReturn(Optional.of(device));
        when(livekit.listParticipants(room)).thenReturn(List.of());
    }

    @Test
    void run_은_세션마다_별도_트랜잭션으로_reconcile_한다() {
        // 한 트랜잭션에 모으면 처리한 세션의 행 잠금이 배치 끝까지 남아 그 세션의 pause·웹훅이 기다린다 — 세션마다 잠그고 커밋한다
        reconciler.run();

        // 세션이 하나이고 다른 목록은 비어 있으므로 트랜잭션도 정확히 한 번 열리고 한 번 커밋된다
        verify(txManager).getTransaction(any());
        verify(txManager).commit(any());
        verify(sessions).findIdsByStatusIn(any());
    }

    // 세션은 카메라 대기인데 방에 바디캠 참가자가 영상 트랙 T1 과 함께 있다(붙음 웹훅 유실) → LiveService.cameraAttached 로 넘긴다
    @Test
    void 카메라가_보이는데_대기_중이면_붙은_것으로_처리한다() {
        when(livekit.listParticipants(room)).thenReturn(List.of(camera("T1", false)));
        assertThat(reconciler.reconcile()).isEqualTo(1); // 반환값 = 처리한 세션·기기 수 (여기서는 이 세션 하나)
        verify(live).cameraAttached(session, device, "T1");
    }

    // 세션은 live 인데 방 참가자가 비어 있다(끊김 웹훅 유실) → 카메라 대기로 바꾸고, 기기 offline, 작업 중이라 매니저 끊김 푸시
    @Test
    void 카메라가_안_보이는데_live_면_waiting_camera_로_되돌리고_알린다() {
        session.cameraAttached("T1", false, NOW);
        reconciler.reconcile();
        assertThat(session.getStatus()).isEqualTo(LiveSession.WAITING_CAMERA);
        assertThat(device.isOnline()).isFalse();
        verify(outbox).publish(new CameraOffline(booking.getId(), MANAGER, "disconnected"));
    }

    // 기본 세션은 10분째 대기 → 첫 reconcile 에 '대기' 푸시와 알린 시각 기록, 두 번째에는 다시 보내지 않는다.
    // 4분째 대기인 새 세션으로 바꿔도 5분 전이라 보내지 않는다 (times(1) = 전체에서 한 번)
    @Test
    void 대기가_5분을_넘기면_매니저_푸시를_한_번만() {
        reconciler.reconcile();
        verify(outbox).publish(new CameraOffline(booking.getId(), MANAGER, "waiting"));
        assertThat(session.getCameraWaitNotifiedAt()).isEqualTo(NOW);
        reconciler.reconcile();
        verify(outbox, times(1)).publish(any());
        LiveSession fresh = LiveSession.create(booking.getId());
        fresh.startWaiting("ing-1", NOW.minus(Duration.ofMinutes(4)));
        when(sessions.lockByIdIfFree(booking.getId())).thenReturn(Optional.of(fresh));
        reconciler.reconcile();
        verify(outbox, times(1)).publish(any()); // 아직 5분 전
    }

    // 잠금 시도가 빈 값을 주면(다른 요청이 그 행을 잠그고 있음) 그 세션은 처리 수에 넣지 않고 LiveKit 도 부르지 않는다
    @Test
    void 다른_요청이_쥔_세션은_건너뛰고_다음_분에_본다() {
        when(sessions.lockByIdIfFree(booking.getId())).thenReturn(Optional.empty()); // SKIP LOCKED
        assertThat(reconciler.reconcile()).isZero();
        verify(livekit, never()).listParticipants(any());
    }

    @Test
    void 작업은_진행_중인데_영상이_막혀_있으면_unmute_한다() {
        // jobs/resume 은 됐는데 live/resume 이 서버에 닿지 않은 경우(앱 종료·네트워크) — 계획 §9.2 의 재조정기 계약
        session.cameraAttached("T1", true, NOW.minusSeconds(600)); // paused, pending 없음 (10분 전)
        session.jobPaused(NOW.minusSeconds(590)); // jobs/pause 가 됐고 그 뒤 jobs/resume — 지금 작업은 in_progress
        when(livekit.listParticipants(room)).thenReturn(List.of(camera("T1", true)));
        when(live.setMuted(session, device, false)).thenReturn(true); // setMuted 는 목 — unmute 가 확인됐다고 답하게 한다

        reconciler.reconcile();

        verify(live).setMuted(session, device, false);
        assertThat(session.getStatus()).isEqualTo(LiveSession.LIVE);
    }

    // live/pause 가 '멈췄다' 고 답하면 videoHeld 가 남는다. jobs/pause 가 실패해 작업이 진행 중이어도 재개 요청 전까지 막아 둔다 (재접속은 아래 '매니저가 멈춘 채' 테스트들)
    @Test
    void 영상만_막고_작업_일시정지가_실패했으면_시간이_지나도_영상을_다시_켜지_않는다() { // 자체 점검 #15 — 매니저는 '영상은 중단됐다' 를 믿고 자리를 비웠을 수 있다
        session.cameraAttached("T1", true, NOW.minusSeconds(600)); // live/pause ① 10분 전, jobs/pause ② 실패(작업은 한 번도 paused 가 아니다)
        session.holdVideo(); // ① 이 매니저에게 '멈췄다' 고 답했다 (LiveService.pause)
        when(livekit.listParticipants(room)).thenReturn(List.of(camera("T1", true)));
        reconciler.reconcile();
        verify(live, never()).setMuted(any(), any(), eq(false));
        assertThat(session.getStatus()).isEqualTo(LiveSession.PAUSED);
    }

    @Test
    void 방금_paused_가_된_세션은_작업이_아직_in_progress_여도_unmute_하지_않는다_live_pause_와_jobs_pause_사이() {
        session.cameraAttached("T1", false, NOW.minusSeconds(600));
        session.muted(NOW.minusSeconds(5)); // live/pause ① 직후, jobs/pause ② 는 아직
        when(livekit.listParticipants(room)).thenReturn(List.of(camera("T1", true)));
        reconciler.reconcile();
        verify(live, never()).setMuted(any(), any(), eq(false));
        assertThat(session.getStatus()).isEqualTo(LiveSession.PAUSED);
    }

    @Test
    void 재조정기의_unmute_확인_실패는_unmute_pending_을_남긴다() {
        // 명령은 카메라에 먹었을 수 있다 — 표시가 없으면 다음 live/pause 가 '이미 paused' 로 mute 없이 통과해 영상이 나가는데 일시정지가 된다 (검수 3차)
        session.cameraAttached("T1", true, NOW.minusSeconds(600));
        session.jobPaused(NOW.minusSeconds(590));
        when(livekit.listParticipants(room)).thenReturn(List.of(camera("T1", true)));
        when(live.setMuted(session, device, false)).thenReturn(false);
        reconciler.reconcile();
        assertThat(session.isUnmutePending()).isTrue();
        assertThat(session.mayBeStreaming()).isTrue();
    }

    // 방의 영상 트랙 sid(T2)가 세션에 적힌 것(T1)과 다르면 새 트랙으로 고쳐 적는다 — 그 트랙은 막혀 있지 않아 세션이 live 가 되고,
    // 작업이 일시정지이므로 곧바로 mute 해 확인되면 paused 로 돌아간다
    @Test
    void 웹훅_없이_재접속한_새_트랙은_sid_를_갱신하고_일시정지_작업이면_바로_mute_한다() {
        booking.pause();
        session.cameraAttached("T1", true, NOW); // paused, 옛 sid T1
        when(livekit.listParticipants(room)).thenReturn(List.of(camera("T2", false))); // 새 트랙 T2 가 unmuted 로 송출
        when(live.setMuted(session, device, true)).thenReturn(true);

        reconciler.reconcile();

        assertThat(session.getCameraTrackSid()).isEqualTo("T2"); // 옛 sid 로 mute 를 보내면 LiveKit 이 못 찾아 매분 실패한다
        verify(live).setMuted(session, device, true);
        assertThat(session.getStatus()).isEqualTo(LiveSession.PAUSED);
    }

    @Test
    void mute_pending_인데_이미_막혀_있으면_paused_로_확정하고_시청자_수를_참가자로_맞춘다() {
        booking.pause(); // mute 요청은 작업이 일시정지일 때만 유효하다
        session.cameraAttached("T1", false, NOW);
        session.requestMute();
        LiveKitGateway.Participant viewer = new LiveKitGateway.Participant("customer-" + CUSTOMER, List.of());
        // 방에는 이미 막힌 트랙의 바디캠 + 고객 참가자 둘(같은 객체를 두 번 넣었다) — 세션의 시청자 수는 이 목록으로 2 가 된다
        when(livekit.listParticipants(room)).thenReturn(List.of(camera("T1", true), viewer, viewer));

        reconciler.reconcile();

        assertThat(session.getStatus()).isEqualTo(LiveSession.PAUSED);
        assertThat(session.isMutePending()).isFalse();
        assertThat(session.getViewerCount()).isEqualTo(2);
        verify(live, never()).setMuted(any(), any(), eq(true));
    }

    // 끝난(ended) 세션인데 녹화가 recording 으로 남았다(녹화 종료 웹훅 유실). LiveKit 에 물어보니 eg-9 가 complete 로 끝나 있다
    // → 웹훅을 받았을 때와 같은 LiveWebhookService.applyEgress 로 결과를 적는다. 끝나지 않은 세션 목록을 비워 녹화 마무리 단계만 일을 하게 했다
    @Test
    void 끝난_세션의_남은_녹화는_LiveKit_조회로_마무리한다() {
        LiveSession ended = LiveSession.create(booking.getId());
        ended.startWaiting("ing-1", NOW);
        ended.recordingStarted("eg-9");
        ended.end(NOW);
        when(sessions.findIdsByStatusIn(any())).thenReturn(List.of());
        when(sessions.findByStatusAndRecordingStatusIn(eq(LiveSession.ENDED), any())).thenReturn(List.of(ended));
        when(sessions.lockByIdIfFree(ended.getBookingId())).thenReturn(Optional.of(ended)); // 세션마다 잠그고 다시 읽는다
        EgressInfo finished = new EgressInfo("eg-9", room, "complete", "x.mp4", 100, null);
        when(livekit.getEgress("eg-9")).thenReturn(Optional.of(finished));

        reconciler.reconcile();

        verify(webhooks).applyEgress(ended, finished);
    }

    @Test
    void stop_때_녹화_정지가_실패했던_끝난_세션은_정지를_다시_보낸다() {
        // 세션은 ended 인데 recording_status 가 recording 그대로 = stopEgress 가 LiveKit 에 안 닿았다 → 녹화·방이 살아 있을 수 있다
        LiveSession ended = LiveSession.create(booking.getId());
        ended.startWaiting("ing-1", NOW);
        ended.recordingStarted("eg-9");
        ended.end(NOW);
        when(sessions.findIdsByStatusIn(any())).thenReturn(List.of());
        when(sessions.findByStatusAndRecordingStatusIn(eq(LiveSession.ENDED), any())).thenReturn(List.of(ended));
        when(sessions.lockByIdIfFree(ended.getBookingId())).thenReturn(Optional.of(ended)); // 세션마다 잠그고 다시 읽는다
        when(livekit.getEgress("eg-9")).thenReturn(Optional.of(new EgressInfo("eg-9", room, "active", null, 0, null)));

        reconciler.reconcile();

        verify(livekit).stopEgress("eg-9");
        verify(livekit).deleteRoom(room);
        assertThat(ended.getRecordingStatus()).isEqualTo(LiveSession.REC_UPLOADING);
        verify(webhooks, never()).applyEgress(any(), any());
    }

    @Test
    void 작업_중_끊긴_카메라의_5분_대기_푸시는_끊긴_시각부터_잰다() {
        session.cameraAttached("T1", false, NOW.minus(Duration.ofMinutes(20))); // 20분 전에 붙었다가
        reconciler.reconcile(); // 지금 끊김 → disconnected 푸시
        verify(outbox).publish(new CameraOffline(booking.getId(), MANAGER, "disconnected"));
        reconciler.reconcile(); // 같은 순간 — 시작 시각 기준이면 여기서 '5분 대기' 가 바로 나갔다
        verify(outbox, never()).publish(new CameraOffline(booking.getId(), MANAGER, "waiting"));
    }

    @Test
    void 작업이_다시_진행_중이면_남아_있던_mute_요청은_버린다() {
        session.cameraAttached("T1", false, NOW);
        session.requestMute(); // jobs/pause 가 실패해 남은 요청. booking 은 in_progress
        when(livekit.listParticipants(room)).thenReturn(List.of(camera("T1", false)));
        reconciler.reconcile();
        assertThat(session.isMutePending()).isFalse();
        assertThat(session.getStatus()).isEqualTo(LiveSession.LIVE);
        verify(live, never()).setMuted(any(), any(), eq(true));
    }

    @Test
    void 작업은_진행_중인데_트랙이_막혀_있으면_실제로_unmute_한다_pause_확인_실패_뒤() {
        session.cameraAttached("T1", false, NOW);
        session.muteUnconfirmed(); // live/pause 가 mute 를 보냈지만 확인 못 함(502) — 카메라는 실제로 muted, 작업은 in_progress
        when(livekit.listParticipants(room)).thenReturn(List.of(camera("T1", true)));
        when(live.setMuted(any(), any(), eq(false))).thenReturn(true);
        reconciler.reconcile();
        verify(live).setMuted(session, device, false);
        assertThat(session.isMutePending()).isFalse();
        assertThat(session.getStatus()).isEqualTo(LiveSession.LIVE);

        // 세션이 live 로 남은 채 트랙만 muted 인 경우도 같다
        session.clearMuteRequests();
        reconciler.reconcile();
        verify(live, times(2)).setMuted(session, device, false);
    }

    // 첫 참가자 조회가 ApiException 이 아닌 RuntimeException 을 던지게 한다 — 커밋·DB 오류처럼 세션 트랜잭션 밖(each)까지 올라가는 실패다.
    // reconcile 은 예외를 밖으로 내지 않고 그 세션만 건너뛰며, 다음 reconcile 에서는 같은 세션을 다시 본다(조회 2회)
    @Test
    void 한_세션의_커밋_실패는_배치를_끊지_않는다() {
        session.cameraAttached("T1", false, NOW);
        // thenThrow(...).thenReturn(...) = 첫 호출은 예외, 두 번째 호출부터는 이 목록
        when(livekit.listParticipants(room)).thenThrow(new RuntimeException("boom")).thenReturn(List.of(camera("T1", false)));
        reconciler.reconcile(); // 던지지 않는다
        reconciler.reconcile();
        verify(livekit, times(2)).listParticipants(room);
    }

    @Test
    void 방금_끝난_세션은_방_삭제와_Ingress_대기_방_복귀를_다시_확인한다() {
        // stop 때 deleteRoom 만 실패하면 세션은 ended 인데 방이 남아 시청자에게 영상이 계속 갈 수 있다
        LiveSession ended = LiveSession.create(booking.getId());
        ended.startWaiting("ing-1", NOW.minusSeconds(120));
        ended.end(NOW.minusSeconds(60));
        device.setPendingRoom("parking-" + MANAGER);
        when(sessions.findIdsByStatusIn(any())).thenReturn(List.of());
        when(sessions.findByStatusAndEndedAtAfter(eq(LiveSession.ENDED), any())).thenReturn(List.of(ended));
        when(sessions.lockByIdIfFree(ended.getBookingId())).thenReturn(Optional.of(ended));

        reconciler.reconcile();

        verify(livekit).deleteRoom(room);
        verify(live).bindIngressQuietly(device, "parking-" + MANAGER);
    }

    @Test
    void 방금_끝난_세션의_재확인은_같은_매니저의_다음_작업이_쓰는_바디캠을_건드리지_않는다() { // 자체 점검 #1 — 다음 작업의 일시정지 잠금을 매분 다시 열었다
        LiveSession ended = LiveSession.create(booking.getId());
        ended.startWaiting("ing-1", NOW.minusSeconds(120));
        ended.end(NOW.minusSeconds(60));
        when(sessions.findIdsByStatusIn(any())).thenReturn(List.of());
        when(sessions.findByStatusAndEndedAtAfter(eq(LiveSession.ENDED), any())).thenReturn(List.of(ended));
        when(sessions.lockByIdIfFree(ended.getBookingId())).thenReturn(Optional.of(ended));
        when(live.usedByAnotherJob(device, ended.getBookingId())).thenReturn(true);

        reconciler.reconcile();

        verify(livekit).deleteRoom(room);
        verify(live, never()).unlockIngress(device);
        verify(live, never()).bindIngressQuietly(any(), any());
    }

    @Test
    void 잠긴_채_남은_바디캠은_일시정지한_작업이_없으면_기기_단위로_푼다() { // 자체 점검 #52 — 15분 창이 지나면 영영 잠겨 다음 작업을 시작하지 못했다
        device.setIngressEnabled(false);
        when(sessions.findIdsByStatusIn(any())).thenReturn(List.of());
        when(devices.findByIngressEnabledFalse()).thenReturn(List.of(device));
        when(bookings.activeJob(MANAGER)).thenReturn(Optional.empty());
        reconciler.reconcile();
        verify(live).unlockIngress(device);

        org.mockito.Mockito.clearInvocations(live); // 지금까지 기록된 호출을 지운다 — 아래 never() 가 앞의 unlockIngress 호출에 걸리지 않게
        booking.pause(); // 이 매니저의 작업이 일시정지 중이면 그 잠금은 살아 있어야 한다
        when(bookings.activeJob(MANAGER)).thenReturn(Optional.of(booking));
        reconciler.reconcile();
        verify(live, never()).unlockIngress(device);
    }

    // 작업이 완료(done)됐는데 세션이 아직 열려 있으면 LiveService.stopSession 에 맡기고, 방 참가자는 읽지 않는다
    @Test
    void 끝난_작업은_세션을_정리한다() {
        booking.complete(NOW);
        reconciler.reconcile();
        verify(live).stopSession(session, device);
        verify(livekit, never()).listParticipants(any());
    }

    // 한 번의 reconcile 에서 세 가지를 같이 본다: ① paused + unmute_pending(재개 확인 실패)이고 작업은 진행 중 → 다시 unmute 해 live
    // ② 녹화 eg-1 이 LiveKit 에서 이미 끝났다 → applyEgress ③ 대기 방으로 옮길 일이 미뤄져 있다 → bindIngressQuietly. 녹화 보장(ensureRecording)도 불린다
    @Test
    void 재개_확인에_실패했던_세션은_다시_unmute_하고_끝난_녹화는_반영하고_pending_room_을_적용한다() {
        session.cameraAttached("T1", false, NOW);
        session.muted(NOW.minusSeconds(600));
        session.requestUnmute();
        session.recordingStarted("eg-1");
        device.setPendingRoom("parking-" + MANAGER);
        when(livekit.listParticipants(room)).thenReturn(List.of(camera("T1", true)));
        when(live.setMuted(session, device, false)).thenReturn(true);
        EgressInfo finished = new EgressInfo("eg-1", room, "complete", "x.mp4", 100, null);
        when(livekit.getEgress("eg-1")).thenReturn(Optional.of(finished));

        reconciler.reconcile();

        assertThat(session.getStatus()).isEqualTo(LiveSession.LIVE);
        assertThat(session.isUnmutePending()).isFalse();
        verify(webhooks).applyEgress(session, finished);
        verify(live).bindIngressQuietly(device, "parking-" + MANAGER);
        verify(live).ensureRecording(session);
    }

    // 작업은 일시정지, 세션은 mute 요청이 남은 live, 실제 트랙은 안 막힘 → 다시 mute 하고, 목 setMuted 가 확인됐다고 답하면 paused 로 확정
    @Test
    void mute_pending_인데_트랙이_안_막혀_있으면_다시_mute() {
        booking.pause();
        session.cameraAttached("T1", false, NOW);
        session.requestMute();
        when(livekit.listParticipants(room)).thenReturn(List.of(camera("T1", false)));
        when(live.setMuted(session, device, true)).thenReturn(true);
        reconciler.reconcile();
        assertThat(session.getStatus()).isEqualTo(LiveSession.PAUSED);
        assertThat(session.isMutePending()).isFalse();
    }

    @Test
    void 카메라가_없을_때_일시정지_작업이면_잠그고_진행_중이면_연다() { // 감사 H-02 — 웹훅 유실·잠금 실패 보정
        // 방 참가자가 비어 있다(기본 준비). 먼저 진행 중 작업 → 잠금 해제만, 그다음 작업을 일시정지로 바꾸면 → 잠금
        reconciler.reconcile();
        verify(live).unlockIngress(device);
        verify(live, never()).lockIngress(any());

        booking.pause();
        reconciler.reconcile();
        verify(live).lockIngress(device);
    }

    // ── 매니저가 멈춘 영상(videoHeld)은 작업이 진행 중이어도 재개 전까지 막는다 (2026-09-28 사용자 결정) ──

    // 영상만 막고 jobs/pause 가 실패한 세션에 웹훅 없이 새 트랙 T2 가 unmuted 로 붙었다 → 작업은 진행 중이어도 바로 mute
    @Test
    void 매니저가_멈춘_채_웹훅_없이_다시_붙은_새_트랙은_작업이_진행_중이어도_mute_한다() {
        session.cameraAttached("T1", true, NOW.minusSeconds(600));
        session.holdVideo(); // live/pause 가 '멈췄다' 고 답했다 — jobs/pause 는 실패
        when(livekit.listParticipants(room)).thenReturn(List.of(camera("T2", false)));
        when(live.setMuted(session, device, true)).thenReturn(true);

        reconciler.reconcile();

        verify(live).setMuted(session, device, true);
        verify(live, never()).setMuted(any(), any(), eq(false));
        assertThat(session.getStatus()).isEqualTo(LiveSession.PAUSED);
    }

    // 재접속 처리에서 mute 확인을 못 해 mute 요청이 남았고, 지금 트랙은 막혀 있다 → 작업이 진행 중이어도 요청을 버리거나 unmute 하지 않고 paused 로 확정
    @Test
    void 매니저가_멈춘_채_남은_mute_요청은_작업이_진행_중이어도_버리지_않는다() {
        session.cameraAttached("T1", false, NOW.minusSeconds(60));
        session.holdVideo();
        session.requestMute();
        when(livekit.listParticipants(room)).thenReturn(List.of(camera("T1", true)));

        reconciler.reconcile();

        verify(live, never()).setMuted(any(), any(), eq(false));
        assertThat(session.getStatus()).isEqualTo(LiveSession.PAUSED);
        assertThat(session.isMutePending()).isFalse();
    }

    // 카메라가 없을 때: 매니저가 멈춘 채면 작업이 진행 중이어도 Ingress 를 잠가 다시 붙지 못하게 한다
    @Test
    void 매니저가_멈춘_채면_카메라가_없을_때_작업이_진행_중이어도_잠근다() {
        session.holdVideo();
        reconciler.reconcile();
        verify(live).lockIngress(device);
        verify(live, never()).unlockIngress(any());
    }

    // 작업 일시정지 중 바디캠이 다시 붙어 한 번 더 mute 됐다(영상 일시정지 시각이 작업 일시정지보다 뒤) → jobs/resume 만 닿고 live/resume 은 잃었다.
    // 예전에는 시각 비교로 '작업 일시정지 실패' 로 오인해 영영 막았다. 이제 jobs/resume 이 멈춤 표시를 풀었으므로 다시 켠다
    @Test
    void 일시정지_중_재접속으로_다시_mute_됐어도_작업을_재개했으면_영상을_다시_켠다() {
        session.jobPaused(NOW.minusSeconds(600)); // jobs/pause 10분 전
        session.cameraAttached("T2", true, NOW.minusSeconds(300)); // 일시정지 중 재접속 → 5분 전 다시 mute (영상 일시정지 시각이 뒤)
        // videoHeld 없음 — jobs/resume(JobService.resume → LiveSessionService.jobResumed)이 풀었다. 작업은 in_progress
        when(livekit.listParticipants(room)).thenReturn(List.of(camera("T2", true)));
        when(live.setMuted(session, device, false)).thenReturn(true);

        reconciler.reconcile();

        verify(live).setMuted(session, device, false);
        assertThat(session.getStatus()).isEqualTo(LiveSession.LIVE);
    }

    // 매니저가 멈춘 채 카메라 대기가 5분을 넘어도 '바디캠이 붙지 않아요' 푸시를 보내지 않는다 (쉬는 중이다)
    @Test
    void 매니저가_멈춘_채면_카메라_대기_5분_푸시를_보내지_않는다() {
        session.holdVideo();
        reconciler.reconcile();
        verify(outbox, never()).publish(new CameraOffline(booking.getId(), MANAGER, "waiting"));
    }

    // 기기 단위 잠금 정리: 진행 중 작업의 영상을 매니저가 멈춘 채면 잠금은 살아 있어야 한다
    @Test
    void 잠긴_바디캠은_매니저가_영상을_멈춘_채면_풀지_않는다() {
        device.setIngressEnabled(false);
        session.holdVideo();
        when(sessions.findIdsByStatusIn(any())).thenReturn(List.of());
        when(devices.findByIngressEnabledFalse()).thenReturn(List.of(device));
        when(bookings.activeJob(MANAGER)).thenReturn(Optional.of(booking)); // 작업은 진행 중
        when(sessions.findById(booking.getId())).thenReturn(Optional.of(session));
        reconciler.reconcile();
        verify(live, never()).unlockIngress(device);
    }
}
