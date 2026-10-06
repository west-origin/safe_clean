package kr.ansimclean.api.live;

import kr.ansimclean.api.booking.Booking;
import kr.ansimclean.api.booking.BookingService;
import kr.ansimclean.api.booking.TestBookings;
import kr.ansimclean.api.common.events.CameraOffline;
import kr.ansimclean.api.common.events.RecordingFailed;
import kr.ansimclean.api.common.events.RecordingReady;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.live.LiveKitGateway.EgressInfo;
import kr.ansimclean.api.live.LiveKitGateway.IngressInfo;
import kr.ansimclean.api.live.LiveKitGateway.WebhookEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static kr.ansimclean.api.live.LiveTestSupport.CUSTOMER;
import static kr.ansimclean.api.live.LiveTestSupport.MANAGER;
import static kr.ansimclean.api.live.LiveTestSupport.NOW;
import static kr.ansimclean.api.live.LiveTestSupport.camera;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 웹훅 6종의 전이와 중복 방어 (BACKEND_PLAN.md §8.1) */
// LiveWebhookService.handle 단위 테스트. 여기서 보는 웹훅: ingress_started·ingress_ended(바디캠 송출 시작·끝), egress_ended(녹화 끝),
// participant_joined·participant_left(시청자 입장·퇴장), room_finished(방 닫힘). egress_started·updated 는 이 파일에서 시험하지 않는다.
// LiveService 는 목이라 cameraAttached·lockIngress 같은 공통 처리는 '불렸는지' 만 확인하고, 세션·기기 상태 변화는 웹훅 서비스가 직접 한 것만 본다.
// 준비: 진행 중(in_progress) 예약 한 건, 그 예약 방의 세션(카메라 대기), Ingress "ing-1" 인 MANAGER 의 바디캠
class LiveWebhookServiceTest {
    static final AppClock CLOCK = new AppClock(Clock.fixed(NOW, AppClock.ZONE)); // NOW 에 멈춘 시계

    LiveSessionRepository sessions = mock(LiveSessionRepository.class);
    ManagerDeviceRepository devices = mock(ManagerDeviceRepository.class);
    LivekitEventRepository events = mock(LivekitEventRepository.class);
    LiveKitGateway livekit = mock(LiveKitGateway.class);
    LiveService live = mock(LiveService.class);
    BookingService bookings = mock(BookingService.class);
    OutboxService outbox = mock(OutboxService.class);
    LiveWebhookService service;
    Booking booking;
    LiveSession session;
    ManagerDevice device;
    String room;

    // 테스트마다 새로: 서비스 조립, 방 이름으로 잠가 읽으면 이 세션, Ingress id "ing-1" 로 찾으면 이 기기가 나오게 한다
    @BeforeEach
    void setUp() {
        service = new LiveWebhookService(sessions, devices, events, livekit, live, bookings, outbox, CLOCK);
        booking = TestBookings.inProgress(CUSTOMER, MANAGER, LocalDate.of(2026, 9, 15), LocalTime.of(10, 0), NOW);
        room = "booking-" + booking.getId();
        session = LiveSession.create(booking.getId());
        session.startWaiting("ing-1", NOW);
        device = LiveTestSupport.device();
        when(bookings.find(booking.getId())).thenReturn(Optional.of(booking));
        when(sessions.lockByRoomName(room)).thenReturn(Optional.of(session));
        when(devices.findByIngressId("ing-1")).thenReturn(Optional.of(device));
    }

    // ingress_* 웹훅 한 건을 만든다: 이벤트 id·이름과, 바디캠 Ingress "ing-1" 이 묶인 방 이름
    static WebhookEvent ingress(String id, String event, String room) {
        return new WebhookEvent(id, event, room, null, new IngressInfo("ing-1", "rtmp://x", "k", room, true), null);
    }

    // livekit_events 에 이미 있는 id 라고 해 두면 handle 은 false(이미 처리함)를 돌려주고 카메라 처리를 하지 않는다
    @Test
    void 같은_이벤트_id_는_한_번만() {
        when(events.existsById("e1")).thenReturn(true);
        assertThat(service.handle(ingress("e1", LiveWebhookService.INGRESS_STARTED, room))).isFalse();
        verify(live, never()).cameraAttached(any(), any(), any());
    }

    // 예약 방에 붙으면: 이벤트 id 를 저장하고, LiveKit 에서 바디캠의 영상 트랙 T1 을 찾아 cameraAttached 로 넘기고, 기기는 online.
    // 이어서 대기 방(parking-)에 붙은 웹훅은 기기만 online 으로 두고 세션 처리는 하지 않는다 — cameraAttached 는 전체 한 번뿐
    @Test
    void ingress_started_는_트랙_sid_를_찾아_cameraAttached_대기_방이면_기기만_online() {
        when(livekit.getParticipant(room, "camera-" + MANAGER)).thenReturn(Optional.of(camera("T1", false)));
        assertThat(service.handle(ingress("e2", LiveWebhookService.INGRESS_STARTED, room))).isTrue();
        verify(events).save(any());
        verify(live).cameraAttached(session, device, "T1");
        assertThat(device.isOnline()).isTrue();

        service.handle(ingress("e3", LiveWebhookService.INGRESS_STARTED, "parking-" + MANAGER));
        verify(live).cameraAttached(any(), any(), any()); // 한 번만
    }

    // 카메라가 붙어 있던(live) 세션 + 대기 방으로 옮길 일이 미뤄진 기기: 끊김 웹훅 뒤 세션은 카메라 대기·트랙 없음, 기기는 offline,
    // 진행 중 작업이라 매니저 끊김 푸시(CameraOffline 아웃박스), 미뤄 둔 방 옮기기를 지금 시도한다
    @Test
    void ingress_ended_는_waiting_camera_로_되돌리고_매니저에게_알리고_pending_room_을_적용한다() {
        session.cameraAttached("T1", false, NOW);
        device.setPendingRoom("parking-" + MANAGER);
        service.handle(ingress("e4", LiveWebhookService.INGRESS_ENDED, room));
        assertThat(session.getStatus()).isEqualTo(LiveSession.WAITING_CAMERA);
        assertThat(session.getCameraTrackSid()).isNull();
        assertThat(device.isOnline()).isFalse();
        verify(outbox).publish(new CameraOffline(booking.getId(), MANAGER, "disconnected"));
        verify(live).bindIngressQuietly(device, "parking-" + MANAGER);
    }

    @Test
    void 일시정지_중_바디캠을_끄면_장애_푸시를_보내지_않고_잠근다() { // 자체 점검 #4 — 쉬는 중인데 '바디캠이 끊겼어요' 가 갔다
        session.cameraAttached("T1", true, NOW);
        booking.pause();
        service.handle(ingress("e30", LiveWebhookService.INGRESS_ENDED, room));
        assertThat(session.getStatus()).isEqualTo(LiveSession.WAITING_CAMERA);
        verify(outbox, never()).publish(any(CameraOffline.class));
        verify(live).lockIngress(device);
    }

    // 녹화 완료 웹훅이 왔는데 '탈퇴한 고객이라 지웠다'(discard… 가 true) 고 하면, 세션을 ready 로 적지 않고 녹화 준비 푸시도 남기지 않는다
    @Test
    void 업로드_중_탈퇴한_고객의_녹화는_준비됨으로_적지_않고_알리지_않는다() { // 자체 점검 #3
        session.recordingStarted("eg-1");
        when(live.discardRecordingOfDeletedCustomer(session, CUSTOMER, booking.getId() + ".mp4")).thenReturn(true);
        service.handle(new WebhookEvent("e31", LiveWebhookService.EGRESS_ENDED, room, null, null, new EgressInfo("eg-1", room, "complete", booking.getId() + ".mp4", 630, null)));
        assertThat(session.getRecordingStatus()).isNotEqualTo(LiveSession.REC_READY);
        verify(outbox, never()).publish(any(RecordingReady.class));
    }

    // 작업이 완료(done)된 뒤 온 끊김 웹훅: 세션은 live 그대로, 아웃박스에도 아무것도 남기지 않는다 (정리는 live/stop·재조정기 몫)
    @Test
    void 끝난_작업의_ingress_ended_는_세션을_건드리지_않는다() {
        session.cameraAttached("T1", false, NOW);
        booking.complete(NOW);
        service.handle(ingress("e5", LiveWebhookService.INGRESS_ENDED, room));
        assertThat(session.getStatus()).isEqualTo(LiveSession.LIVE);
        verify(outbox, never()).publish(any());
    }

    // 앞: 녹화 eg-1 이 complete 로 끝나면 ready + 길이 630초 + 고객 푸시(RecordingReady).
    // 뒤: 다른 방("booking-x")의 녹화 eg-2 가 failed 로 끝나면 failed + 실패 안내(RecordingFailed). 두 세션 모두 같은 예약을 가리키게 만들었다
    @Test
    void egress_ended_는_complete_면_ready_아니면_failed() {
        session.recordingStarted("eg-1");
        service.handle(new WebhookEvent("e6", LiveWebhookService.EGRESS_ENDED, room, null, null, new EgressInfo("eg-1", room, "complete", booking.getId() + ".mp4", 630, null)));
        assertThat(session.getRecordingStatus()).isEqualTo(LiveSession.REC_READY);
        assertThat(session.getDurationSeconds()).isEqualTo(630);
        verify(outbox).publish(new RecordingReady(booking.getId(), CUSTOMER));

        LiveSession other = LiveSession.create(booking.getId());
        other.recordingStarted("eg-2");
        when(sessions.lockByRoomName("booking-x")).thenReturn(Optional.of(other));
        service.handle(new WebhookEvent("e7", LiveWebhookService.EGRESS_ENDED, "booking-x", null, null, new EgressInfo("eg-2", "booking-x", "failed", null, 0, "disk")));
        assertThat(other.getRecordingStatus()).isEqualTo(LiveSession.REC_FAILED);
        verify(outbox).publish(new RecordingFailed(booking.getId(), CUSTOMER)); // 고객 안내 + 운영자 확인 (검증 검토)
    }

    // 세션에 '구독 막음' 표시가 있으면 들어온 고객의 구독 권한을 바로 끈다. 표시를 지우면(그리고 목 LiveService 의 shouldBlockViewers 도 false) 막지 않는다
    @Test
    void 일시정지로_시청을_막아_둔_방에_옛_토큰으로_다시_들어온_고객은_바로_막는다() { // 재감사 H-02
        session.setViewersBlocked(true);
        service.handle(new WebhookEvent("e20", LiveWebhookService.PARTICIPANT_JOINED, room, "customer-" + CUSTOMER, null, null));
        verify(livekit).setCanSubscribe(room, "customer-" + CUSTOMER, false);
        session.setViewersBlocked(false);
        service.handle(new WebhookEvent("e21", LiveWebhookService.PARTICIPANT_JOINED, room, "customer-other", null, null));
        verify(livekit, org.mockito.Mockito.never()).setCanSubscribe(room, "customer-other", false);
    }

    // "customer-" 로 시작하는 참가자만 시청자 수에 들어가고 매니저 미리보기("manager-")는 빠진다.
    // 방 닫힘 웹훅은 작업이 진행 중이면 세션을 그대로 두고, 작업이 done 이 된 뒤에 오면 세션을 ended 로 적는다
    @Test
    void 시청자_입장_퇴장은_customer_참가자만_센다_room_finished_는_끝난_작업만_ended() {
        service.handle(new WebhookEvent("e8", LiveWebhookService.PARTICIPANT_JOINED, room, "customer-" + CUSTOMER, null, null));
        service.handle(new WebhookEvent("e9", LiveWebhookService.PARTICIPANT_JOINED, room, "manager-" + MANAGER, null, null));
        assertThat(session.getViewerCount()).isEqualTo(1);
        service.handle(new WebhookEvent("e10", LiveWebhookService.PARTICIPANT_LEFT, room, "customer-" + CUSTOMER, null, null));
        assertThat(session.getViewerCount()).isZero();

        service.handle(new WebhookEvent("e11", LiveWebhookService.ROOM_FINISHED, room, null, null, null));
        assertThat(session.isEnded()).isFalse(); // 아직 진행 중
        booking.complete(NOW);
        service.handle(new WebhookEvent("e12", LiveWebhookService.ROOM_FINISHED, room, null, null, null));
        assertThat(session.isEnded()).isTrue();
    }

    @Test
    void 일시정지_중_카메라가_끊기면_다시_붙기_전에_Ingress_를_잠근다() { // 감사 H-02
        booking.pause();
        session.cameraAttached("T1", true, NOW);
        service.handle(ingress("e9", LiveWebhookService.INGRESS_ENDED, room));
        verify(live).lockIngress(device);
    }

    @Test
    void 진행_중_작업에서_카메라가_끊기면_잠그지_않는다() {
        session.cameraAttached("T1", false, NOW);
        service.handle(ingress("e10", LiveWebhookService.INGRESS_ENDED, room));
        verify(live, org.mockito.Mockito.never()).lockIngress(any());
    }

    // 이 예약의 담당(MANAGER)이 아닌 다른 매니저의 바디캠(Ingress "ing-9")이 이 예약 방에 붙었다는 웹훅:
    // cameraAttached 로 넘기지 않고, 그 바디캠을 자기 대기 방(parking-그 매니저id)으로 옮기라고 한다
    @Test
    void 담당이_아닌_매니저의_바디캠은_고객_방에_붙이지_않고_대기_방으로_돌린다() { // 전체 재점검 J1
        ManagerDevice stranger = ManagerDevice.register(UUID.randomUUID(), "안심캠", "AC-9", "ing-9", "rtmp://x", "k");
        when(devices.findByIngressId("ing-9")).thenReturn(Optional.of(stranger));
        service.handle(new WebhookEvent("e11", LiveWebhookService.INGRESS_STARTED, room, null, new IngressInfo("ing-9", "rtmp://x", "k", room, true), null));
        verify(live, never()).cameraAttached(any(), any(), any());
        verify(live).bindIngressQuietly(stranger, stranger.parkingRoom());
    }

    // 매니저가 영상을 멈춘 채(jobs/pause 는 실패, 작업은 진행 중) 바디캠을 끄면: 장애 푸시 없이 잠근다 — 다시 붙기 전에
    @Test
    void 매니저가_멈춘_채_바디캠이_끊기면_장애_푸시_없이_잠근다() {
        session.cameraAttached("T1", true, NOW);
        session.holdVideo();
        service.handle(ingress("e31", LiveWebhookService.INGRESS_ENDED, room));
        assertThat(session.getStatus()).isEqualTo(LiveSession.WAITING_CAMERA);
        verify(outbox, never()).publish(any(CameraOffline.class));
        verify(live).lockIngress(device);
    }
}
