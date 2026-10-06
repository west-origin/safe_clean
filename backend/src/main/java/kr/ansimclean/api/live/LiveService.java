package kr.ansimclean.api.live;

import kr.ansimclean.api.booking.Booking;
import kr.ansimclean.api.booking.BookingService;
import kr.ansimclean.api.booking.BookingStatus;
import kr.ansimclean.api.catalog.AppSettingsService;
import kr.ansimclean.api.common.config.AppProperties;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.events.CameraOnline;
import kr.ansimclean.api.common.format.KoreanFormat;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.supabase.SupabaseStorageClient;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.live.dto.CustomerLiveResponse;
import kr.ansimclean.api.live.dto.LiveStatusResponse;
import kr.ansimclean.api.live.dto.MuteResponse;
import kr.ansimclean.api.live.dto.OkResponse;
import kr.ansimclean.api.live.dto.RecordingResponse;
import kr.ansimclean.api.live.dto.StartLiveResponse;
import kr.ansimclean.api.live.dto.TokenResponse;
import kr.ansimclean.api.profile.ProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 라이브 세션의 사용자 동작 (BACKEND_PLAN.md §5.4·§8.1·§8.2.1): 시작·상태·일시정지·재개·정지·고객 토큰·녹화본·고객 화면.
 * LiveKit 호출은 LiveKitGateway 로만. 일시정지/재개는 트랙 muted 를 **확인한 뒤에만 200** — 확인 못 하면 pending 표시 + LiveReconciler 가 재시도.
 * 매니저 앱은 송출하지 않는다(영상은 바디캠 → Ingress). 프리뷰·고객 토큰은 모두 시청 전용.
 * 예외: 막을(다시 켤) 영상 트랙이 없으면 확인 없이 200 — pause 는 mute 요청을 남기고 Ingress 잠금을 시도하며, resume 은 요청 표시를 지우고 끝낸다.
 * public 메서드는 앱의 라이브 API(LiveController)가, public 이 없는 메서드는 같은 live 패키지의 웹훅·재조정기 등(예: LiveWebhookService,
 * LiveReconciler)이 부른다 — public 이 없는 메서드는 같은 패키지에서만 부를 수 있다.
 * 용어: 방(room) = 영상이 오가는 채널(예약 하나에 하나), Ingress = 바디캠 영상 입구, Egress = 녹화, mute = 서버가 트랙을 막는 것.
 * '막아야 함' 의 기준은 하나다 — holdsVideo = 작업이 일시정지 ∨ 매니저가 영상을 멈추고 아직 재개하지 않음(video_held).
 * video_held 는 live/pause 가 '멈췄다'(200) 고 답할 때 켜고, 502('계속 전송') 로 답하거나 매니저가 재개(live/resume·jobs/resume)하면 끈다.
 * 재조정기가 pending 을 이어받는 방식: unmute_pending 은 막을 필요가 없으면 다시 unmute 한다. mute_pending 은 막아야 하면 다시
 * mute 하고, 아니면(live/pause 502 뒤 jobs/pause 까지 못 간 보통의 경우) 요청을 버린다 — mute 가 실제로 먹었으면 다시 켠다.
 * 앱이 그때 '영상이 계속 전송되고 있다' 고 안내하기 때문이다 (LiveReconciler.reconcileOne).
 */
// 트랜잭션(CODE_GUIDE.md 3-3) — 이 클래스의 특이점:
// ① pause·resume 은 noRollbackFor = ApiException 이라, 확인 실패(502)를 알리면서도 mute_pending·unmute_pending 표시는 저장한다
//   — 표시가 남아 있는 동안 새 시청 토큰·새 녹화·jobs/pause 가 막히고, 재조정기가 이어받는다(방식은 위 클래스 설명).
// ② 클래스의 @Transactional 은 public 이 아닌(패키지) 메서드에도 걸린다(다른 클래스에서 부를 때). 웹훅·재조정기가 연 묶음 안에서
//   불리면 그 묶음에 합쳐지므로, 여기서 예외가 나가면 그쪽 묶음 전체가 되돌려진다. 그래서 그쪽이 부르는 메서드는 대부분 LiveKit 실패를
//   던지지 않고 로그만 남긴다(예: bindIngressQuietly, setViewerAccess). 일부러 던지는 곳도 있다(예: discardRecordingOfDeletedCustomer — 되돌려 다시 부르게).
@Service
@Transactional
public class LiveService {
    // 방을 만들 때 LiveKit 에 주는 두 시간 값(초, 6시간): 아무도 안 들어올 때·마지막 참가자가 나간 뒤 방을 열어 두는 시간
    static final int ROOM_TIMEOUT_SECONDS = 6 * 60 * 60;
    static final Duration PREVIEW_TTL = Duration.ofHours(6); // 매니저 미리보기 토큰의 유효 시간
    static final Duration VIEWER_TTL = Duration.ofHours(2); // 고객 시청 토큰의 유효 시간
    static final int RECORDING_URL_TTL_SECONDS = 6 * 3600; // 청소 3~4시간 녹화본을 한 번에 완주해도 재발급이 없게 (1시간이면 모든 완주 시청이 끊겼다 — 검증 검토 10 수정판). 앱은 만료 시 재발급
    // app_settings 테이블의 키 — 고객 라이브 화면의 상태별 문구와 안심 문구 (customerLive 가 읽는다)
    static final String SETTING_STATUS_COPY = "live_status_copy";
    static final String SETTING_REASSURANCE = "live_reassurance";
    static final String AVATAR_KEY = "managerAvatar"; // 매니저 사진 대신 내려주는 앱 에셋 이름
    static final String NO_DEVICE = "바디캠이 등록되지 않았어요. 운영자에게 문의해주세요"; // 매니저에게 바디캠(manager_devices 행)이 없을 때
    private static final Logger log = LoggerFactory.getLogger(LiveService.class);

    private final LiveSessionRepository sessions;
    private final ManagerDeviceRepository devices;
    private final LiveKitGateway livekit;
    private final BookingService bookings;
    private final ProfileService profiles;
    private final AppSettingsService settings;
    private final SupabaseStorageClient storage;
    private final OutboxService outbox;
    private final AppProperties props;
    private final AppClock clock;

    public LiveService(LiveSessionRepository sessions, ManagerDeviceRepository devices, LiveKitGateway livekit, BookingService bookings, ProfileService profiles,
                       AppSettingsService settings, SupabaseStorageClient storage, OutboxService outbox, AppProperties props, AppClock clock) {
        this.sessions = sessions;
        this.devices = devices;
        this.livekit = livekit;
        this.bookings = bookings;
        this.profiles = profiles;
        this.settings = settings;
        this.storage = storage;
        this.outbox = outbox;
        this.props = props;
        this.clock = clock;
    }

    // ── 매니저 ──

    /** §8.1 작업 시작: 방 생성 → Ingress 를 이 방에 → waiting_camera → 프리뷰 토큰. 같은 세션에 다시 불러도 같다(폴링 재시도 겸) */
    // 부르는 곳: 매니저 앱 api/manager.js 의 startLiveSession (POST /live/sessions/{id}/start). 결과 cameraBusy 는 아래 bindIngress 참고
    public StartLiveResponse start(UUID managerId, UUID bookingId) {
        Booking b = bookings.requireManagerBooking(managerId, bookingId); // 이 매니저에게 배정된 작업이 아니면 404
        boolean active = BookingStatus.ACTIVE.contains(b.getStatus()); // ACTIVE = 진행 중(in_progress)·일시정지(paused)
        // 날짜 조건은 '아직 시작 전' 인 작업에만 — 이미 진행 중인 작업은 자정을 넘겨 앱을 다시 열어도 같은 세션에 다시 붙어야 한다 (검수 3차)
        if (!active && !(b.getStatus() == BookingStatus.SCHEDULED && b.getServiceDate().equals(clock.today()))) {
            throw new ApiException(ErrorCode.INVALID_STATE, "오늘 진행하는 작업에서만 라이브를 시작할 수 있어요");
        }
        ManagerDevice device = devices.findById(managerId).orElseThrow(() -> new ApiException(ErrorCode.INVALID_STATE, NO_DEVICE));
        LiveSession s = sessions.findById(bookingId).orElseGet(() -> sessions.save(LiveSession.create(bookingId))); // 세션 행이 없으면 지금 만든다
        if (s.isEnded() && b.getStatus() == BookingStatus.DONE) throw new ApiException(ErrorCode.INVALID_STATE, "끝난 작업이에요");
        // 세션이 시작 전(created)이거나 끝난(ended) 상태일 때만 방을 만든다. 이미 대기·진행·일시정지면 같은 방을 그대로 쓴다
        if (!s.isActive()) {
            livekit.createRoom(s.getRoomName(), ROOM_TIMEOUT_SECONDS, ROOM_TIMEOUT_SECONDS);
            s.startWaiting(device.getIngressId(), clock.now());
        }
        // 바디캠 Ingress 를 이 방으로 옮긴다. 다른 방에서 송출 중이라 못 옮기면 busy — 앱이 '바디캠을 껐다 켜 달라' 고 안내한다(StartLiveResponse)
        boolean busy = !bindIngress(device, s.getRoomName());
        if (!holdsVideo(b, s)) unlockIngress(device); // 지난 작업의 일시정지 잠금이 남았으면 연다 — 안 열면 카메라가 붙지 못한다 (지금 멈춰 있으면 그대로 둔다)
        // 매니저가 자기 바디캠 화면을 보는 시청 전용 토큰 (참가자 이름표 'manager-매니저id')
        String token = livekit.joinToken(s.getRoomName(), "manager-" + managerId, "매니저 미리보기", PREVIEW_TTL);
        return new StartLiveResponse(s.getRoomName(), s.getStatus(), s.isCameraOnline(), busy, new StartLiveResponse.Preview(livekit.url(), token));
    }

    /** Ingress 를 방에 묶는다. 송출 중이면 pending_room 에 적어 두고 false (ingress_ended 때 적용, §8.1) */
    // 바디캠 Ingress 가 없거나 LiveKit 오류면 ApiException 을 던진다 — 웹훅·재조정기는 던지지 않는 bindIngressQuietly 를 쓴다
    boolean bindIngress(ManagerDevice device, String roomName) {
        if (device.getIngressId() == null) throw new ApiException(ErrorCode.INVALID_STATE, NO_DEVICE);
        try {
            livekit.updateIngressRoom(device.getIngressId(), roomName);
            device.clearPendingRoom(); // 미뤄 둔 방 변경이 있었다면 이제 필요 없다
            return true;
        } catch (LiveKitGateway.IngressBusyException e) {
            device.setPendingRoom(roomName);
            return false;
        }
    }

    /** 재조정기·웹훅용: 실패해도 던지지 않는다 — 이 메서드는 프록시를 거치므로 안에서 던지면 참여 트랜잭션이 rollback-only 가 돼 배치 전체가 UnexpectedRollbackException 으로 끊긴다 (검수 3차) */
    boolean bindIngressQuietly(ManagerDevice device, String roomName) {
        try {
            return bindIngress(device, roomName);
        } catch (ApiException e) {
            log.warn("Ingress 방 변경 실패 {} → {}: {} (다음 재조정 때 다시)", device.getIngressId(), roomName, e.getMessage());
            return false;
        }
    }

    /** 매니저(3초 폴링)·고객 둘 다. 남의 예약은 404 */
    // 부르는 곳: 매니저 앱 api/manager.js 의 getLiveSessionStatus (GET /live/sessions/{id})
    @Transactional(readOnly = true)
    public LiveStatusResponse status(UUID userId, UUID bookingId) {
        Booking b = requireParty(userId, bookingId);
        Optional<LiveSession> s = sessions.findById(bookingId);
        // 배정 매니저의 바디캠. 매니저가 아직 없으면 부른 사람(그때는 고객뿐이다) id 로 찾는다 — 기기가 없으면 busy 는 false
        Optional<ManagerDevice> device = devices.findById(b.getManagerId() == null ? userId : b.getManagerId());
        String status = s.map(LiveSession::getStatus).orElse(LiveSession.CREATED);
        // busy(cameraBusy): 바디캠이 다른 방에서 송출 중이라, 이 방으로 옮기는 일이 pending_room 에 미뤄져 있다
        boolean busy = device.map(d -> d.getPendingRoom() != null && s.isPresent() && d.getPendingRoom().equals(s.get().getRoomName())).orElse(false);
        return new LiveStatusResponse(status, s.map(LiveSession::isCameraOnline).orElse(false), LiveSession.PAUSED.equals(status), busy,
                s.map(LiveSession::getViewerCount).orElse(0), s.map(LiveSession::getStartedAt).map(Instant::toString).orElse(null),
                s.map(LiveSession::isVideoHeld).orElse(false));
    }

    /**
     * 일시정지 ① (§8.2.1): 영상만 막는다. live 면 mute → 확인 폴링 → 확인돼야 200. 확인 못 하면 mute_pending + 502.
     * 카메라가 안 붙었으면 막을 영상이 없다 → mute_pending 만 두고 200. 이미 paused 면 200.
     * 단, paused 여도 unmute_pending(재개 확인 실패)이면 unmute 가 카메라에 먹었을 수 있으므로 다시 mute 하고 확인한다.
     * 부르는 곳: 매니저 앱 api/manager.js 의 pauseLiveSession. 영상이 나갈 수 있는 동안(mayBeStreaming)은 JobService 가 jobs/pause 를 거부하므로 이것이 먼저다.
     */
    // noRollbackFor: 롤백되면 카메라 대기였던 세션에서 방금 찾은 트랙·live 표시까지 사라져, 영상이 나가는데도 jobs/pause 가 통과할 수 있다
    @Transactional(noRollbackFor = ApiException.class) // 502(확인 실패)를 던져도 mute_pending 은 저장돼야 재조정기가 잇는다
    public MuteResponse pause(UUID managerId, UUID bookingId) {
        bookings.requireManagerBooking(managerId, bookingId);
        LiveSession s = sessions.lockById(bookingId).orElse(null); // 웹훅·재조정기와 같은 행을 다투므로 잠그고 읽는다(확인 폴링 동안 유지)
        if (s == null) return new MuteResponse(LiveSession.CREATED, true); // 라이브를 시작한 적이 없다(바디캠 미등록 등) — 막을 영상이 없으니 작업 일시정지는 진행된다
        if (LiveSession.PAUSED.equals(s.getStatus()) && !s.isUnmutePending()) return held(s); // 이미 막힌 것이 확인됐다 — 다시 할 일이 없다
        if (!s.mayBeStreaming() || s.getCameraTrackSid() == null) adoptPublishedTrack(s, managerId); // '카메라 없음' 이 사실인지 LiveKit 에 한 번 묻는다
        if (LiveSession.PAUSED.equals(s.getStatus()) && !s.isUnmutePending()) return held(s); // 붙어 보니 이미 mute
        // LiveKit 에 물어봐도 영상 트랙이 없다 — 막을 영상이 없으니 'mute 요청' 만 남기고 200.
        // 매니저에게 '멈췄다' 고 답하므로 video_held 도 남긴다 — jobs/pause 가 실패한 채 카메라가 붙어도 cameraAttached 가 막는다
        if (!s.mayBeStreaming() || s.getCameraTrackSid() == null) {
            s.requestMute();
            syncViewerAccess(s); // 붙는 순간의 새 트랙이 이미 있는 고객에게 흘러가지 않게 (재감사 H-02)
            devices.findById(managerId).ifPresent(this::lockIngress); // 카메라가 안 붙어 있다 — 일시정지 동안 붙지 못하게 잠근다 (감사 H-02). 실패는 재조정기가
            return held(s);
        }
        ManagerDevice device = devices.findById(managerId).orElseThrow(() -> new ApiException(ErrorCode.INVALID_STATE, NO_DEVICE));
        setViewerAccess(s, false); // mute 보다 먼저 — 확인 폴링 동안·카메라 재접속 때도 고객에게 새 프레임이 가지 않게 (재감사 H-02). 실패해도 mute 가 1차 방어
        // mute 명령을 보내고 트랙이 정말 막혔는지 몇 번 다시 읽어 확인한다 — 그동안 세션 행 잠금을 쥐고 있다
        if (setMuted(s, device, true)) {
            s.muted(clock.now());
            // 지금은 송출 중이라 잠글 수 없다(LiveKit 이 거부). 카메라가 끊기는 순간(ingress_ended·재조정기) 잠가 재접속을 막는다 (감사 H-02)
            return held(s);
        }
        s.muteUnconfirmed(); // unmute 가 이미 먹었을 수 있다 — live 로 두어 다음 pause·jobs/pause 가 '이미 paused' 로 통과하지 않게
        s.releaseVideo(); // 앱은 '영상이 계속 전송되고 있다' 고 안내한다 — 앞선 시도의 '멈췄다' 를 이어 막아 두지 않는다 (재조정기가 옛 mute 요청을 버린다)
        throw new ApiException(ErrorCode.LIVE_PAUSE_UNCONFIRMED); // 502. noRollbackFor 덕분에 위의 mute_pending 은 저장된다
    }

    /**
     * 세션은 '카메라 없음'(waiting_camera·트랙 sid 없음)인데 바디캠이 이미 영상을 올리고 있을 수 있다 — 재접속 때 웹훅이 오는 순간엔 트랙이 없어
     * cameraAttached 가 건너뛰고 재조정기(최대 1분)를 기다리는 동안. 그대로 '막을 영상 없음' 200 을 주면 작업은 일시정지인데 영상이 나갔다 (자체 점검 #2).
     * 트랙이 보이면 세션에 붙여 보통의 mute 경로를 타게 한다. 조회가 실패하면 모르는 채로 200 을 주지 않는다 — live+mute_pending 으로 두고 502
     */
    private void adoptPublishedTrack(LiveSession s, UUID managerId) {
        ManagerDevice device = devices.findById(managerId).orElse(null);
        if (device == null) return;
        Optional<LiveKitGateway.Track> track;
        try {
            // 방에서 바디캠 참가자('camera-매니저id')의 영상 트랙을 찾는다. 참가자나 트랙이 없으면 빈 값
            track = livekit.getParticipant(s.getRoomName(), device.cameraIdentity()).flatMap(LiveKitGateway.Participant::videoTrack);
        } catch (ApiException e) {
            log.warn("일시정지 전 카메라 확인 실패 {}: {}", s.getRoomName(), e.getMessage());
            setViewerAccess(s, false);
            s.muteUnconfirmed();
            s.releaseVideo(); // 502 = '계속 전송' 안내 — 막아 둔 표시를 남기지 않는다 (위 pause 의 확인 실패와 같다)
            throw new ApiException(ErrorCode.LIVE_PAUSE_UNCONFIRMED);
        }
        // muted 는 LiveKit 이 알려 준 실제 값이다 — 트랙이 이미 막혀 있으면 세션이 바로 paused 가 된다
        track.ifPresent(t -> s.cameraAttached(t.sid(), t.muted(), clock.now()));
    }

    /** 재개 ② (§8.2.1): unmute → 확인 → live. 확인 못 하면 unmute_pending + 502(재조정기가 1분마다 재시도). 카메라가 없으면 표시만 지운다 */
    // 부르는 곳: 매니저 앱 api/manager.js 의 resumeLiveSession. 작업 재개(jobs/resume)가 먼저 끝나 있어야 한다
    @Transactional(noRollbackFor = ApiException.class) // unmute 는 이미 카메라에 먹었을 수 있다 — unmute_pending 이 저장돼야 다음 pause 가 실제로 mute 한다
    public MuteResponse resume(UUID managerId, UUID bookingId) {
        // 예약을 먼저 잠그고(JobService.pause 와 같은 예약 → 세션 순서) 읽는다 — 동시에 처리 중인 jobs/pause 가 커밋한 뒤의 상태를 보려고.
        // 잠그지 않고 읽으면 옛 '진행 중' 을 믿고 일시정지된 작업의 영상을 켤 수 있었다
        Booking b = bookings.lockManagerBooking(managerId, bookingId);
        if (b.getStatus() == BookingStatus.PAUSED) throw new ApiException(ErrorCode.INVALID_STATE, "작업을 먼저 재개해주세요"); // 늦게 도착한 옛 resume 이 일시정지 중 영상을 켜지 않게 (jobs/pause 의 STOP_VIDEO_FIRST 와 대칭)
        LiveSession s = sessions.lockById(bookingId).orElse(null); // pause 와 같이 행을 잠그고 읽는다
        devices.findById(managerId).ifPresent(this::unlockIngress); // 일시정지 중 잠근 Ingress 를 연다 — 끊겼던 바디캠이 다시 붙을 수 있게 (감사 H-02). 실패는 재조정기가
        if (s == null) return new MuteResponse(LiveSession.CREATED, false);
        s.releaseVideo(); // 매니저가 재개를 눌렀다 — 이제부터는 작업 상태를 따른다 (확인에 실패해 502 로 끝나도 뜻은 재개다)
        // 이미 live — 영상이 나가는 중이다. 남은 요청 표시만 지우고 고객 구독을 맞춘다
        if (s.isStreaming()) {
            s.clearMuteRequests(); // 카메라 재접속 때 확인 못 한 mute 요청이 남아 있으면 재조정기가 지울 때까지 고객 시청·새 녹화가 막힌다
            syncViewerAccess(s);
            return new MuteResponse(s.getStatus(), false);
        }
        // paused 가 아니거나(카메라 대기 등) 막아 둔 트랙이 없다 — unmute 할 대상이 없으니 표시만 지운다
        if (!LiveSession.PAUSED.equals(s.getStatus()) || s.getCameraTrackSid() == null) {
            s.clearMuteRequests();
            if (LiveSession.WAITING_CAMERA.equals(s.getStatus())) s.restartCameraWait(clock.now()); // 5분 '바디캠이 붙지 않음' 알림은 재개한 때부터 잰다 (자체 점검 #4)
            syncViewerAccess(s);
            return new MuteResponse(s.getStatus(), false);
        }
        ManagerDevice device = devices.findById(managerId).orElseThrow(() -> new ApiException(ErrorCode.INVALID_STATE, NO_DEVICE));
        // paused + 트랙 있음: unmute 명령을 보내고 트랙이 정말 풀렸는지 확인한다
        if (setMuted(s, device, false)) {
            s.unmuted();
            syncViewerAccess(s); // 영상이 다시 나간 뒤에 고객 구독을 연다
            return new MuteResponse(s.getStatus(), false);
        }
        s.requestUnmute(); // 확인 실패 — unmute_pending 을 남겨 재조정기가 이어서 확인·재시도한다
        syncViewerAccess(s); // 재개 요청은 매니저 뜻 — 확인만 못 했어도 고객을 막아 두지 않는다(트랙이 아직 mute 면 검은 화면)
        throw new ApiException(ErrorCode.LIVE_PAUSE_UNCONFIRMED, "영상을 다시 시작하지 못했어요. 잠시 후 다시 시도해주세요");
    }

    /** mute/unmute 를 부르고 트랙 상태를 폴링해 확인한다 (app.livekit.mute-confirm-*) */
    // 결과: 트랙이 원하는 값(muted)으로 바뀐 것을 봤으면 true, 명령 실패·끝까지 확인 못 함이면 false (던지지 않는다).
    // 쉬는 동안에도 호출한 쪽의 트랜잭션·세션 행 잠금이 유지된다 — 확인하는 동안 같은 세션의 웹훅·jobs/pause 는 기다린다.
    // 예: pause·resume, 재조정기(LiveReconciler.retryMute)
    boolean setMuted(LiveSession s, ManagerDevice device, boolean muted) {
        try {
            livekit.mutePublishedTrack(s.getRoomName(), device.cameraIdentity(), s.getCameraTrackSid(), muted);
        } catch (ApiException e) {
            log.warn("mute 호출 실패 {}: {}", s.getRoomName(), e.getMessage());
            return false;
        }
        int attempts = props.livekit().muteConfirmAttempts();
        for (int i = 0; i < attempts; i++) {
            try {
                Optional<LiveKitGateway.Track> track = livekit.getParticipant(s.getRoomName(), device.cameraIdentity()).flatMap(LiveKitGateway.Participant::videoTrack);
                if (track.isPresent() && track.get().muted() == muted) return true;
            } catch (ApiException e) {
                // 확인 조회가 실패했다 — mute/unmute 명령은 이미 카메라에 먹었을 수 있다. 예외로 빠져나가면 호출자가 pending 표시를 못 남겨
                // '서버는 일시정지인데 영상은 나가는' 상태가 된다(외부 검토 3차). 미확인으로 돌려 호출자가 pending 을 남기게 한다
                log.warn("mute 확인 조회 실패 {} ({}회): {}", s.getRoomName(), i + 1, e.getMessage());
            }
            sleep(props.livekit().muteConfirmSleepMs());
        }
        return false;
    }

    /** 완료 뒤: 녹화 정지 → 방 삭제 → Ingress 를 대기 방으로 → ended. 이미 끝났으면 200. 재조정기도 같은 걸 부른다.
     *  진행 중(in_progress/paused)인 작업의 세션은 끊지 않는다 — 다시 start 해도 녹화가 이어지지 않는다(앱은 jobs/complete 뒤에 부른다) */
    // 부르는 곳: 매니저 앱 api/manager.js 의 stopLiveSession. 세션이 없으면 할 일 없이 200
    public OkResponse stop(UUID managerId, UUID bookingId) {
        Booking b = bookings.requireManagerBooking(managerId, bookingId);
        if (b.getStatus() != BookingStatus.DONE && b.getStatus() != BookingStatus.CANCELLED) throw new ApiException(ErrorCode.INVALID_STATE, "청소를 먼저 완료해주세요"); // scheduled 세션을 끊으면 재시작 뒤 녹화가 없다
        sessions.lockById(bookingId).ifPresent(s -> stopSession(s, devices.findById(managerId).orElse(null)));
        return new OkResponse(true);
    }

    /**
     * 세션 하나를 끝낸다: 녹화 중이면 녹화(Egress) 정지 → 방 삭제 → 바디캠 정리(releaseDevice) → ended. 이미 ended 면 아무것도 안 한다.
     * LiveKit 호출 실패(ApiException)는 던지지 않고 로그만 남긴다 — 재조정기가 끝난 세션의 방 삭제·잠금 해제(끝난 뒤 15분 동안)와
     * recording/uploading 으로 남은 녹화를 다시 본다. 부르는 곳: stop, LiveReconciler(작업이 done·cancelled 인데 세션이 안 끝났을 때). device 는 null 일 수 있다
     */
    void stopSession(LiveSession s, ManagerDevice device) {
        if (s.isEnded()) return;
        if (s.getEgressId() != null && LiveSession.REC_RECORDING.equals(s.getRecordingStatus())) {
            try {
                livekit.stopEgress(s.getEgressId());
                s.recordingStopping(); // recording → uploading. 파일이 다 올라가면 egress 웹훅(또는 재조정기)이 ready/failed 로 적는다
            } catch (ApiException e) {
                log.warn("녹화 정지 실패 {} — 재조정기가 다시 본다", s.getRoomName());
            }
        }
        try {
            livekit.deleteRoom(s.getRoomName());
        } catch (ApiException e) {
            log.warn("방 삭제 실패 {} (empty timeout 으로 사라진다)", s.getRoomName());
        }
        releaseDevice(s, device);
        s.end(clock.now());
    }

    /**
     * 끝난 작업의 바디캠을 놓아 준다: 잠금을 풀고, 이 방에 묶여 있으면 대기 방으로. 같은 매니저의 다음 작업이 이미 쓰고 있으면 아무것도 건드리지 않는다 —
     * 늦게 돈 정리가 다음 작업의 일시정지 잠금을 풀거나(자체 점검 #1) 다음 작업 방에 묶인 바디캠을 대기 방으로 빼던 것(#51). 재조정기의 끝난 방 재확인도 이걸 쓴다
     * (재조정기의 끝난 방 재확인은 이 메서드를 부르지 않고 같은 검사 usedByAnotherJob 만 쓴다 — 대기 방으로 옮기지는 않고 잠금 해제·미뤄 둔 방 적용만 한다)
     */
    void releaseDevice(LiveSession s, ManagerDevice device) {
        if (device == null || device.getIngressId() == null || usedByAnotherJob(device, s.getBookingId())) return;
        unlockIngress(device); // 다음 작업에서 카메라가 붙을 수 있게
        if (!boundTo(device, s.getRoomName())) return;
        try {
            bindIngress(device, device.parkingRoom()); // 대기 방 = 'parking-매니저id'. 송출 중이면 bindIngress 가 pending_room 에 적어 둔다
        } catch (ApiException e) {
            // LiveKit 오류 — 대기 방으로 옮길 일을 적어 두면 ingress_ended 웹훅·재조정기가 나중에 적용한다
            device.setPendingRoom(device.parkingRoom());
        }
    }

    /** 같은 매니저의 다른 진행 중(in_progress/paused) 작업이 있는가 */
    boolean usedByAnotherJob(ManagerDevice device, UUID bookingId) {
        return bookings.activeJob(device.getManagerId()).filter(j -> !j.getId().equals(bookingId)).isPresent();
    }

    /** 바디캠이 이 방에 묶여 있는가(대기 중인 바인딩 포함). 조회가 실패하면 모른다 → false(남의 바인딩을 덮어쓰지 않는다) */
    boolean boundTo(ManagerDevice device, String roomName) {
        if (roomName.equals(device.getPendingRoom())) return true;
        if (device.getPendingRoom() != null) return false; // 다른 방으로 옮기는 중
        try {
            return livekit.getIngress(device.getIngressId()).map(i -> roomName.equals(i.roomName())).orElse(false);
        } catch (ApiException e) {
            log.warn("바디캠 바인딩 조회 실패 {}: {}", device.getIngressId(), e.getMessage());
            return false;
        }
    }

    // ── 고객 ──

    /** 시청 토큰: 자기 예약이 진행 중이고 영상 중단 요청이 없을 때만. 기존 참가자·토큰 폐기는 별도 미디어 계층 조치가 필요하다. */
    // 부르는 곳: 고객 앱 api/customer.js 의 getLiveViewerToken (POST /live/sessions/{id}/token)
    public TokenResponse viewerToken(UUID customerId, UUID bookingId) {
        Booking b = bookings.requireCustomerBooking(customerId, bookingId); // 남의 예약이면 404
        LiveSession s = sessions.findById(bookingId).orElse(null);
        // 주는 조건: 작업이 in_progress(일시정지 아님) + 방이 열려 있음 + 영상이 paused 아님 + 매니저가 멈춘 채가 아님(video_held) + mute/unmute 확인 대기 없음
        if (b.getStatus() != BookingStatus.IN_PROGRESS || s == null || !s.isActive() || s.isVideoHeld()
                || LiveSession.PAUSED.equals(s.getStatus()) || s.isMutePending() || s.isUnmutePending()) {
            throw new ApiException(ErrorCode.LIVE_NOT_AVAILABLE);
        }
        // 참가자 이름표 'customer-고객id' — 웹훅·재조정기는 이 접두사(LiveWebhookService.VIEWER_PREFIX)로 고객 시청자를 가려낸다
        String token = livekit.joinToken(s.getRoomName(), "customer-" + customerId, "고객", VIEWER_TTL);
        return new TokenResponse(livekit.url(), token, s.getRoomName());
    }

    /** 녹화본 서명 URL (6시간, RECORDING_URL_TTL_SECONDS). 준비 전이거나 Storage 에 파일이 없으면 RECORDING_NOT_READY */
    // 부르는 곳: 고객 앱 api/customer.js 의 getRecording (RECORDING_NOT_READY 를 받으면 null 로 바꾼다).
    // 서명 URL = 비공개 Storage 버킷의 녹화본 파일을 정해진 시간 동안만 열 수 있는 주소
    @Transactional(readOnly = true)
    public RecordingResponse recording(UUID customerId, UUID bookingId) {
        bookings.requireCustomerBooking(customerId, bookingId);
        LiveSession s = sessions.findById(bookingId).orElseThrow(() -> new ApiException(ErrorCode.RECORDING_NOT_READY));
        if (!s.isRecordingReady() || s.getRecordingPath() == null) throw new ApiException(ErrorCode.RECORDING_NOT_READY);
        String url;
        try {
            url = storage.signedUrl(props.storage().recordingsBucket(), s.getRecordingPath(), RECORDING_URL_TTL_SECONDS);
        } catch (ApiException e) {
            // Storage 함수는 중립 NOT_FOUND 를 던진다 — 녹화본에서는 '녹화 영상이 아직 준비되지 않았어요' 로 바꿔 앱이 '아직 없음' 으로 읽게 한다.
            // 그 밖의 실패(UPSTREAM '잠시 후 다시 시도해주세요')는 그대로 나간다
            if (e.code() == ErrorCode.NOT_FOUND) throw new ApiException(ErrorCode.RECORDING_NOT_READY);
            throw e;
        }
        return new RecordingResponse(url, s.getDurationSeconds() == null ? 0 : s.getDurationSeconds());
    }

    /** GET /customer/bookings/{id}/live (§5.2) */
    // 고객 라이브 화면을 그릴 값 한 묶음: 매니저 소개·작업 시작 시각(예약의 startedAt)·시청자 수·상태 문구·안심 문구·재생 길이·상태.
    // 부르는 곳: 고객 앱 api/customer.js 의 getLiveSession — live/liveClient.js 가 화면에 들어올 때와 30초마다 다시 받는다
    @Transactional(readOnly = true)
    public CustomerLiveResponse customerLive(UUID customerId, UUID bookingId) {
        Booking b = bookings.requireCustomerBooking(customerId, bookingId);
        Optional<LiveSession> s = sessions.findById(bookingId);
        String state = state(b, s.orElse(null));
        String recording = recordingState(s.orElse(null));
        // app_settings 의 JSON 문구 묶음. 상태 이름을 키로 { title, description } 를 꺼낸다 (path: 키가 없어도 null 대신 빈 노드)
        JsonNode copies = settings.node(SETTING_STATUS_COPY);
        JsonNode copy = "ended".equals(state) && copies.has("ended_" + recording) ? copies.path("ended_" + recording) : copies.path(state); // 끝난 뒤엔 녹화본 상태별 문구(V014)
        JsonNode reassurance = settings.node(SETTING_REASSURANCE);
        CustomerLiveResponse.Manager manager = b.getManagerId() == null ? new CustomerLiveResponse.Manager("", "", AVATAR_KEY) : manager(b.getManagerId()); // 매니저 배정 전이면 빈 소개
        boolean ready = s.map(LiveSession::isRecordingReady).orElse(false);
        long total = ready && s.get().getDurationSeconds() != null ? s.get().getDurationSeconds() : 0; // 녹화본 길이(초). 준비 전이면 0
        // 재생 칸은 현재 위치 0:00·비율 0 으로 두고 총 길이만 채운다 (재생 중 위치는 앱이 계산한다)
        return new CustomerLiveResponse(manager,
                b.getStartedAt() == null ? "" : KoreanFormat.liveStartedAt(b.getStartedAt()),
                s.map(LiveSession::getViewerCount).orElse(0),
                new CustomerLiveResponse.Copy(copy.path("title").asString(), copy.path("description").asString()),
                new CustomerLiveResponse.Copy(reassurance.path("title").asString(), reassurance.path("description").asString()),
                new CustomerLiveResponse.Playback(KoreanFormat.mmss(0), KoreanFormat.mmss(total), 0),
                new CustomerLiveResponse.Live(state, "live".equals(state), ready, s.map(LiveSession::isRecordingPending).orElse(false), recording));
    }

    /** none | pending | ready | failed */
    // 고객 화면용 녹화본 상태. pending = 녹화 중·업로드 중·방은 열렸는데 녹화 시작 전 (LiveSession.isRecordingPending)
    static String recordingState(LiveSession s) {
        if (s == null) return "none";
        if (s.isRecordingReady()) return "ready";
        if (LiveSession.REC_FAILED.equals(s.getRecordingStatus())) return "failed";
        return s.isRecordingPending() ? "pending" : "none";
    }

    /** live/pause 가 '멈췄다' 고 답한다 — 매니저가 재개할 때까지 작업 상태와 관계없이 막아 두라는 표시를 함께 남긴다 */
    private static MuteResponse held(LiveSession s) {
        s.holdVideo();
        return new MuteResponse(s.getStatus(), true);
    }

    /**
     * 영상을 막아 둬야 하는가: 작업이 일시정지거나, 매니저가 영상을 멈추고 아직 재개하지 않았다(작업 일시정지가 실패한 경우 포함).
     * 재접속(cameraAttached)·재조정기·웹훅·Ingress 잠금·고객 상태 문구가 모두 이 기준을 쓴다 — 한 곳이라도 작업 상태만 보면 영상이 다시 나간다
     */
    static boolean holdsVideo(Booking b, LiveSession s) {
        return b.getStatus() == BookingStatus.PAUSED || (s != null && s.isVideoHeld());
    }

    /** waiting | live | paused | ended */
    // 고객 화면용 라이브 상태. 예약 상태를 먼저 보고, 작업이 in_progress 일 때만 세션 상태를 본다 (default 는 created·waiting_camera)
    static String state(Booking b, LiveSession s) {
        if (b.getStatus() == BookingStatus.DONE || b.getStatus() == BookingStatus.CANCELLED) return "ended";
        if (!BookingStatus.ACTIVE.contains(b.getStatus())) return "waiting"; // 청소 시작 전에는 카메라가 붙어 있어도 시청 불가(viewerToken 과 같은 기준)
        if (holdsVideo(b, s)) return "paused"; // 매니저가 일부러 쉬는 중(작업 일시정지, 또는 영상만 멈추고 아직 재개 안 함) — 바디캠을 꺼 세션이 waiting_camera 여도 '카메라 연결을 기다리고 있어요' 로 오해하지 않게 (자체 점검 #4)
        if (s == null) return "waiting";
        return switch (s.getStatus()) {
            case LiveSession.LIVE -> "live";
            case LiveSession.PAUSED -> "paused";
            case LiveSession.ENDED -> "ended";
            default -> "waiting";
        };
    }

    /** "김미영 매니저" · "경력 2년 · 완료 청소 142건" (설명 문구는 코드에서 정함 — 프로필에 소개 글이 없다) */
    CustomerLiveResponse.Manager manager(UUID managerId) {
        ProfileService.ManagerIdentity m = profiles.managerIdentity(managerId);
        // 경력 = 활동 시작일부터 오늘(서울 날짜)까지 만 년 수, 1년 미만도 '1년'. 활동 시작일이 없으면 경력 부분을 뺀다
        String career = m.activeSince() == null ? "" : "경력 " + Math.max(1, Period.between(m.activeSince(), clock.today()).getYears()) + "년 · ";
        return new CustomerLiveResponse.Manager(m.name() + " 매니저", career + "완료 청소 " + m.completedCount() + "건", AVATAR_KEY);
    }

    // ── 공통 ──

    /** 예약의 고객 또는 배정된 매니저만 (그 외 404 — 존재를 숨긴다) */
    Booking requireParty(UUID userId, UUID bookingId) {
        Booking b = bookings.find(bookingId).orElseThrow(ApiException::notFound);
        if (!userId.equals(b.getCustomerId()) && !userId.equals(b.getManagerId())) throw ApiException.notFound();
        return b;
    }

    /** 웹훅·재조정기가 쓴다: 바디캠이 붙었을 때의 공통 처리 (§8.1). 첫 연결이면 고객 푸시 */
    // 순서: 기기 온라인 → 세션 live → (작업 일시정지면 곧바로 mute) → 오디오 막기 → 고객 구독 맞추기 → 녹화 시작 → CameraOnline 이벤트.
    // 푸시는 여기서 보내지 않고 아웃박스에 CameraOnline 을 남긴다 — 푸시 핸들러가 first 일 때만 고객에게 보낸다
    void cameraAttached(LiveSession s, ManagerDevice device, String trackSid) {
        Booking b = bookings.find(s.getBookingId()).orElse(null);
        if (b == null) return;
        device.markOnline(clock.now());
        if (trackSid == null) return; // 참가자는 들어왔는데 영상 트랙이 아직 없다 — live 로 적으면 pause 가 막을 트랙이 없어 jobs/pause 가 409 로 돈다. 재조정기가 트랙이 보일 때 잇는다
        boolean first = s.getCameraOnlineAt() == null; // 이 작업에서 바디캠이 한 번도 붙은 적 없다 (재접속이면 false)
        // 막아야 하는가 = 작업이 일시정지거나, 매니저가 영상을 멈추고 아직 재개하지 않았다(videoHeld). 후자는 영상만 막고 jobs/pause 가
        // 실패한 채 바디캠이 끊겼다 다시 붙은 경우다 — 예전에는 작업 상태만 봐서 새 트랙을 막지 않아 영상이 다시 나갔다 (2026-09-28 사용자 결정: 재개할 때까지 막는다)
        boolean shouldMute = holdsVideo(b, s);
        if (!shouldMute) s.clearMuteRequests(); // 영상 일시정지가 실패(502)한 뒤 남은 옛 mute 요청으로 진행 중 영상을 막지 않는다 — 매니저는 '실패' 로 안내받았다
        s.cameraAttached(trackSid, false, clock.now()); // 트랙이 막혔는지 아직 모른다 — '나가는 중'(live) 으로 적고, 막아야 하면 아래에서 확인 뒤 paused
        if (shouldMute) {
            // 일시정지 중 재접속 — Ingress 잠금(감사 H-02)이 실패했거나 잠그기 전에 다시 붙은 경우, 또는 카메라 없이 멈춘 뒤 처음 붙은 경우.
            // 새 트랙은 mute 안 된 채 올라오므로 바로 막는다
            log.warn("일시정지 중 바디캠이 다시 붙음 {} — 사후 mute", s.getRoomName());
            if (setMuted(s, device, true)) s.muted(clock.now()); // 확인된 뒤에만 paused
            else s.requestMute();                    // live + mute_pending — 재조정기가 1분 안에 다시 (그때까지 영상이 나갈 수 있음을 상태가 말한다)
        }
        muteAudio(s, device); // 영상만 송출(결정 §13-23) — 바디캠이 소리도 보내면 그 트랙을 막는다
        syncViewerAccess(s);
        if (!shouldMute || LiveSession.PAUSED.equals(s.getStatus())) ensureRecording(s); // mute 를 확인 못 한 채로는 녹화를 새로 시작하지 않는다 (감사 H-02)
        outbox.publish(new CameraOnline(s.getBookingId(), b.getCustomerId(), first));
    }

    /**
     * 고객 시청자의 구독 권한을 세션 상태에 맞춘다(일시정지·mute 미확인이면 막고, 아니면 연다). 값이 이미 맞으면 LiveKit 을 부르지 않는다.
     * 새 시청 토큰을 막아도 이미 들어와 있는 고객은 그대로 받으므로, 일시정지 중 다시 붙은 바디캠의 새 트랙(아직 mute 전)이 그들에게 가지 않게 (재감사 H-02).
     * 실패는 던지지 않는다 — 재조정기가 1분마다 다시 맞춘다
     */
    void syncViewerAccess(LiveSession s) {
        syncViewerAccess(s, null);
    }

    // 참가자 목록을 받는 판(같은 이름, 인자만 다름)은 재조정기(LiveReconciler.reconcileOne)가 부른다
    /**
     * @param participants 재조정기가 이미 읽은 방 참가자 목록. 있으면 뜻이 그대로여도 참가자마다 실제 권한을 확인해 어긋난 사람만 고친다 —
     *                     한 명 막기가 실패해도(웹훅·일시정지 때) 다음 주기에 다시 막힌다 (재감사 2차 #1). 없으면 뜻이 바뀔 때만 LiveKit 을 부른다
     */
    void syncViewerAccess(LiveSession s, List<LiveKitGateway.Participant> participants) {
        boolean block = shouldBlockViewers(s);
        if (participants == null && block == s.isViewersBlocked()) return;
        setViewerAccess(s, !block, participants);
    }

    /**
     * 끝난 녹화를 받았는데 그 사이 고객이 탈퇴했다 — 탈퇴 미디어 정리는 준비된(ready) 녹화만 지우므로, 업로드 중에 탈퇴하면 그 집 영상이
     * 90일 보관 정리까지 남았다 (자체 점검 #3). 파일을 지우고 세션을 '지움' 으로. Storage 삭제가 실패하면 던진다 — 웹훅·재조정기가 다시 부른다
     * @return 지웠으면 true (호출자는 '녹화 준비됨' 으로 적지 않는다)
     */
    boolean discardRecordingOfDeletedCustomer(LiveSession s, UUID customerId, String path) {
        if (!profiles.isDeleted(customerId)) return false;
        storage.delete(props.storage().recordingsBucket(), path);
        s.recordingDeleted();
        log.info("탈퇴한 고객의 녹화본을 받는 즉시 지움 {}", s.getRoomName());
        return true;
    }

    /** 고객 구독을 막아야 하는가 — 예약(작업)의 일시정지도 본다 (카메라가 끊겨도 뜻이 남게) */
    boolean shouldBlockViewers(LiveSession s) {
        boolean jobPaused = bookings.find(s.getBookingId()).map(b -> b.getStatus() == BookingStatus.PAUSED).orElse(false);
        return s.shouldBlockViewers(jobPaused);
    }

    /** 고객 시청자 전원의 구독을 열거나(allow=true) 막는다. 참가자 목록은 LiveKit 에 새로 묻는다. 실패는 로그만 남기고 던지지 않는다 */
    void setViewerAccess(LiveSession s, boolean allow) {
        setViewerAccess(s, allow, null);
    }

    /**
     * known 이 있으면(재조정기가 읽은 목록) 그대로 쓰고, 없으면 LiveKit 에 참가자 목록을 묻는다.
     * 'customer-' 로 시작하는 고객 참가자 중 지금 권한이 원하는 값과 다른 사람만 바꾼다 (바디캠·매니저 미리보기는 건드리지 않는다)
     */
    void setViewerAccess(LiveSession s, boolean allow, List<LiveKitGateway.Participant> known) {
        s.setViewersBlocked(!allow); // 뜻을 먼저 적는다 — 웹훅(다시 들어온 고객)이 이 값을 본다. 실제 권한은 참가자마다 아래에서·재조정기가 맞춘다
        List<LiveKitGateway.Participant> participants;
        try {
            participants = known != null ? known : livekit.listParticipants(s.getRoomName());
        } catch (ApiException e) {
            log.warn("시청 권한 {} — 참가자 목록 실패 {}: {} (재조정기가 다시)", allow ? "열기" : "막기", s.getRoomName(), e.getMessage());
            return;
        }
        for (LiveKitGateway.Participant p : participants) {
            if (!p.identity().startsWith(LiveWebhookService.VIEWER_PREFIX) || p.canSubscribe() == allow) continue;
            try {
                livekit.setCanSubscribe(s.getRoomName(), p.identity(), allow);
            } catch (ApiException e) {
                log.warn("시청 권한 {} 실패 {} {}: {} (재조정기가 다시)", allow ? "열기" : "막기", s.getRoomName(), p.identity(), e.getMessage());
            }
        }
    }

    /**
     * 영상만 송출(결정 §13-23, domain.md): 바디캠이 소리를 끄지 못하면 서버가 오디오 트랙을 늘 mute 한다 — 명세에만 있고 구현이 빠져 있었다(재감사).
     * 고객 앱은 영상만 붙이지만 시청 토큰으로는 오디오도 구독할 수 있다. 실패는 던지지 않는다(재조정기가 다시)
     */
    void muteAudio(LiveSession s, ManagerDevice device) {
        try {
            livekit.getParticipant(s.getRoomName(), device.cameraIdentity()).ifPresent(p -> muteAudio(s, device, p));
        } catch (ApiException e) {
            log.warn("오디오 확인 실패 {}: {}", s.getRoomName(), e.getMessage());
        }
    }

    /** 바디캠 참가자의 오디오 트랙 중 아직 안 막힌 것을 모두 mute 한다(영상 트랙은 건너뛴다). 재조정기는 이미 읽은 참가자로 이걸 직접 부른다 */
    void muteAudio(LiveSession s, ManagerDevice device, LiveKitGateway.Participant camera) {
        for (LiveKitGateway.Track t : camera.tracks()) {
            if (t.video() || t.muted()) continue;
            try {
                livekit.mutePublishedTrack(s.getRoomName(), device.cameraIdentity(), t.sid(), true);
                log.info("바디캠 오디오 트랙 mute {} {}", s.getRoomName(), t.sid());
            } catch (ApiException e) {
                log.warn("오디오 mute 실패 {}: {}", s.getRoomName(), e.getMessage());
            }
        }
    }

    /**
     * 일시정지 동안 바디캠이 다시 붙지 못하게 Ingress 를 잠근다 (감사 H-02). 송출 중(busy)이거나 LiveKit 오류면 false —
     * 송출 중이면 끊기는 순간 ingress_ended·재조정기가 다시 부른다. 던지지 않는다(웹훅·재조정기 트랜잭션을 깨지 않게)
     * 부르는 곳은 모두 카메라가 안 붙어 보일 때다(pause 의 '카메라 없음' 경로, ingress_ended·재조정기는 막아야 할 때 — 작업 일시정지 또는 video_held). 그때 busy 면
     * 카메라가 방금 다시 붙은 것이라 새 트랙은 아직 mute 전이다 — cameraAttached(웹훅·재조정기)가 작업 일시정지를 보고 막는다
     */
    boolean lockIngress(ManagerDevice device) {
        if (device.getIngressId() == null || !device.isIngressEnabled()) return true;
        try {
            livekit.setIngressEnabled(device.getIngressId(), false);
            device.setIngressEnabled(false);
            log.info("일시정지 — 바디캠 Ingress 잠금 {}", device.getIngressId());
            return true;
        } catch (LiveKitGateway.IngressBusyException e) {
            return false;
        } catch (ApiException e) {
            log.warn("Ingress 잠금 실패 {}: {} (재조정기가 다시)", device.getIngressId(), e.getMessage());
            return false;
        }
    }

    /** 잠금을 푼다 — 재개·작업 종료·다음 작업 시작. 실패하면 재조정기가 다시(작업이 진행 중인데 잠겨 있으면 연다). 던지지 않는다 */
    void unlockIngress(ManagerDevice device) {
        if (device.getIngressId() == null || device.isIngressEnabled()) return;
        try {
            livekit.setIngressEnabled(device.getIngressId(), true);
            device.setIngressEnabled(true);
            log.info("바디캠 Ingress 잠금 해제 {}", device.getIngressId());
        } catch (RuntimeException e) {
            log.warn("Ingress 잠금 해제 실패 {}: {} (재조정기가 다시)", device.getIngressId(), e.getMessage());
        }
    }

    /** 카메라가 붙어 있는데 녹화가 없으면 시작 (§8.2: ingress_started 에서. 실패는 재조정기가 다시) */
    void ensureRecording(LiveSession s) {
        if (!LiveSession.REC_NONE.equals(s.getRecordingStatus())) return; // 이미 녹화 중·업로드 중·준비됨·실패면 새로 시작하지 않는다
        // 웹훅뿐 아니라 재조정기도 이 함수를 부른다. mute 미확인 상태에서 새 녹화를 시작하지 않는다.
        if (s.isMutePending() || s.isUnmutePending()) return;
        try {
            // 방 영상을 녹화해 Storage 에 '예약id.mp4' 로 올리게 한다. 받은 egressId 를 세션에 적어 정지·완료 확인에 쓴다
            LiveKitGateway.EgressInfo egress = livekit.startRecording(s.getRoomName(), s.getBookingId() + ".mp4");
            s.recordingStarted(egress.egressId());
        } catch (ApiException e) {
            log.warn("녹화 시작 실패 {} — 재조정기가 다시 시도", s.getRoomName());
        }
    }

    /** setMuted 의 확인 사이에 쉰다. 쉬는 중 스레드 중단 요청이 오면 예외는 여기서 끝내되, 지워진 중단 표시는 다시 세워 둔다 */
    private static void sleep(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
