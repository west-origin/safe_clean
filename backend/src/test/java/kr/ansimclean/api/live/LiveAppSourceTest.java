package kr.ansimclean.api.live;

import kr.ansimclean.api.booking.Booking;
import kr.ansimclean.api.booking.BookingService;
import kr.ansimclean.api.booking.TestBookings;
import kr.ansimclean.api.catalog.AppSettingsService;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.events.CameraOffline;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.supabase.SupabaseStorageClient;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.live.LiveKitGateway.Track;
import kr.ansimclean.api.live.LiveKitGateway.WebhookEvent;
import kr.ansimclean.api.live.dto.LiveStatusResponse;
import kr.ansimclean.api.live.dto.StartLiveResponse;
import kr.ansimclean.api.profile.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static kr.ansimclean.api.live.LiveTestSupport.CUSTOMER;
import static kr.ansimclean.api.live.LiveTestSupport.MANAGER;
import static kr.ansimclean.api.live.LiveTestSupport.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 휴대폰 송출 모드(app.livekit.source=app, BACKEND_PLAN.md §8.0): 시작(송출 토큰·기기 행)·상태(source)·track_* 웹훅 */
// LiveService·LiveWebhookService 단위 테스트. 저장소·LiveKit·예약 서비스는 Mockito 목, 세션·예약·기기는 실제 객체.
// 바디캠 모드의 같은 동작은 LiveServiceTest·LiveWebhookServiceTest 가 본다(그쪽은 LiveTestSupport.props() = ingress).
class LiveAppSourceTest {
    static final AppClock CLOCK = new AppClock(Clock.fixed(NOW, AppClock.ZONE)); // 서울 2026-09-15 14:00 에 멈춘 시계
    static final LocalDate TODAY = LocalDate.of(2026, 9, 15);

    LiveSessionRepository sessions = mock(LiveSessionRepository.class);
    ManagerDeviceRepository devices = mock(ManagerDeviceRepository.class);
    LivekitEventRepository events = mock(LivekitEventRepository.class);
    LiveKitGateway livekit = mock(LiveKitGateway.class);
    BookingService bookings = mock(BookingService.class);
    OutboxService outbox = mock(OutboxService.class);
    LiveService service;
    Booking booking;
    String room;

    // 오늘 10:00 에 배정된(scheduled) 예약. 기기 행은 아직 없다(바디캠 미등록 매니저). save 는 받은 객체를 그대로 돌려준다
    @BeforeEach
    void setUp() {
        service = new LiveService(sessions, devices, livekit, bookings, mock(ProfileService.class), mock(AppSettingsService.class),
                mock(SupabaseStorageClient.class), outbox, LiveTestSupport.appProps(), CLOCK);
        booking = TestBookings.scheduled(CUSTOMER, MANAGER, TODAY, LocalTime.of(10, 0));
        room = "booking-" + booking.getId();
        when(bookings.requireManagerBooking(MANAGER, booking.getId())).thenReturn(booking);
        booking.confirmBeforePhotos(NOW);
        when(bookings.lockManagerBooking(MANAGER, booking.getId())).thenReturn(booking);
        when(bookings.find(booking.getId())).thenReturn(Optional.of(booking));
        when(devices.findById(MANAGER)).thenReturn(Optional.empty());
        when(devices.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessions.findById(booking.getId())).thenReturn(Optional.empty());
        when(sessions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(livekit.publishToken(anyString(), anyString(), anyString(), any())).thenReturn("pub-tok");
        when(livekit.url()).thenReturn("wss://lk.example");
    }

    // 바디캠이 없어도 시작된다: 휴대폰 카메라 기기 행을 만들고(잠금 후), 방을 만들고, 카메라 대기 상태, 송출 토큰(이름표 camera-매니저id).
    // 바디캠 모드의 프리뷰(preview)는 없고 Ingress 는 건드리지 않는다
    @Test
    void 시작은_기기_행을_만들고_송출_토큰을_주며_Ingress_를_건드리지_않는다() {
        StartLiveResponse res = service.start(MANAGER, booking.getId());
        assertThat(res.roomName()).isEqualTo(room);
        assertThat(res.status()).isEqualTo(LiveSession.WAITING_CAMERA);
        assertThat(res.cameraBusy()).isFalse();
        assertThat(res.preview()).isNull();
        assertThat(res.publish().url()).isEqualTo("wss://lk.example");
        assertThat(res.publish().token()).isEqualTo("pub-tok");
        verify(devices).lockManager(MANAGER);
        verify(devices).save(any(ManagerDevice.class));
        verify(livekit).createRoom(room, 21600, 21600);
        verify(livekit).publishToken(eq(room), eq("camera-" + MANAGER), anyString(), eq(LiveService.PREVIEW_TTL));
        verify(livekit, never()).updateIngressRoom(anyString(), anyString());
        verify(livekit, never()).joinToken(anyString(), anyString(), anyString(), any());
    }

    // 이미 기기 행이 있으면(바디캠을 등록했던 매니저 포함) 새로 만들지 않는다. 이미 대기 중인 세션이면 방도 다시 만들지 않는다
    @Test
    void 다시_시작하면_기기_행과_방을_다시_만들지_않는다() {
        when(devices.findById(MANAGER)).thenReturn(Optional.of(ManagerDevice.phone(MANAGER)));
        LiveSession s = LiveSession.create(booking.getId());
        s.startWaiting(null, NOW);
        when(sessions.findById(booking.getId())).thenReturn(Optional.of(s));
        StartLiveResponse res = service.start(MANAGER, booking.getId());
        assertThat(res.publish().token()).isEqualTo("pub-tok");
        verify(devices, never()).save(any());
        verify(livekit, never()).createRoom(anyString(), anyInt(), anyInt());
    }

    // 오늘이 아닌 작업은 바디캠 모드와 같이 거절한다 (송출 토큰이 나가지 않는다)
    @Test
    void 오늘_작업이_아니면_거절() {
        Booking tomorrow = TestBookings.scheduled(CUSTOMER, MANAGER, TODAY.plusDays(1), LocalTime.of(10, 0));
        when(bookings.requireManagerBooking(MANAGER, tomorrow.getId())).thenReturn(tomorrow);
        tomorrow.confirmBeforePhotos(NOW);
        when(bookings.lockManagerBooking(MANAGER, tomorrow.getId())).thenReturn(tomorrow);
        assertThatThrownBy(() -> service.start(MANAGER, tomorrow.getId())).isInstanceOf(ApiException.class);
        verify(livekit, never()).publishToken(anyString(), anyString(), anyString(), any());
    }

    // 상태 응답의 source 로 앱이 휴대폰 카메라 미리보기를 켤지 안다
    @Test
    void 상태_응답에_source_app() {
        // status 는 requireParty(예약 당사자 확인 — bookings.find)를 거친다. setUp 이 이 예약을 돌려주게 해 두었다
        LiveStatusResponse res = service.status(MANAGER, booking.getId());
        assertThat(res.source()).isEqualTo("app");
    }

    // 원격 unmute 가 막힌 프로젝트: 서버의 unmute 호출이 거절돼도, 휴대폰이 스스로 푼 트랙이 확인되면 재개 성공.
    // 일시정지(mute)는 호출이 거절되면 그대로 실패한다 (서버가 막아야 하므로)
    @Test
    void 휴대폰_송출의_재개는_원격_unmute_가_거절돼도_트랙이_풀렸으면_성공_mute_는_거절되면_실패() {
        ManagerDevice phone = ManagerDevice.phone(MANAGER);
        LiveSession s = LiveSession.create(booking.getId());
        s.startWaiting(null, NOW);
        s.cameraAttached("TR1", false, NOW);
        org.mockito.Mockito.doThrow(new ApiException(kr.ansimclean.api.common.error.ErrorCode.UPSTREAM)).when(livekit).mutePublishedTrack(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyBoolean());
        when(livekit.getParticipant(room, "camera-" + MANAGER)).thenReturn(Optional.of(new LiveKitGateway.Participant("camera-" + MANAGER, java.util.List.of(new Track("TR1", true, false)))));
        assertThat(service.setMuted(s, phone, false)).isTrue();
        assertThat(service.setMuted(s, phone, true)).isFalse();
    }

    // ── 웹훅 (LiveWebhookService) ──

    LiveService live = mock(LiveService.class);

    LiveWebhookService webhooks(boolean app) {
        return new LiveWebhookService(sessions, devices, events, livekit, live, bookings, outbox, CLOCK, app ? LiveTestSupport.appProps() : LiveTestSupport.props());
    }

    static WebhookEvent track(String id, String event, String room, String identity, String sid, boolean video) {
        return new WebhookEvent(id, event, room, identity, null, null, new Track(sid, video, false));
    }

    // 매니저 앱이 카메라 트랙을 올리면 바디캠의 ingress_started 처럼 cameraAttached(세션·휴대폰 기기·트랙 sid)
    @Test
    void track_published_는_cameraAttached() {
        ManagerDevice phone = ManagerDevice.phone(MANAGER);
        LiveSession s = LiveSession.create(booking.getId());
        s.startWaiting(null, NOW);
        when(devices.findById(MANAGER)).thenReturn(Optional.of(phone));
        when(sessions.lockByRoomName(room)).thenReturn(Optional.of(s));
        webhooks(true).handle(track("t1", LiveWebhookService.TRACK_PUBLISHED, room, "camera-" + MANAGER, "TR1", true));
        verify(live).cameraAttached(s, phone, "TR1");
    }

    // 바디캠 모드에서는 track_* 를 무시한다(ingress_* 가 같은 일을 한다). 앱 모드여도 오디오·고객·대기 방·담당 아닌 매니저의 트랙은 무시
    @Test
    void track_published_를_무시하는_경우() {
        ManagerDevice phone = ManagerDevice.phone(MANAGER);
        LiveSession s = LiveSession.create(booking.getId());
        s.startWaiting(null, NOW);
        when(devices.findById(MANAGER)).thenReturn(Optional.of(phone));
        when(sessions.lockByRoomName(room)).thenReturn(Optional.of(s));
        webhooks(false).handle(track("a", LiveWebhookService.TRACK_PUBLISHED, room, "camera-" + MANAGER, "TR1", true));
        webhooks(true).handle(track("b", LiveWebhookService.TRACK_PUBLISHED, room, "camera-" + MANAGER, "TR2", false));
        webhooks(true).handle(track("c", LiveWebhookService.TRACK_PUBLISHED, room, "customer-" + CUSTOMER, "TR3", true));
        webhooks(true).handle(track("d", LiveWebhookService.TRACK_PUBLISHED, "parking-" + MANAGER, "camera-" + MANAGER, "TR4", true));
        java.util.UUID other = java.util.UUID.randomUUID();
        when(devices.findById(other)).thenReturn(Optional.of(ManagerDevice.phone(other)));
        webhooks(true).handle(track("e", LiveWebhookService.TRACK_PUBLISHED, room, "camera-" + other, "TR5", true));
        verify(live, never()).cameraAttached(any(), any(), any());
    }

    // 진행 중 작업의 카메라 트랙이 내려가면 카메라 대기로 되돌리고 기기 offline, 매니저에게 '끊겼어요' 푸시
    @Test
    void track_unpublished_는_카메라_대기로_되돌리고_푸시() {
        Booking inProgress = TestBookings.inProgress(CUSTOMER, MANAGER, TODAY, LocalTime.of(10, 0), NOW);
        String r = "booking-" + inProgress.getId();
        ManagerDevice phone = ManagerDevice.phone(MANAGER);
        phone.markOnline(NOW);
        LiveSession s = LiveSession.create(inProgress.getId());
        s.startWaiting(null, NOW);
        s.cameraAttached("TR1", false, NOW);
        when(bookings.find(inProgress.getId())).thenReturn(Optional.of(inProgress));
        when(devices.findById(MANAGER)).thenReturn(Optional.of(phone));
        when(sessions.lockByRoomName(r)).thenReturn(Optional.of(s));
        webhooks(true).handle(track("u1", LiveWebhookService.TRACK_UNPUBLISHED, r, "camera-" + MANAGER, "TR1", true));
        assertThat(s.getStatus()).isEqualTo(LiveSession.WAITING_CAMERA);
        assertThat(phone.isOnline()).isFalse();
        verify(outbox).publish(any(CameraOffline.class));
    }

    // 이미 새 트랙(TR2)으로 다시 붙은 뒤 옛 트랙(TR1)의 늦은 알림이 오면 무시한다
    @Test
    void 옛_트랙의_늦은_track_unpublished_는_무시() {
        Booking inProgress = TestBookings.inProgress(CUSTOMER, MANAGER, TODAY, LocalTime.of(10, 0), NOW);
        String r = "booking-" + inProgress.getId();
        LiveSession s = LiveSession.create(inProgress.getId());
        s.startWaiting(null, NOW);
        s.cameraAttached("TR2", false, NOW);
        when(bookings.find(inProgress.getId())).thenReturn(Optional.of(inProgress));
        when(devices.findById(MANAGER)).thenReturn(Optional.of(ManagerDevice.phone(MANAGER)));
        when(sessions.lockByRoomName(r)).thenReturn(Optional.of(s));
        webhooks(true).handle(track("u2", LiveWebhookService.TRACK_UNPUBLISHED, r, "camera-" + MANAGER, "TR1", true));
        assertThat(s.getStatus()).isEqualTo(LiveSession.LIVE);
        verify(outbox, never()).publish(any());
    }
}
