package kr.ansimclean.api.live;

import kr.ansimclean.api.booking.Booking;
import kr.ansimclean.api.booking.BookingService;
import kr.ansimclean.api.booking.TestBookings;
import kr.ansimclean.api.catalog.AppSettingsService;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.events.CameraOnline;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.supabase.SupabaseStorageClient;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.live.dto.CustomerLiveResponse;
import kr.ansimclean.api.live.dto.MuteResponse;
import kr.ansimclean.api.live.dto.StartLiveResponse;
import kr.ansimclean.api.profile.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static kr.ansimclean.api.live.LiveTestSupport.CUSTOMER;
import static kr.ansimclean.api.live.LiveTestSupport.MANAGER;
import static kr.ansimclean.api.live.LiveTestSupport.NOW;
import static kr.ansimclean.api.live.LiveTestSupport.camera;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 라이브 시작·일시정지(확인 폴링)·재개·정지·고객 토큰·녹화본·고객 화면 (BACKEND_PLAN.md §5.4·§8.1·§8.2.1). LiveKit 은 게이트웨이 mock */
// LiveService 단위 테스트. 저장소·LiveKit·예약/프로필 서비스·설정·Storage·아웃박스는 Mockito 목이고,
// 세션(LiveSession)·예약(Booking)·기기(ManagerDevice)는 실제 객체라 메서드 뒤의 상태 변화를 그대로 확인한다.
// mute 확인 폴링은 LiveTestSupport.props() 설정대로 2번·대기 0ms 라, getParticipant 목이 돌려주는 트랙의 muted 값으로 확인 성공·실패를 정한다.
// 확인 폴링을 흉내 낼 때는 thenReturn(a, b) 로 조회마다 다른 트랙을 준다 (첫 조회는 a, 그 뒤로는 b)
class LiveServiceTest {
    static final AppClock CLOCK = new AppClock(Clock.fixed(NOW, AppClock.ZONE)); // NOW(서울 2026-09-15 14:00)에 멈춘 시계
    static final LocalDate TODAY = LocalDate.of(2026, 9, 15); // 그 시계의 서울 날짜 — '오늘 작업' 판단에 쓴다

    LiveSessionRepository sessions = mock(LiveSessionRepository.class);
    ManagerDeviceRepository devices = mock(ManagerDeviceRepository.class);
    LiveKitGateway livekit = mock(LiveKitGateway.class);
    BookingService bookings = mock(BookingService.class);
    ProfileService profiles = mock(ProfileService.class);
    AppSettingsService settings = mock(AppSettingsService.class);
    SupabaseStorageClient storage = mock(SupabaseStorageClient.class);
    OutboxService outbox = mock(OutboxService.class);
    LiveService service;
    Booking booking;
    ManagerDevice device;
    String room;

    // 기본 준비: 오늘 10:00 에 배정된(scheduled) 예약 한 건과 MANAGER 의 바디캠. 예약 소유권 검사(requireManager/CustomerBooking)는 통과하고,
    // save 는 받은 객체를 그대로 돌려주고, LiveKit 입장 토큰(joinToken — 매니저 미리보기·고객 시청 둘 다)은 "tok",
    // Ingress 방 옮기기는 성공(옮긴 방 이름을 담아 돌려줌)하게 한다
    @BeforeEach
    void setUp() {
        service = new LiveService(sessions, devices, livekit, bookings, profiles, settings, storage, outbox, LiveTestSupport.props(), CLOCK);
        booking = TestBookings.scheduled(CUSTOMER, MANAGER, TODAY, LocalTime.of(10, 0));
        room = "booking-" + booking.getId();
        device = LiveTestSupport.device();
        when(bookings.requireManagerBooking(MANAGER, booking.getId())).thenReturn(booking);
        when(bookings.lockManagerBooking(MANAGER, booking.getId())).thenReturn(booking); // resume 은 예약을 잠그고 읽는다
        when(bookings.requireCustomerBooking(CUSTOMER, booking.getId())).thenReturn(booking);
        when(bookings.find(booking.getId())).thenReturn(Optional.of(booking));
        when(devices.findById(MANAGER)).thenReturn(Optional.of(device));
        when(sessions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(livekit.joinToken(anyString(), anyString(), anyString(), any())).thenReturn("tok");
        when(livekit.url()).thenReturn("wss://lk.example");
        when(livekit.updateIngressRoom(anyString(), anyString())).thenAnswer(inv -> new LiveKitGateway.IngressInfo("ing-1", "rtmp://x", "k", inv.getArgument(1), false));
    }

    // 바디캠이 붙어 영상이 나가는(live, 영상 트랙 T1) 세션을 만들고, findById·lockById 가 이 세션을 돌려주게 한다.
    // 한 테스트에서 다시 부르면 두 조회 모두 새 세션으로 바뀐다
    LiveSession liveSession() {
        LiveSession s = LiveSession.create(booking.getId());
        s.startWaiting("ing-1", NOW);
        s.cameraAttached("T1", false, NOW);
        when(sessions.findById(booking.getId())).thenReturn(Optional.of(s));
        when(sessions.lockById(booking.getId())).thenReturn(Optional.of(s));
        return s;
    }

    // 앞: 세션 행이 없을 때 start → 방 생성(두 시간 값 21600초 = 6시간), 바디캠 Ingress 를 이 방으로, 카메라 대기 상태,
    // 매니저 미리보기 토큰(이름표 "manager-매니저id", 6시간). 뒤: 이미 대기 중인 세션으로 다시 start 하면 방을 또 만들지 않는다
    @Test
    void 시작은_방을_만들고_Ingress_를_묶고_대기_상태와_프리뷰_토큰을_준다_두_번_불러도_방은_한_번() {
        when(sessions.findById(booking.getId())).thenReturn(Optional.empty());
        when(sessions.lockById(booking.getId())).thenReturn(Optional.empty());
        StartLiveResponse res = service.start(MANAGER, booking.getId());
        assertThat(res.roomName()).isEqualTo(room);
        assertThat(res.status()).isEqualTo(LiveSession.WAITING_CAMERA);
        assertThat(res.cameraOnline()).isFalse();
        assertThat(res.cameraBusy()).isFalse();
        assertThat(res.preview().token()).isEqualTo("tok");
        verify(livekit).createRoom(room, 21600, 21600);
        verify(livekit).updateIngressRoom("ing-1", room);
        verify(livekit).joinToken(eq(room), eq("manager-" + MANAGER), anyString(), eq(Duration.ofHours(6)));

        LiveSession s = LiveSession.create(booking.getId());
        s.startWaiting("ing-1", NOW);
        when(sessions.findById(booking.getId())).thenReturn(Optional.of(s));
        when(sessions.lockById(booking.getId())).thenReturn(Optional.of(s));
        service.start(MANAGER, booking.getId());
        verify(livekit, times(1)).createRoom(anyString(), any(Integer.class), any(Integer.class));
    }

    // Ingress 방 옮기기가 '송출 중이라 못 바꿈'(IngressBusyException)을 던지게 한다 → 응답 cameraBusy=true, 기기에는 옮길 방을 적어 둔다
    @Test
    void 바디캠이_대기_방에_송출_중이면_cameraBusy_와_pending_room() {
        when(sessions.findById(booking.getId())).thenReturn(Optional.empty());
        when(sessions.lockById(booking.getId())).thenReturn(Optional.empty());
        when(livekit.updateIngressRoom("ing-1", room)).thenThrow(new LiveKitGateway.IngressBusyException("active"));
        StartLiveResponse res = service.start(MANAGER, booking.getId());
        assertThat(res.cameraBusy()).isTrue();
        assertThat(device.getPendingRoom()).isEqualTo(room);
    }

    @Test
    void 오늘_작업이_아니거나_기기가_없으면_INVALID_STATE() {
        Booking tomorrow = TestBookings.scheduled(CUSTOMER, MANAGER, TODAY.plusDays(1), LocalTime.of(10, 0));
        when(bookings.requireManagerBooking(MANAGER, tomorrow.getId())).thenReturn(tomorrow);
        // assertThatThrownBy(람다) = 람다가 예외를 던져야 통과. extracting("code") = 그 예외 객체의 code 필드 값을 꺼내 비교한다
        assertThatThrownBy(() -> service.start(MANAGER, tomorrow.getId())).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.INVALID_STATE);
        when(devices.findById(MANAGER)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.start(MANAGER, booking.getId())).isInstanceOf(ApiException.class).hasMessage(LiveService.NO_DEVICE);
    }

    // 어제 22:00 작업을 3시간 전에 시작해 지금(오늘 14:00 서울)도 진행 중이고 세션은 live — 방문일이 오늘이 아니어도
    // start 는 거절하지 않고 지금 상태(live)를 돌려주며, 방을 새로 만들지 않는다
    @Test
    void 이미_진행_중인_작업은_자정을_넘겨_다시_불러도_같은_세션에_붙는다() {
        Booking yesterday = TestBookings.scheduled(CUSTOMER, MANAGER, TODAY.minusDays(1), LocalTime.of(22, 0));
        yesterday.start(NOW.minusSeconds(3 * 3600), TODAY.minusDays(1));
        when(bookings.requireManagerBooking(MANAGER, yesterday.getId())).thenReturn(yesterday);
        LiveSession s = LiveSession.create(yesterday.getId());
        s.startWaiting("ing-1", NOW);
        s.cameraAttached("T1", false, NOW);
        when(sessions.findById(yesterday.getId())).thenReturn(Optional.of(s));
        StartLiveResponse res = service.start(MANAGER, yesterday.getId());
        assertThat(res.status()).isEqualTo("live");
        verify(livekit, never()).createRoom(anyString(), anyInt(), anyInt());
    }

    // 확인 폴링 1번째 조회는 아직 안 막힌 트랙, 2번째는 막힌 트랙을 돌려준다 → 2번째에 확인돼 paused.
    // 바로 다시 pause 하면 '이미 확인된 paused' 라 mute 명령을 또 보내지 않는다 (mute(true) 호출은 전체 한 번)
    @Test
    void 일시정지는_mute_뒤_트랙_muted_가_확인돼야_paused() {
        LiveSession s = liveSession();
        when(livekit.getParticipant(room, "camera-" + MANAGER)).thenReturn(Optional.of(camera("T1", false)), Optional.of(camera("T1", true)));
        MuteResponse res = service.pause(MANAGER, booking.getId());
        assertThat(res.status()).isEqualTo(LiveSession.PAUSED);
        assertThat(res.muted()).isTrue();
        assertThat(s.isMutePending()).isFalse();
        verify(livekit).mutePublishedTrack(room, "camera-" + MANAGER, "T1", true);
        assertThat(service.pause(MANAGER, booking.getId()).status()).isEqualTo(LiveSession.PAUSED); // 멱등
        verify(livekit, times(1)).mutePublishedTrack(anyString(), anyString(), anyString(), eq(true));
    }

    @Test
    void 일시정지는_mute_보다_먼저_이미_들어와_있는_고객의_구독을_막고_재개하면_연다() { // 재감사 H-02 — 새 토큰만 막으면 기존 시청자는 재접속한 트랙을 받는다
        LiveSession s = liveSession();
        String viewer = "customer-" + CUSTOMER;
        // 참가자 목록: 일시정지 때는 구독 가능한 고객 + 바디캠, 재개 때는 구독이 막힌 고객 + 막힌 바디캠
        when(livekit.listParticipants(room)).thenReturn(List.of(new LiveKitGateway.Participant(viewer, List.of()), camera("T1", false)),
                List.of(new LiveKitGateway.Participant(viewer, List.of(), false), camera("T1", true))); // 재개 때는 막혀 있는 고객
        when(livekit.getParticipant(room, "camera-" + MANAGER)).thenReturn(Optional.of(camera("T1", true)));
        service.pause(MANAGER, booking.getId());
        // InOrder = 아래 order.verify 들이 이 순서대로 불렸는지 확인한다 (구독 막기 → mute)
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(livekit);
        order.verify(livekit).setCanSubscribe(room, viewer, false);
        order.verify(livekit).mutePublishedTrack(room, "camera-" + MANAGER, "T1", true);
        verify(livekit, never()).setCanSubscribe(room, "camera-" + MANAGER, false); // 바디캠 참가자는 건드리지 않는다
        assertThat(s.isViewersBlocked()).isTrue();

        when(livekit.getParticipant(room, "camera-" + MANAGER)).thenReturn(Optional.of(camera("T1", false)));
        service.resume(MANAGER, booking.getId());
        verify(livekit).setCanSubscribe(room, viewer, true);
        assertThat(s.isViewersBlocked()).isFalse();
    }

    @Test
    void 구독_막기가_실패해도_mute_는_진행하고_재조정기가_참가자별로_다시_막는다() { // 재감사 2차 #1 — 한 명 막기 실패가 영영 남지 않게
        LiveSession s = liveSession();
        String viewer = "customer-" + CUSTOMER;
        when(livekit.listParticipants(room)).thenReturn(List.of(new LiveKitGateway.Participant(viewer, List.of())));
        // 이 고객의 구독 막기: 첫 호출은 LiveKit 오류(UPSTREAM), 두 번째부터는 성공(doNothing)
        org.mockito.Mockito.doThrow(new ApiException(ErrorCode.UPSTREAM, "down")).doNothing().when(livekit).setCanSubscribe(room, viewer, false);
        when(livekit.getParticipant(room, "camera-" + MANAGER)).thenReturn(Optional.of(camera("T1", true)));
        assertThat(service.pause(MANAGER, booking.getId()).status()).isEqualTo(LiveSession.PAUSED); // mute 는 그대로
        assertThat(s.isViewersBlocked()).isTrue(); // 뜻은 적혀 있다

        service.syncViewerAccess(s, List.of(new LiveKitGateway.Participant(viewer, List.of(), true))); // 재조정기: 실제로는 아직 구독 가능
        verify(livekit, times(2)).setCanSubscribe(room, viewer, false);
        service.syncViewerAccess(s, List.of(new LiveKitGateway.Participant(viewer, List.of(), false))); // 이미 막혔으면 부르지 않는다
        verify(livekit, times(2)).setCanSubscribe(room, viewer, false);
    }

    @Test
    void 일시정지_중_카메라가_끊겨도_작업이_일시정지면_구독을_다시_열지_않는다() { // 재감사 2차 #1 — 세션이 waiting_camera 가 되며 '막을 뜻' 이 사라졌다
        booking.start(NOW, TODAY);
        LiveSession s = liveSession();
        String viewer = "customer-" + CUSTOMER;
        when(livekit.getParticipant(room, "camera-" + MANAGER)).thenReturn(Optional.of(camera("T1", true)));
        service.pause(MANAGER, booking.getId());
        booking.pause(); // jobs/pause
        s.cameraDetached(NOW); // 바디캠이 끊김 → waiting_camera
        assertThat(s.getStatus()).isEqualTo(LiveSession.WAITING_CAMERA);
        // 재조정기처럼 참가자 목록(구독이 막힌 고객)을 넘긴다 — 작업이 일시정지라 막을 뜻이 남아 구독을 다시 열지 않는다
        service.syncViewerAccess(s, List.of(new LiveKitGateway.Participant(viewer, List.of(), false)));
        verify(livekit, never()).setCanSubscribe(room, viewer, true);
        assertThat(s.isViewersBlocked()).isTrue();
    }

    @Test
    void 바디캠이_보내는_오디오_트랙은_붙는_순간_mute_하고_영상은_두다() { // 영상만 송출(결정 §13-23) — 명세에만 있고 구현이 없었다
        booking.start(NOW, TODAY);
        LiveSession s = liveSession();
        // 바디캠 참가자가 영상 트랙 T1 과 오디오 트랙 A1(video=false)을 둘 다 올린 상태. 둘 다 아직 막히지 않았다
        LiveKitGateway.Participant cam = new LiveKitGateway.Participant("camera-" + MANAGER, List.of(new LiveKitGateway.Track("T1", true, false), new LiveKitGateway.Track("A1", false, false)));
        when(livekit.getParticipant(room, "camera-" + MANAGER)).thenReturn(Optional.of(cam));
        when(livekit.startRecording(anyString(), anyString())).thenReturn(new LiveKitGateway.EgressInfo("eg-1", room, "active", null, 0, null));
        service.cameraAttached(s, device, "T1");
        verify(livekit).mutePublishedTrack(room, "camera-" + MANAGER, "A1", true);
        assertThat(s.getRecordingStartedAt()).isEqualTo(NOW);
        assertThat(s.isRecordingStartConfirmed()).isFalse(); // 요청 시각을 실제 파일 시작으로 오인하지 않는다.
        verify(livekit, never()).mutePublishedTrack(room, "camera-" + MANAGER, "T1", true);
    }

    // 확인 조회가 계속 '안 막힌 트랙' 을 돌려준다 → 설정된 2번을 다 읽고 실패 → 502(LIVE_PAUSE_UNCONFIRMED), 세션은 live + mute 요청 남김
    @Test
    void mute_를_확인하지_못하면_502_와_mute_pending_상태는_live_그대로() {
        LiveSession s = liveSession();
        when(livekit.getParticipant(room, "camera-" + MANAGER)).thenReturn(Optional.of(camera("T1", false)));
        assertThatThrownBy(() -> service.pause(MANAGER, booking.getId())).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.LIVE_PAUSE_UNCONFIRMED);
        assertThat(s.getStatus()).isEqualTo(LiveSession.LIVE);
        assertThat(s.isMutePending()).isTrue();
        verify(livekit, times(2)).getParticipant(room, "camera-" + MANAGER); // 폴링 2회
    }

    // 카메라 대기 세션(트랙 없음). LiveKit 에 물어도 바디캠 참가자가 없다(getParticipant 목 기본값 = 빈 Optional)
    // → mute 명령 없이 '막아 달라는 요청' 만 남기고 정상 응답
    @Test
    void 카메라가_안_붙었으면_막을_영상이_없다_mute_pending_만_두고_200() {
        LiveSession s = LiveSession.create(booking.getId());
        s.startWaiting("ing-1", NOW);
        when(sessions.findById(booking.getId())).thenReturn(Optional.of(s));
        when(sessions.lockById(booking.getId())).thenReturn(Optional.of(s));
        MuteResponse res = service.pause(MANAGER, booking.getId());
        assertThat(res.status()).isEqualTo(LiveSession.WAITING_CAMERA);
        assertThat(s.isMutePending()).isTrue();
        verify(livekit, never()).mutePublishedTrack(anyString(), anyString(), anyString(), eq(true));
    }

    // 앞: 10분 전부터 paused 인 세션을 재개 — 트랙이 풀린 것이 보여 live. 뒤: 다시 paused 로 만들고 트랙이 계속 막혀 보이게 하면
    // 확인 실패 → 502, 세션은 paused + unmute 요청 남김(재조정기가 이어서 한다)
    @Test
    void 재개는_unmute_확인_뒤_live_확인_못_하면_unmute_pending_과_502() {
        LiveSession s = liveSession();
        s.muted(NOW.minusSeconds(600));
        when(livekit.getParticipant(room, "camera-" + MANAGER)).thenReturn(Optional.of(camera("T1", false)));
        assertThat(service.resume(MANAGER, booking.getId()).status()).isEqualTo(LiveSession.LIVE);
        verify(livekit).mutePublishedTrack(room, "camera-" + MANAGER, "T1", false);

        s.muted(NOW.minusSeconds(600));
        when(livekit.getParticipant(room, "camera-" + MANAGER)).thenReturn(Optional.of(camera("T1", true)));
        assertThatThrownBy(() -> service.resume(MANAGER, booking.getId())).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.LIVE_PAUSE_UNCONFIRMED);
        assertThat(s.getStatus()).isEqualTo(LiveSession.PAUSED);
        assertThat(s.isUnmutePending()).isTrue();
    }

    @Test
    void 작업이_일시정지_중이면_재개를_거부한다_늦은_옛_요청이_영상을_켜지_않게() {
        LiveSession s = liveSession();
        s.muted(NOW.minusSeconds(600));
        booking.start(NOW, TODAY);
        booking.pause();
        assertThatThrownBy(() -> service.resume(MANAGER, booking.getId())).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.INVALID_STATE);
        verify(livekit, never()).mutePublishedTrack(anyString(), anyString(), anyString(), eq(false));
    }

    @Test
    void 확인_조회가_통신_오류로_실패해도_pending_표시는_남는다() {
        // unmute 명령은 카메라에 먹었는데 getParticipant 가 UPSTREAM 을 던지면 — 예외로 빠져나가면 unmute_pending 이 안 남아 다음 pause 가 '이미 paused' 로 통과한다 (외부 검토 3차)
        LiveSession s = liveSession();
        s.muted(NOW.minusSeconds(600));
        when(livekit.getParticipant(room, "camera-" + MANAGER)).thenThrow(new ApiException(ErrorCode.UPSTREAM));
        assertThatThrownBy(() -> service.resume(MANAGER, booking.getId())).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.LIVE_PAUSE_UNCONFIRMED);
        verify(livekit).mutePublishedTrack(room, "camera-" + MANAGER, "T1", false);
        assertThat(s.isUnmutePending()).isTrue();
        assertThat(s.mayBeStreaming()).isTrue(); // 영상이 나갈 수 있다고 본다 → jobs/pause 가 막힌다

        // pause 도 같다: mute 명령 뒤 확인 실패 → live + mute_pending
        // (pause 는 작업 상태와 무관하게 세션만 본다 — 이 테스트의 작업은 시작 전(scheduled) 그대로다)
        assertThatThrownBy(() -> service.pause(MANAGER, booking.getId())).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.LIVE_PAUSE_UNCONFIRMED);
        assertThat(s.isMutePending()).isTrue();
        assertThat(s.getStatus()).isEqualTo(LiveSession.LIVE);
    }

    // 작업이 일시정지인 채 바디캠이 세 번 붙는 상황(웹훅·재조정기가 cameraAttached 를 부른다):
    // ① 트랙 없이 참가자만 → 기기만 online, 세션은 카메라 대기 ② 트랙 T1 인데 mute 확인 실패 → live + mute 요청
    // ③ 다시 붙고 mute 확인 성공 → paused
    @Test
    void 카메라_재접속은_mute_가_확인된_뒤에만_paused_트랙이_없으면_live_가_아니다() {
        booking.start(NOW, TODAY);
        booking.pause();
        LiveSession s = LiveSession.create(booking.getId());
        s.startWaiting("ing-1", NOW);
        when(sessions.findById(booking.getId())).thenReturn(Optional.of(s));
        when(sessions.lockById(booking.getId())).thenReturn(Optional.of(s));

        when(livekit.startRecording(room, booking.getId() + ".mp4")).thenReturn(new LiveKitGateway.EgressInfo("eg-1", room, "active", null, 0, null));
        service.cameraAttached(s, device, null); // 참가자만 있고 트랙이 없다
        assertThat(s.getStatus()).isEqualTo(LiveSession.WAITING_CAMERA);
        assertThat(device.isOnline()).isTrue();

        when(livekit.getParticipant(room, "camera-" + MANAGER)).thenReturn(Optional.of(camera("T1", false))); // mute 확인 실패
        service.cameraAttached(s, device, "T1");
        assertThat(s.getStatus()).isEqualTo(LiveSession.LIVE); // 확인 못 했으면 paused 라고 적지 않는다
        assertThat(s.isMutePending()).isTrue();
        verify(livekit).mutePublishedTrack(room, "camera-" + MANAGER, "T1", true);

        when(livekit.getParticipant(room, "camera-" + MANAGER)).thenReturn(Optional.of(camera("T1", true))); // 확인 성공
        service.cameraAttached(s, device, "T1");
        assertThat(s.getStatus()).isEqualTo(LiveSession.PAUSED);
        assertThat(s.isMutePending()).isFalse();
    }

    @Test
    void 라이브를_시작한_적_없는_작업의_pause_resume_은_200_이다() {
        when(sessions.findById(booking.getId())).thenReturn(Optional.empty());
        when(sessions.lockById(booking.getId())).thenReturn(Optional.empty()); // 바디캠 미등록 등으로 live/start 가 실패한 작업
        assertThat(service.pause(MANAGER, booking.getId()).muted()).isTrue(); // 막을 영상이 없다 — jobs/pause 가 진행된다
        assertThat(service.resume(MANAGER, booking.getId()).muted()).isFalse();
    }

    @Test
    void pause_resume_은_502_를_던져도_pending_표시를_롤백하지_않는다() throws Exception {
        // 클래스 @Transactional 아래에서 ApiException(RuntimeException)은 롤백된다 — noRollbackFor 가 없으면 mute_pending/unmute_pending 이 사라져
        // 다음 pause 가 '이미 paused' 로 거짓 200 을 주고 영상이 나가는 채로 작업이 일시정지된다
        // (이 '이미 paused' 는 resume 쪽 이야기다. pause 쪽은 카메라 대기 세션에서 방금 찾은 트랙·live 표시까지 되돌려져
        //  jobs/pause 가 통과하는 것이 문제다 — LiveService.pause 위 주석)
        // 이 단위 테스트에는 스프링·DB 트랜잭션이 없어 롤백 자체는 볼 수 없다. 그래서 리플렉션(클래스 정보를 코드로 읽기)으로
        // pause(UUID, UUID)·resume(UUID, UUID) 에 붙은 @Transactional 의 noRollbackFor 값에 ApiException 이 들어 있는지 직접 본다
        for (String name : List.of("pause", "resume")) {
            var tx = LiveService.class.getMethod(name, UUID.class, UUID.class).getAnnotation(org.springframework.transaction.annotation.Transactional.class);
            assertThat(tx).isNotNull();
            assertThat(tx.noRollbackFor()).contains(ApiException.class);
        }
    }

    // LiveService.state(예약, 세션) 는 static 이라 service 객체 없이 바로 부른다. 예약 상태가 먼저고, 그다음 세션 상태를 본다
    @Test
    void 고객_화면_state_는_청소_시작_전이면_카메라가_붙어도_waiting() {
        LiveSession s = liveSession(); // scheduled 예약에 live 세션
        assertThat(LiveService.state(booking, s)).isEqualTo("waiting");
        booking.start(NOW, TODAY);
        assertThat(LiveService.state(booking, s)).isEqualTo("live");
        booking.pause();
        s.cameraDetached(NOW); // 쉬는 동안 바디캠을 껐다
        assertThat(LiveService.state(booking, s)).isEqualTo("paused"); // '카메라 연결을 기다리고 있어요' 가 아니다 (자체 점검 #4)
    }

    // 고객이 탈퇴했다고(isDeleted=true) 하면 받은 녹화 파일을 Storage 에서 지우고 true, 세션은 녹화 준비됨이 아니다.
    // 탈퇴하지 않은 고객이면 아무것도 하지 않고 false
    @Test
    void 탈퇴한_고객의_녹화는_받는_즉시_지운다() { // 자체 점검 #3
        LiveSession s = liveSession();
        s.recordingStarted("eg-1");
        when(profiles.isDeleted(CUSTOMER)).thenReturn(true);
        assertThat(service.discardRecordingOfDeletedCustomer(s, CUSTOMER, "x.mp4")).isTrue();
        verify(storage).delete(anyString(), eq("x.mp4"));
        assertThat(s.isRecordingReady()).isFalse();
        when(profiles.isDeleted(CUSTOMER)).thenReturn(false);
        assertThat(service.discardRecordingOfDeletedCustomer(s, CUSTOMER, "y.mp4")).isFalse();
    }

    @Test
    void 재개_확인에_실패한_뒤_다시_일시정지하면_실제로_mute_하고_확인한다() {
        // 재개(unmute) 호출은 먹었는데 확인만 못 한 상태(paused + unmute_pending): '이미 paused' 로 넘기면 영상이 나가는데 일시정지가 된다(§8.2.1 위반)
        LiveSession s = liveSession();
        s.muted(NOW.minusSeconds(600));
        s.requestUnmute(); // paused + unmute_pending: 재개 명령은 보냈지만 확인하지 못한 상태를 만든다
        assertThat(s.mayBeStreaming()).isTrue();
        when(livekit.getParticipant(room, "camera-" + MANAGER)).thenReturn(Optional.of(camera("T1", false)), Optional.of(camera("T1", true)));

        MuteResponse res = service.pause(MANAGER, booking.getId());

        assertThat(res.status()).isEqualTo(LiveSession.PAUSED);
        assertThat(s.isUnmutePending()).isFalse();
        assertThat(s.mayBeStreaming()).isFalse();
        verify(livekit).mutePublishedTrack(room, "camera-" + MANAGER, "T1", true);

        s.requestUnmute(); // 이번엔 mute 확인 실패 → 502 + mute_pending (재조정기가 잇는다)
        when(livekit.getParticipant(room, "camera-" + MANAGER)).thenReturn(Optional.of(camera("T1", false)));
        assertThatThrownBy(() -> service.pause(MANAGER, booking.getId())).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.LIVE_PAUSE_UNCONFIRMED);
        assertThat(s.isMutePending()).isTrue();
        assertThat(s.isUnmutePending()).isFalse();
        assertThat(s.mayBeStreaming()).isTrue(); // unmute 는 먹었고 mute 는 확인 못 했다 — paused+mute_pending 이면 jobs/pause 가 통과해 영상이 나가는 채 '일시정지' 가 된다
        assertThat(s.getStatus()).isEqualTo(LiveSession.LIVE);
        assertThatThrownBy(() -> service.pause(MANAGER, booking.getId())).isInstanceOf(ApiException.class); // 다시 눌러도 '이미 paused' 200 이 아니라 실제로 mute 를 시도한다
        verify(livekit, org.mockito.Mockito.times(3)).mutePublishedTrack(room, "camera-" + MANAGER, "T1", true);
    }

    @Test
    void 끝난_작업의_정리는_다음_작업_방에_묶인_바디캠이나_다음_작업이_쓰는_잠금을_건드리지_않는다() { // 자체 점검 #1·#51
        LiveSession s = liveSession();
        booking.start(NOW, TODAY);
        booking.complete(NOW);
        // 앞: 바디캠 Ingress 가 이미 다음 작업 방("booking-next")에 묶여 송출 중이라고 LiveKit 이 답한다
        when(livekit.getIngress("ing-1")).thenReturn(Optional.of(new LiveKitGateway.IngressInfo("ing-1", "rtmp://x", "k", "booking-next", true)));
        service.stop(MANAGER, booking.getId());
        verify(livekit, never()).updateIngressRoom("ing-1", "parking-" + MANAGER); // 다음 작업 방에 묶여 있다 — 대기 방으로 빼지 않는다
        assertThat(s.getStatus()).isEqualTo(LiveSession.ENDED);

        LiveSession s2 = liveSession(); // 같은 예약을 다시 정리하는 경우(재조정기)를 흉내 — 다음 작업이 진행 중
        // 뒤: 바디캠이 잠겨 있고, 같은 매니저의 진행 중 작업(activeJob)이 이 예약이 아닌 다음 작업이다
        device.setIngressEnabled(false);
        Booking next = TestBookings.inProgress(CUSTOMER, MANAGER, TODAY, LocalTime.of(14, 0), NOW);
        when(bookings.activeJob(MANAGER)).thenReturn(Optional.of(next));
        service.stopSession(s2, device);
        verify(livekit, never()).setIngressEnabled("ing-1", true); // 다음 작업의 일시정지 잠금을 풀지 않는다
    }

    @Test
    void 세션은_카메라_대기인데_바디캠이_이미_영상을_올리고_있으면_일시정지가_mute_한다() { // 자체 점검 #2 — 재접속 웹훅 때 트랙이 없어 waiting_camera 로 남은 동안
        booking.start(NOW, TODAY);
        LiveSession s = liveSession();
        s.cameraDetached(NOW); // waiting_camera, 트랙 sid 없음
        // 1번째 조회(일시정지 전 카메라 확인)에 새 트랙 T2 가 보이고, 2번째 조회(mute 확인)에서는 막혀 있다
        when(livekit.getParticipant(room, "camera-" + MANAGER)).thenReturn(Optional.of(camera("T2", false)), Optional.of(camera("T2", true)));
        MuteResponse res = service.pause(MANAGER, booking.getId());
        verify(livekit).mutePublishedTrack(room, "camera-" + MANAGER, "T2", true);
        assertThat(res.status()).isEqualTo(LiveSession.PAUSED);
    }

    @Test
    void 일시정지_전_카메라_확인이_실패하면_막을_영상이_없다고_보지_않고_502() { // 자체 점검 #2
        booking.start(NOW, TODAY);
        LiveSession s = liveSession();
        s.cameraDetached(NOW);
        when(livekit.getParticipant(room, "camera-" + MANAGER)).thenThrow(new ApiException(ErrorCode.UPSTREAM, "down"));
        assertThatThrownBy(() -> service.pause(MANAGER, booking.getId())).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.LIVE_PAUSE_UNCONFIRMED);
        assertThat(s.isMutePending()).isTrue();
        assertThat(s.mayBeStreaming()).isTrue(); // jobs/pause 가 통과하지 못한다
    }

    // 녹화 eg-1 이 도는 live 세션. 작업이 scheduled·진행 중이면 stop 은 409(INVALID_STATE), 완료 뒤에는 녹화 정지(→ 업로드 중)·방 삭제·
    // 이 방에 묶인 바디캠을 대기 방으로 옮긴 뒤 ended(끝난 시각 NOW). 한 번 더 불러도 방 삭제는 한 번뿐이다
    @Test
    void 정지는_녹화_정지_방_삭제_Ingress_대기_방_복귀_뒤_ended() {
        LiveSession s = liveSession();
        s.recordingStarted("eg-1");
        assertThatThrownBy(() -> service.stop(MANAGER, booking.getId())).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.INVALID_STATE); // scheduled 도 못 끊는다(재시작 세션은 녹화가 없다)
        booking.start(NOW, TODAY);
        assertThatThrownBy(() -> service.stop(MANAGER, booking.getId())).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.INVALID_STATE); // 진행 중엔 못 끊는다
        booking.complete(NOW);
        when(livekit.getIngress("ing-1")).thenReturn(Optional.of(new LiveKitGateway.IngressInfo("ing-1", "rtmp://x", "k", room, false))); // 이 방에 묶여 있다
        service.stop(MANAGER, booking.getId());
        verify(livekit).stopEgress("eg-1");
        verify(livekit).deleteRoom(room);
        verify(livekit).updateIngressRoom("ing-1", "parking-" + MANAGER);
        assertThat(s.getStatus()).isEqualTo(LiveSession.ENDED);
        assertThat(s.getRecordingStatus()).isEqualTo(LiveSession.REC_UPLOADING);
        assertThat(s.getEndedAt()).isEqualTo(NOW);
        service.stop(MANAGER, booking.getId()); // 멱등
        verify(livekit, times(1)).deleteRoom(room);
    }

    // 작업 전(scheduled)이면 거절 → 청소 시작 + live 세션이면 토큰(이름표 "customer-고객id", 2시간) → 세션이 끝나면 다시 거절
    @Test
    void 고객_토큰은_진행_중_예약과_열린_세션에서만() {
        assertThatThrownBy(() -> service.viewerToken(CUSTOMER, booking.getId())).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.LIVE_NOT_AVAILABLE); // scheduled
        booking.start(NOW, TODAY);
        LiveSession s = liveSession();
        assertThat(service.viewerToken(CUSTOMER, booking.getId()).roomName()).isEqualTo(room);
        verify(livekit).joinToken(eq(room), eq("customer-" + CUSTOMER), anyString(), eq(Duration.ofHours(2)));
        s.end(NOW);
        assertThatThrownBy(() -> service.viewerToken(CUSTOMER, booking.getId())).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.LIVE_NOT_AVAILABLE);
    }

    // 두 경우 모두 LIVE_NOT_AVAILABLE 이고 LiveKit 토큰을 만들지 않는다: ① mute 요청이 확인되지 않은 동안 ② 작업이 일시정지인 동안
    @Test
    void 일시정지_및_mute_미확인_동안_새_시청_토큰을_주지_않는다() {
        booking.start(NOW, TODAY);
        LiveSession s = liveSession();
        s.requestMute();
        assertThatThrownBy(() -> service.viewerToken(CUSTOMER, booking.getId()))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.LIVE_NOT_AVAILABLE);
        s.clearMuteRequests();
        booking.pause();
        assertThatThrownBy(() -> service.viewerToken(CUSTOMER, booking.getId()))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.LIVE_NOT_AVAILABLE);
        verify(livekit, never()).joinToken(anyString(), anyString(), anyString(), any());
    }

    // 일시정지 작업에 바디캠이 붙었는데 mute 확인이 실패해 live + mute 요청이 남은 세션. 붙음 처리 때도,
    // 재조정기처럼 ensureRecording 을 직접 불러도 녹화를 시작하지 않는다 (녹화 상태 none 그대로)
    @Test
    void 재조정기도_mute_미확인_세션의_녹화를_시작할_수_없다() {
        booking.start(NOW, TODAY);
        booking.pause();
        LiveSession s = liveSession();
        when(livekit.getParticipant(room, "camera-" + MANAGER)).thenReturn(Optional.of(camera("T1", false)));
        when(livekit.startRecording(anyString(), anyString())).thenReturn(new LiveKitGateway.EgressInfo("eg-1", room, "active", null, 0, null));
        service.cameraAttached(s, device, "T1");
        // LiveReconciler가 다음 주기에 공통 녹화 함수를 다시 호출하는 경로.
        service.ensureRecording(s);
        verify(livekit, never()).startRecording(anyString(), anyString());
        assertThat(s.isMutePending()).isTrue();
        assertThat(s.getRecordingStatus()).isEqualTo(LiveSession.REC_NONE);
    }

    // 녹화가 준비되기 전엔 RECORDING_NOT_READY. ready 가 되면 버킷 "recordings" 의 그 파일로 서명 URL(유효 시간 RECORDING_URL_TTL_SECONDS)과 길이(초)를 준다
    @Test
    void 녹화본은_ready_일_때만_서명_URL() {
        LiveSession s = liveSession();
        assertThatThrownBy(() -> service.recording(CUSTOMER, booking.getId())).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.RECORDING_NOT_READY);
        s.recordingReady(booking.getId() + ".mp4", 630);
        when(storage.signedUrl("recordings", booking.getId() + ".mp4", LiveService.RECORDING_URL_TTL_SECONDS)).thenReturn("https://signed");
        assertThat(service.recording(CUSTOMER, booking.getId()).url()).isEqualTo("https://signed");
        assertThat(service.recording(CUSTOMER, booking.getId()).durationSeconds()).isEqualTo(630);
    }

    // ready 로 적혀 있는데 Storage 에 파일이 없으면(서명 404 → 중립 NOT_FOUND) 녹화본 쪽에서만 RECORDING_NOT_READY 로 바꾼다 —
    // 고객 앱 getRecording 이 이 코드를 '아직 없음'(null) 으로 읽는다. 그 밖의 Storage 실패(UPSTREAM)는 그대로 나간다 (문제 목록 21번)
    @Test
    void 녹화본_파일이_Storage_에_없으면_RECORDING_NOT_READY() {
        LiveSession s = liveSession();
        s.recordingReady(booking.getId() + ".mp4", 630);
        when(storage.signedUrl("recordings", booking.getId() + ".mp4", LiveService.RECORDING_URL_TTL_SECONDS)).thenThrow(new ApiException(ErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> service.recording(CUSTOMER, booking.getId())).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.RECORDING_NOT_READY);
        org.mockito.Mockito.reset(storage);
        when(storage.signedUrl("recordings", booking.getId() + ".mp4", LiveService.RECORDING_URL_TTL_SECONDS)).thenThrow(new ApiException(ErrorCode.UPSTREAM));
        assertThatThrownBy(() -> service.recording(CUSTOMER, booking.getId())).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.UPSTREAM);
    }

    // 준비: app_settings 의 상태별 문구·안심 문구 JSON 과 매니저 정보(김미영, 2024-03-01 부터 활동, 완료 142건)를 목으로 준다.
    // 같은 예약을 청소 전 → 진행 중 → 완료(녹화 ready) → 녹화 failed 로 바꿔 가며 고객 라이브 화면 응답을 확인한다.
    // 경력은 2024-03-01 부터 오늘(2026-09-15)까지 만 2년, 녹화 길이 630초 = "10:30"
    @Test
    void 고객_화면은_상태별_문구와_매니저_소개_녹화_길이를_만든다() {
        JsonMapper json = JsonMapper.builder().build(); // 문자열 JSON 을 JsonNode(트리)로 읽는 Jackson 도구
        when(settings.node(LiveService.SETTING_STATUS_COPY)).thenReturn(json.readTree("{\"waiting\":{\"title\":\"카메라 연결을 기다리고 있어요\",\"description\":\"w\"},\"live\":{\"title\":\"거실 청소 중\",\"description\":\"l\"},\"ended\":{\"title\":\"청소가 끝났어요\",\"description\":\"e\"},\"ended_ready\":{\"title\":\"청소가 끝났어요\",\"description\":\"ready\"},\"ended_failed\":{\"title\":\"청소가 끝났어요\",\"description\":\"failed\"}}"));
        when(settings.node(LiveService.SETTING_REASSURANCE)).thenReturn(json.readTree("{\"title\":\"안심하고 지켜보세요!\",\"description\":\"r\"}"));
        when(profiles.managerIdentity(MANAGER)).thenReturn(new ProfileService.ManagerIdentity("김미영", LocalDate.of(2024, 3, 1), 142));

        when(sessions.findById(booking.getId())).thenReturn(Optional.empty());
        when(sessions.lockById(booking.getId())).thenReturn(Optional.empty());
        CustomerLiveResponse waiting = service.customerLive(CUSTOMER, booking.getId());
        assertThat(waiting.live().state()).isEqualTo("waiting");
        assertThat(waiting.live().available()).isFalse();
        assertThat(waiting.status().title()).isEqualTo("카메라 연결을 기다리고 있어요");
        assertThat(waiting.manager().name()).isEqualTo("김미영 매니저");
        assertThat(waiting.manager().description()).isEqualTo("경력 2년 · 완료 청소 142건");
        assertThat(waiting.startedAt()).isEqualTo("");

        booking.start(NOW, TODAY);
        LiveSession s = liveSession();
        CustomerLiveResponse live = service.customerLive(CUSTOMER, booking.getId());
        assertThat(live.live().state()).isEqualTo("live");
        assertThat(live.live().available()).isTrue();
        assertThat(live.startedAt()).isEqualTo("2026. 09. 15. 14:00"); // 청소 시작 시각 NOW 를 서울 시간으로 적은 문자열

        booking.complete(NOW);
        s.recordingReady("x.mp4", 630);
        CustomerLiveResponse ended = service.customerLive(CUSTOMER, booking.getId());
        assertThat(ended.live().state()).isEqualTo("ended");
        assertThat(ended.live().recordingReady()).isTrue();
        assertThat(ended.live().recording()).isEqualTo("ready");
        assertThat(ended.status().description()).isEqualTo("ready"); // 녹화본 상태별 문구 (V014)
        assertThat(ended.playback().totalLabel()).isEqualTo("10:30");
        assertThat(ended.reassurance().title()).isEqualTo("안심하고 지켜보세요!");

        s.recordingFailed();
        CustomerLiveResponse failed = service.customerLive(CUSTOMER, booking.getId());
        assertThat(failed.live().recording()).isEqualTo("failed");
        assertThat(failed.live().recordingPending()).isFalse();
        assertThat(failed.status().description()).isEqualTo("failed");
    }

    @Test
    void 카메라가_붙으면_대기_중이던_mute_를_적용하고_녹화를_시작하고_고객에게_알린다() {
        booking.start(NOW, TODAY);
        booking.pause(); // 작업이 일시정지 — 붙는 카메라는 막아야 한다
        LiveSession s = LiveSession.create(booking.getId());
        s.startWaiting("ing-1", NOW);
        s.requestMute();
        when(livekit.startRecording(room, booking.getId() + ".mp4")).thenReturn(new LiveKitGateway.EgressInfo("eg-1", room, "active", null, 0, null));
        when(livekit.getParticipant(room, "camera-" + MANAGER)).thenReturn(Optional.of(camera("T1", true))); // mute 확인

        service.cameraAttached(s, device, "T1");

        // mute 확인 → paused, 확인됐으니 녹화 시작(eg-1), 기기 online, 이 세션에서 처음 붙었으므로 CameraOnline(first=true)을 아웃박스에
        assertThat(s.getStatus()).isEqualTo(LiveSession.PAUSED);
        assertThat(s.isMutePending()).isFalse();
        verify(livekit).mutePublishedTrack(room, "camera-" + MANAGER, "T1", true);
        assertThat(s.getEgressId()).isEqualTo("eg-1");
        assertThat(s.getRecordingStatus()).isEqualTo(LiveSession.REC_RECORDING);
        assertThat(device.isOnline()).isTrue();
        verify(outbox).publish(new CameraOnline(booking.getId(), CUSTOMER, true));
    }

    // ── 일시정지 중 재접속 차단 (감사 H-02) ──

    // 카메라 대기 세션에서 pause → LiveKit 에 Ingress 잠금(setIngressEnabled false)을 보내고 기기에도 잠김으로 적는다.
    // 작업을 진행 중으로 바꾼 뒤 resume → 잠금을 풀고 기기도 열림으로
    @Test
    void 카메라가_없을_때_일시정지하면_Ingress_를_잠가_재접속을_막고_재개하면_연다() {
        LiveSession s = LiveSession.create(booking.getId());
        s.startWaiting("ing-1", NOW);
        when(sessions.findById(booking.getId())).thenReturn(Optional.of(s));
        when(sessions.lockById(booking.getId())).thenReturn(Optional.of(s));
        service.pause(MANAGER, booking.getId());
        verify(livekit).setIngressEnabled("ing-1", false);
        assertThat(device.isIngressEnabled()).isFalse();

        booking.start(NOW, TODAY); // 실제 흐름처럼 작업을 진행 중으로 둔다 (resume 은 작업이 paused 일 때만 거절한다)
        service.resume(MANAGER, booking.getId());
        verify(livekit).setIngressEnabled("ing-1", true);
        assertThat(device.isIngressEnabled()).isTrue();
    }

    // 잠금 요청이 '송출 중'(IngressBusyException)으로 거절되면 lockIngress 는 false 를 돌려주고 기기는 열린 채로 둔다.
    // 영상이 나가는 세션의 pause 는 잠금과 상관없이 mute 확인만으로 paused 가 된다
    @Test
    void 송출_중이라_잠글_수_없으면_열린_채로_두고_일시정지는_mute_확인으로_끝난다() {
        LiveSession s = liveSession();
        org.mockito.Mockito.doThrow(new LiveKitGateway.IngressBusyException("busy")).when(livekit).setIngressEnabled("ing-1", false);
        assertThat(service.lockIngress(device)).isFalse();
        assertThat(device.isIngressEnabled()).isTrue(); // 끊기는 순간 ingress_ended·재조정기가 다시 잠근다
        when(livekit.getParticipant(room, "camera-" + MANAGER)).thenReturn(Optional.of(camera("T1", true)));
        assertThat(service.pause(MANAGER, booking.getId()).status()).isEqualTo(LiveSession.PAUSED);
    }

    @Test
    void 일시정지_중_재접속한_트랙의_mute_를_확인하지_못하면_녹화를_새로_시작하지_않는다() {
        booking.start(NOW, TODAY);
        booking.pause();
        LiveSession s = LiveSession.create(booking.getId());
        s.startWaiting("ing-1", NOW);
        when(livekit.getParticipant(room, "camera-" + MANAGER)).thenReturn(Optional.of(camera("T1", false)));
        service.cameraAttached(s, device, "T1");
        assertThat(s.isMutePending()).isTrue();
        verify(livekit, never()).startRecording(anyString(), anyString());
    }

    // 잠긴 바디캠 + 같은 매니저의 다른 진행 중 작업 없음(activeJob 목 기본값 = 빈 Optional) → 세션 정리 때 잠금을 푼다
    @Test
    void 작업이_끝나면_잠근_Ingress_를_연다_다음_작업에서_카메라가_붙게() {
        LiveSession s = liveSession();
        device.setIngressEnabled(false);
        service.stopSession(s, device);
        verify(livekit).setIngressEnabled("ing-1", true);
        assertThat(device.isIngressEnabled()).isTrue();
    }

    // ── 매니저가 멈춘 영상은 재개할 때까지 막는다 (videoHeld, 2026-09-28 사용자 결정) ──

    // 영상 일시정지 ① 성공 → 작업 일시정지 ② 실패(작업은 진행 중) → 바디캠이 끊겼다 다시 붙음(새 트랙 T2).
    // 예전에는 재접속 처리가 작업 상태만 보고 T2 를 막지 않아 영상과 고객 시청이 다시 열렸다. 이제 매니저가 재개할 때까지 막고,
    // 재개(live/resume)한 뒤의 재접속은 평소대로 내보낸다
    @Test
    void 영상만_막고_작업_일시정지가_실패한_채_바디캠이_다시_붙어도_매니저가_재개할_때까지_막는다() {
        booking.start(NOW, TODAY);
        LiveSession s = liveSession();
        when(livekit.startRecording(anyString(), anyString())).thenReturn(new LiveKitGateway.EgressInfo("eg-1", room, "active", null, 0, null));
        when(livekit.getParticipant(room, "camera-" + MANAGER)).thenReturn(Optional.of(camera("T1", true)));
        service.pause(MANAGER, booking.getId()); // ① 성공 — 매니저는 '영상은 중단됐다' 는 답을 받았다. ② jobs/pause 는 실패해 작업은 진행 중
        assertThat(s.isVideoHeld()).isTrue();

        s.cameraDetached(NOW); // 바디캠 끊김 → 세션은 카메라 대기
        when(livekit.getParticipant(room, "camera-" + MANAGER)).thenReturn(Optional.of(camera("T2", true))); // 새 트랙 mute 확인 성공
        service.cameraAttached(s, device, "T2");

        verify(livekit).mutePublishedTrack(room, "camera-" + MANAGER, "T2", true);
        assertThat(s.getStatus()).isEqualTo(LiveSession.PAUSED);
        assertThat(service.shouldBlockViewers(s)).isTrue(); // 고객 구독도 막힌 채
        assertThat(LiveService.state(booking, s)).isEqualTo("paused"); // 고객 화면도 '일시정지'

        when(livekit.getParticipant(room, "camera-" + MANAGER)).thenReturn(Optional.of(camera("T2", false)));
        service.resume(MANAGER, booking.getId()); // 매니저가 재개(시트 닫기 → live/resume)
        assertThat(s.isVideoHeld()).isFalse();
        assertThat(s.getStatus()).isEqualTo(LiveSession.LIVE);

        s.cameraDetached(NOW);
        service.cameraAttached(s, device, "T3"); // 재개한 뒤의 재접속 — 막지 않는다
        verify(livekit, never()).mutePublishedTrack(room, "camera-" + MANAGER, "T3", true);
        assertThat(s.getStatus()).isEqualTo(LiveSession.LIVE);
    }

    // 영상 일시정지가 502(확인 실패)로 끝나면 매니저는 '영상이 계속 전송되고 있다' 는 안내를 받았다 — 막아 둘 뜻이 없다.
    // 이 경우 다시 붙은 새 트랙은 예전처럼 내보낸다 (남은 mute 요청은 버린다)
    @Test
    void 영상_일시정지가_502_로_실패했으면_재접속한_트랙을_막아_두지_않는다() {
        booking.start(NOW, TODAY);
        LiveSession s = liveSession();
        when(livekit.startRecording(anyString(), anyString())).thenReturn(new LiveKitGateway.EgressInfo("eg-1", room, "active", null, 0, null));
        when(livekit.getParticipant(room, "camera-" + MANAGER)).thenReturn(Optional.of(camera("T1", false))); // 끝까지 막히지 않는다
        assertThatThrownBy(() -> service.pause(MANAGER, booking.getId())).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.LIVE_PAUSE_UNCONFIRMED);
        assertThat(s.isVideoHeld()).isFalse();

        s.cameraDetached(NOW);
        service.cameraAttached(s, device, "T2");
        verify(livekit, never()).mutePublishedTrack(room, "camera-" + MANAGER, "T2", true);
        assertThat(s.getStatus()).isEqualTo(LiveSession.LIVE);
        assertThat(s.isMutePending()).isFalse();
    }

    // 바디캠이 안 붙은 채 ① 이 200('mute 요청' 만 남김) → ② 실패 → 그 뒤 바디캠이 처음 붙는다. 예전에는 붙는 순간 요청을 버리고 내보냈다
    @Test
    void 카메라_없이_영상을_멈춘_뒤_처음_붙는_바디캠도_재개_전까지_막는다() {
        booking.start(NOW, TODAY);
        LiveSession s = LiveSession.create(booking.getId());
        s.startWaiting("ing-1", NOW);
        when(sessions.findById(booking.getId())).thenReturn(Optional.of(s));
        when(sessions.lockById(booking.getId())).thenReturn(Optional.of(s));
        when(livekit.startRecording(anyString(), anyString())).thenReturn(new LiveKitGateway.EgressInfo("eg-1", room, "active", null, 0, null));

        assertThat(service.pause(MANAGER, booking.getId()).muted()).isTrue(); // LiveKit 에도 카메라가 없다 — mute 요청만 남기고 200
        assertThat(s.isVideoHeld()).isTrue();

        when(livekit.getParticipant(room, "camera-" + MANAGER)).thenReturn(Optional.of(camera("T1", true)));
        service.cameraAttached(s, device, "T1");
        verify(livekit).mutePublishedTrack(room, "camera-" + MANAGER, "T1", true);
        assertThat(s.getStatus()).isEqualTo(LiveSession.PAUSED);
    }

    // 재개 요청이 작업이 아직 일시정지라 거절되면(늦게 온 옛 resume) 멈춘 표시를 풀지 않는다
    @Test
    void 작업이_아직_일시정지라_거절된_재개는_멈춘_표시를_풀지_않는다() {
        booking.start(NOW, TODAY);
        LiveSession s = liveSession();
        when(livekit.getParticipant(room, "camera-" + MANAGER)).thenReturn(Optional.of(camera("T1", true)));
        service.pause(MANAGER, booking.getId());
        booking.pause(); // ② 성공
        assertThatThrownBy(() -> service.resume(MANAGER, booking.getId())).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.INVALID_STATE);
        assertThat(s.isVideoHeld()).isTrue();
    }

    // 매니저가 멈춘 채 바디캠이 끊겨 카메라 대기가 된 동안: 고객 화면은 '일시정지', 구독은 막힌 채, 새 시청 토큰도 주지 않는다.
    // (예전에는 이 창에서 상태가 '대기' 가 되고 재조정기가 구독을 다시 열었다)
    @Test
    void 매니저가_멈춘_채_카메라가_끊긴_동안에도_일시정지로_보이고_구독과_새_토큰을_막는다() {
        booking.start(NOW, TODAY);
        LiveSession s = liveSession();
        when(livekit.getParticipant(room, "camera-" + MANAGER)).thenReturn(Optional.of(camera("T1", true)));
        service.pause(MANAGER, booking.getId());
        s.cameraDetached(NOW);
        assertThat(s.getStatus()).isEqualTo(LiveSession.WAITING_CAMERA);

        assertThat(LiveService.state(booking, s)).isEqualTo("paused");
        assertThat(service.shouldBlockViewers(s)).isTrue();
        String viewer = "customer-" + CUSTOMER;
        when(livekit.listParticipants(room)).thenReturn(List.of(new LiveKitGateway.Participant(viewer, List.of(), false)));
        service.syncViewerAccess(s);
        verify(livekit, never()).setCanSubscribe(room, viewer, true); // 막아 둔 고객을 다시 열지 않는다
        assertThatThrownBy(() -> service.viewerToken(CUSTOMER, booking.getId())).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.LIVE_NOT_AVAILABLE);
        assertThat(service.status(MANAGER, booking.getId()).held()).isTrue(); // 매니저 앱이 '작업 상태를 바꾸지 못했어요' 시트를 다시 띄우는 근거
    }

    // 첫 시도는 '멈췄다'(200) → 다시 시도에서 확인 실패(502, '계속 전송' 안내) → 막아 둔 표시를 지운다. 매니저가 마지막으로 받은 안내와 서버를 맞춘다
    @Test
    void 영상_일시정지가_다시_시도에서_502_로_끝나면_막아_둔_표시를_지운다() {
        booking.start(NOW, TODAY);
        LiveSession s = liveSession();
        when(livekit.getParticipant(room, "camera-" + MANAGER)).thenReturn(Optional.of(camera("T1", true)));
        service.pause(MANAGER, booking.getId());
        assertThat(s.isVideoHeld()).isTrue();

        s.cameraDetached(NOW);
        s.cameraAttached("T2", false, NOW); // 다시 붙었는데 막았는지 아직 모른다 (cameraAttached 의 확인 실패와 같은 모양)
        s.requestMute();
        when(livekit.getParticipant(room, "camera-" + MANAGER)).thenReturn(Optional.of(camera("T2", false)));
        assertThatThrownBy(() -> service.pause(MANAGER, booking.getId())).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.LIVE_PAUSE_UNCONFIRMED);
        assertThat(s.isVideoHeld()).isFalse();
    }

    // 재개는 예약을 먼저 잠그고 읽는다 — 동시에 처리 중인 jobs/pause 가 커밋한 뒤의 상태로 판단하게 (JobService.pause 와 같은 예약 → 세션 순서)
    @Test
    void 재개는_예약을_잠그고_읽는다() {
        booking.start(NOW, TODAY);
        liveSession();
        service.resume(MANAGER, booking.getId());
        verify(bookings).lockManagerBooking(MANAGER, booking.getId());
        verify(bookings, never()).requireManagerBooking(MANAGER, booking.getId());
    }

    // 매니저가 멈춘 채면 라이브를 다시 시작(앱 재진입)해도 일시정지 잠금을 풀지 않는다
    @Test
    void 매니저가_멈춘_채면_라이브를_다시_시작해도_잠금을_풀지_않는다() {
        booking.start(NOW, TODAY);
        LiveSession s = liveSession();
        s.holdVideo();
        service.start(MANAGER, booking.getId());
        verify(livekit, never()).setIngressEnabled("ing-1", true);
    }
}
