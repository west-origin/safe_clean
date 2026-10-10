package kr.ansimclean.api.live;

import kr.ansimclean.api.common.config.AppProperties;
import kr.ansimclean.api.booking.Booking;
import kr.ansimclean.api.common.error.ApiException;
import java.util.UUID;
import kr.ansimclean.api.booking.BookingService;
import kr.ansimclean.api.booking.BookingStatus;
import kr.ansimclean.api.common.events.CameraOffline;
import kr.ansimclean.api.common.events.RecordingFailed;
import kr.ansimclean.api.common.events.RecordingReady;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.live.LiveKitGateway.WebhookEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * LiveKit 웹훅 처리 (§8.1). 같은 event id 는 한 번만(livekit_events), 전이는 "현재 상태에서 허용될 때만" — 순서가 뒤바뀌어 와도 안전하다.
 * 서명 검증은 컨트롤러가 LiveKitGateway.parseWebhook 으로 먼저 한다.
 * 부르는 곳: LiveWebhookController(handle), LiveReconciler(applyEgress — 녹화 종료 웹훅을 놓쳤을 때).
 * 다루는 이벤트: 바디캠 송출 시작·끝(ingress_*) → 기기 online/offline·카메라 붙음/끊김, 녹화(egress_*) → 녹화 상태,
 * 고객 시청자 입장·퇴장(participant_*) → 시청자 수, 방 닫힘(room_finished) → 세션 종료.
 * 푸시는 직접 보내지 않고 아웃박스 이벤트(CameraOffline·RecordingReady·RecordingFailed)로 남긴다.
 * handle 한 번이 한 묶음이다 — 처리 중 예외가 나면 중복 방어 행(livekit_events)도 함께 되돌려져, 같은 id 가 다시 오면 다시 처리된다.
 * LiveKit 호출 하나가 실패해 묶음 전체가 되돌려지지 않게, 일부 호출은 실패를 잡고 재조정기에 맡긴다 (예: viewer 의 구독 막기, LiveService.bindIngressQuietly).
 */
@Service
@Transactional
public class LiveWebhookService {
    // LiveKit 웹훅의 event 이름 (WebhookEvent.event 값). 아래 목록에 없는 이벤트는 무시한다
    public static final String INGRESS_STARTED = "ingress_started";
    public static final String INGRESS_ENDED = "ingress_ended";
    public static final String EGRESS_STARTED = "egress_started";
    public static final String EGRESS_UPDATED = "egress_updated";
    public static final String EGRESS_ENDED = "egress_ended";
    public static final String PARTICIPANT_JOINED = "participant_joined";
    public static final String PARTICIPANT_LEFT = "participant_left";
    public static final String ROOM_FINISHED = "room_finished";
    public static final String TRACK_PUBLISHED = "track_published"; // 휴대폰 송출(§8.0): 매니저 앱이 카메라 트랙을 올렸다
    public static final String TRACK_UNPUBLISHED = "track_unpublished"; // 휴대폰 송출: 매니저 앱이 트랙을 내렸다(송출 종료·앱 종료·연결 끊김)
    static final String CAMERA_PREFIX = "camera-"; // 카메라 참가자 이름표 앞부분 (ManagerDevice.cameraIdentity — 바디캠 Ingress·휴대폰 송출 공통)
    static final String VIEWER_PREFIX = "customer-"; // 고객 시청자의 참가자 이름표 앞부분 (LiveService.viewerToken 이 "customer-고객id" 로 만든다)
    static final String PARKING_PREFIX = "parking-"; // 대기 방 이름 앞부분 (ManagerDevice.parkingRoom) — 작업이 없을 때 바디캠이 향하는 아무도 못 보는 방
    private static final Logger log = LoggerFactory.getLogger(LiveWebhookService.class);

    private final LiveSessionRepository sessions;
    private final ManagerDeviceRepository devices;
    private final LivekitEventRepository events;
    private final LiveKitGateway livekit;
    private final LiveService live;
    private final BookingService bookings;
    private final OutboxService outbox;
    private final AppClock clock;
    private final AppProperties props; // app.livekit.source — 휴대폰 송출 모드에서만 track_* 웹훅을 카메라 붙음·끊김으로 본다 (§8.0)

    // live(LiveService) = 카메라 붙음 처리·Ingress 방 옮기기·잠금 등 재조정기와 같이 쓰는 공통 처리
    public LiveWebhookService(LiveSessionRepository sessions, ManagerDeviceRepository devices, LivekitEventRepository events, LiveKitGateway livekit,
                              LiveService live, BookingService bookings, OutboxService outbox, AppClock clock, AppProperties props) {
        this.sessions = sessions;
        this.devices = devices;
        this.events = events;
        this.livekit = livekit;
        this.live = live;
        this.bookings = bookings;
        this.outbox = outbox;
        this.clock = clock;
        this.props = props;
    }

    /** @return false 면 이미 처리한 이벤트 */
    public boolean handle(WebhookEvent e) {
        // 중복 방어: 처리한 이벤트 id 를 livekit_events 에 적어 두고, 같은 id 가 다시 오면 아무것도 하지 않는다.
        // id 가 없는 이벤트는 기록하지 않고 매번 처리한다. 기록은 RecordingRetention 이 7일 지나면 지운다
        if (e.id() != null && events.existsById(e.id())) return false;
        if (e.id() != null) events.save(LivekitEvent.of(e.id(), e.event(), clock.now()));
        // switch 화살표(->) 문법: 이벤트 이름이 맞는 한 줄만 실행한다 (아래로 흘러내리지 않는다)
        switch (e.event()) {
            case INGRESS_STARTED -> onIngressStarted(e);
            case INGRESS_ENDED -> onIngressEnded(e);
            case EGRESS_STARTED, EGRESS_UPDATED -> onEgressUpdated(e);
            case EGRESS_ENDED -> onEgressEnded(e);
            case PARTICIPANT_JOINED -> viewer(e, +1);
            case PARTICIPANT_LEFT -> viewer(e, -1);
            case ROOM_FINISHED -> onRoomFinished(e);
            case TRACK_PUBLISHED -> onTrackPublished(e);
            case TRACK_UNPUBLISHED -> onTrackUnpublished(e);
            default -> log.debug("LiveKit 웹훅 무시 {}", e.event());
        }
        return true;
    }

    /**
     * 바디캠이 송출을 시작했다. 기기는 어느 방이든 online 으로 적는다(대기 방이어도 — Job/Preparing 장비 상태의 '연결됨').
     * 예약 방이고 세션이 진행 중(waiting_camera·live·paused)이면 담당 매니저의 바디캠인지 확인한 뒤 LiveService.cameraAttached 로 넘긴다
     */
    void onIngressStarted(WebhookEvent e) {
        if (e.ingress() == null) return;
        Optional<ManagerDevice> device = devices.findByIngressId(e.ingress().ingressId());
        device.ifPresent(d -> d.markOnline(clock.now()));
        String room = e.ingress().roomName();
        if (room == null || room.startsWith(PARKING_PREFIX) || device.isEmpty()) return; // 대기 방이거나 등록되지 않은 Ingress 면 세션을 건드리지 않는다
        // lockByRoomName = 방 이름으로 세션 행을 잠그고 읽는다 — pause·재조정기와 같은 행을 동시에 고치지 않게
        sessions.lockByRoomName(room).filter(LiveSession::isActive).ifPresent(s -> {
            UUID assigned = bookings.find(s.getBookingId()).map(Booking::getManagerId).orElse(null);
            if (!device.get().getManagerId().equals(assigned)) {
                // 이 방의 담당이 아닌 매니저의 바디캠(방문을 취소한 매니저 등) — 고객 방에 붙이지 않고 대기 방으로 돌린다 (전체 재점검 J1)
                log.warn("담당이 아닌 바디캠이 예약 방에 붙음 {} → 대기 방으로", room);
                live.bindIngressQuietly(device.get(), device.get().parkingRoom());
                return;
            }
            // 방 안의 바디캠 참가자("camera-매니저id")가 올린 첫 영상 트랙 id. 아직 트랙이 없으면 null — cameraAttached 는 그때 live 로 적지 않는다
            String trackSid = livekit.getParticipant(room, device.get().cameraIdentity()).flatMap(LiveKitGateway.Participant::videoTrack).map(LiveKitGateway.Track::sid).orElse(null);
            live.cameraAttached(s, device.get(), trackSid);
        });
    }

    /**
     * 바디캠 송출이 끝났다(끄거나 끊김). 기기를 offline 으로 적고, 예약 방에 카메라가 붙어 있던 세션이면(작업이 진행 중·일시정지일 때만)
     * 카메라 대기(waiting_camera)로 되돌린다.
     * 작업이 진행 중이면 매니저 푸시(CameraOffline 아웃박스), 일시정지 중이면 다시 붙지 못하게 Ingress 를 잠근다. 마지막에 미뤄 둔 방 옮기기를 적용한다
     */
    void onIngressEnded(WebhookEvent e) {
        if (e.ingress() == null) return;
        Optional<ManagerDevice> device = devices.findByIngressId(e.ingress().ingressId());
        device.ifPresent(ManagerDevice::markOffline);
        String room = e.ingress().roomName();
        if (room != null && !room.startsWith(PARKING_PREFIX)) {
            sessions.lockByRoomName(room).filter(LiveSession::isCameraOnline).ifPresent(s -> {
                Booking b = bookings.find(s.getBookingId()).orElse(null);
                // 작업이 진행 중·일시정지(ACTIVE)일 때만 세션을 바꾼다 — 끝났거나 취소된 작업이면 그대로 둔다
                if (b != null && BookingStatus.ACTIVE.contains(b.getStatus())) {
                    s.cameraDetached(clock.now());
                    // 일시정지 중(매니저가 영상을 멈춘 채 포함)에 끈 것은 장애가 아니다 — '바디캠이 끊겼어요' 푸시를 보내지 않는다 (자체 점검 #4)
                    if (b.getStatus() == BookingStatus.IN_PROGRESS && !s.isVideoHeld()) outbox.publish(new CameraOffline(s.getBookingId(), b.getManagerId(), "disconnected"));
                    // 영상만 막고 jobs/pause 가 실패한 세션(작업은 진행 중)도 매니저가 멈춘 채(videoHeld)라 잠근다 — 다시 붙어도 cameraAttached 가 막는다
                    if (LiveService.holdsVideo(b, s)) device.ifPresent(live::lockIngress); // 일시정지 중 끊겼다 — 다시 붙기 전에 잠근다 (감사 H-02)
                }
            });
        }
        // 송출 중이라 못 바꿨던 방을 지금 적용한다 (§8.1 pending_room)
        device.filter(d -> d.getPendingRoom() != null).ifPresent(d -> live.bindIngressQuietly(d, d.getPendingRoom()));
    }

    /**
     * 휴대폰 송출(§8.0): 매니저 앱('camera-매니저id')이 카메라 영상 트랙을 올렸다 → 바디캠의 ingress_started 와 같이 LiveService.cameraAttached
     * (live 로 적기·막아야 하면 바로 mute·녹화 시작·첫 연결 푸시). 바디캠 모드이거나, 영상이 아니거나, 담당 매니저가 아니면 무시한다
     */
    void onTrackPublished(WebhookEvent e) {
        UUID managerId = phoneCameraManager(e);
        if (managerId == null) return;
        Optional<ManagerDevice> device = devices.findById(managerId); // LiveService.startFromPhone 이 만든 휴대폰 카메라 행
        if (device.isEmpty()) return;
        sessions.lockByRoomName(e.roomName()).filter(LiveSession::isActive).ifPresent(s -> {
            UUID assigned = bookings.find(s.getBookingId()).map(Booking::getManagerId).orElse(null);
            if (!managerId.equals(assigned)) { // 송출 토큰은 담당 매니저에게만 나가므로 정상이면 오지 않는다 (배정이 바뀐 직후의 옛 토큰 등)
                log.warn("담당이 아닌 매니저의 카메라 트랙 {} — 무시", e.roomName());
                return;
            }
            live.cameraAttached(s, device.get(), e.track().sid());
        });
    }

    /**
     * 휴대폰 송출: 매니저 앱이 카메라 트랙을 내렸다(송출 종료·앱 종료·네트워크 끊김) → 바디캠의 ingress_ended 와 같이 카메라 대기로 되돌린다.
     * 작업이 진행 중이고 매니저가 영상을 멈춘 채가 아니면 '카메라 연결이 끊겼어요' 푸시(CameraOffline). 지금 세션에 붙은 트랙일 때만 처리한다
     */
    void onTrackUnpublished(WebhookEvent e) {
        UUID managerId = phoneCameraManager(e);
        if (managerId == null) return;
        Optional<ManagerDevice> device = devices.findById(managerId);
        sessions.lockByRoomName(e.roomName()).filter(LiveSession::isCameraOnline).ifPresent(s -> {
            if (s.getCameraTrackSid() != null && !s.getCameraTrackSid().equals(e.track().sid())) return; // 이미 새 트랙으로 다시 붙었다 — 옛 트랙의 늦은 알림
            Booking b = bookings.find(s.getBookingId()).orElse(null);
            if (b == null || !BookingStatus.ACTIVE.contains(b.getStatus())) return;
            device.ifPresent(ManagerDevice::markOffline);
            s.cameraDetached(clock.now());
            if (b.getStatus() == BookingStatus.IN_PROGRESS && !s.isVideoHeld()) outbox.publish(new CameraOffline(s.getBookingId(), b.getManagerId(), "disconnected"));
        });
    }

    /** 휴대폰 송출 모드에서 예약 방의 'camera-매니저id' 영상 트랙 이벤트면 그 매니저 id, 아니면 null */
    private UUID phoneCameraManager(WebhookEvent e) {
        if (!props.livekit().appSource()) return null; // 바디캠 모드에서는 ingress_* 가 같은 일을 한다
        if (e.track() == null || !e.track().video() || e.roomName() == null || e.roomName().startsWith(PARKING_PREFIX)) return null;
        String identity = e.participantIdentity();
        if (identity == null || !identity.startsWith(CAMERA_PREFIX)) return null;
        try {
            return UUID.fromString(identity.substring(CAMERA_PREFIX.length()));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** 녹화(Egress)가 시작됐거나 진행 중이다 — 세션에 egress id 를 적고 녹화 상태를 recording 으로 */
    void onEgressUpdated(WebhookEvent e) {
        if (e.egress() == null || e.egress().roomName() == null) return;
        sessions.lockByRoomName(e.egress().roomName()).ifPresent(s -> {
            // 녹화를 아직 모를 때만 — ready/failed 뒤에 늦게 온 started/updated 가 상태를 되돌리지 않게
            if (LiveSession.REC_NONE.equals(s.getRecordingStatus()) && s.getEgressId() == null && !e.egress().isFinished()) s.recordingStarted(e.egress().egressId());
        });
    }

    /** 녹화가 끝났다(성공·실패) — 방 이름으로 세션을 잠그고 applyEgress 로 반영 */
    void onEgressEnded(WebhookEvent e) {
        if (e.egress() == null || e.egress().roomName() == null) return;
        sessions.lockByRoomName(e.egress().roomName()).ifPresent(s -> applyEgress(s, e.egress()));
    }

    /** 웹훅·재조정기 공통: 끝난 Egress 를 세션에 반영 */
    void applyEgress(LiveSession s, LiveKitGateway.EgressInfo egress) {
        // 이미 ready 면 다시 적지 않는다 — 웹훅과 재조정기가 같은 성공 녹화를 두 번 넘겨도 RecordingReady 는 한 번만 나간다.
        // (실패 쪽은 재조정기가 recording·uploading 인 세션만 넘기므로 failed 뒤에 재조정기가 다시 넘기지는 않는다.) 끝나지 않은 녹화도 무시
        if (s.isRecordingReady() || !egress.isFinished()) return;
        if (s.getEgressId() != null && egress.egressId() != null && !s.getEgressId().equals(egress.egressId())) return; // 다른(이전) 녹화의 이벤트
        if (egress.isComplete()) {
            // 성공: 저장 경로(LiveKit 이 알려 준 파일 이름, 없으면 "예약id.mp4")를 적고 고객 푸시(RecordingReady)를 아웃박스에 남긴다
            String path = egress.filename() == null || egress.filename().isBlank() ? s.getBookingId() + ".mp4" : egress.filename();
            Booking b = bookings.find(s.getBookingId()).orElse(null);
            if (b != null && live.discardRecordingOfDeletedCustomer(s, b.getCustomerId(), path)) return; // 업로드 중에 탈퇴 (자체 점검 #3)
            s.recordingReady(path, egress.durationSeconds());
            if (b != null) outbox.publish(new RecordingReady(s.getBookingId(), b.getCustomerId()));
        } else {
            // 실패·중단·한도 초과: 녹화본이 없다 — 운영자용 오류 로그 + 고객 안내 푸시(RecordingFailed)
            s.recordingFailed();
            log.error("녹화 실패 — 운영자 확인 필요(§13-8): {} {}: {}", s.getRoomName(), egress.status(), egress.error());
            bookings.find(s.getBookingId()).ifPresent(b -> outbox.publish(new RecordingFailed(s.getBookingId(), b.getCustomerId())));
        }
    }

    /**
     * 참가자가 방에 들어왔다(delta +1)·나갔다(-1). 이름표가 "customer-" 로 시작하는 고객 시청자만 센다 — 바디캠·매니저 미리보기는 빼고.
     * 들어온 고객은, 지금 구독을 막아야 하는 상태면 바로 구독 권한을 끈다
     */
    void viewer(WebhookEvent e, int delta) {
        if (e.roomName() == null || e.participantIdentity() == null || !e.participantIdentity().startsWith(VIEWER_PREFIX)) return;
        sessions.lockByRoomName(e.roomName()).ifPresent(s -> {
            if (delta > 0) s.viewerJoined();
            else s.viewerLeft();
            if (delta > 0 && (s.isViewersBlocked() || live.shouldBlockViewers(s))) { // 일시정지 전에 받은 토큰(2시간)으로 다시 들어온 고객 — 들어오자마자 막는다 (재감사 H-02). 실패하면 재조정기가 참가자별로 다시
                try {
                    livekit.setCanSubscribe(e.roomName(), e.participantIdentity(), false);
                } catch (ApiException ex) {
                    log.warn("다시 들어온 시청자 막기 실패 {}: {} (재조정기가 다시)", e.roomName(), ex.getMessage());
                }
            }
        });
    }

    /**
     * LiveKit 방이 닫혔다(빈 방 시간 초과나 방 삭제). 작업이 done 이면 세션을 ended 로 적는다.
     * 작업이 아직 끝나지 않았는데 방이 먼저 닫혔으면 세션은 그대로 두고 경고만 남긴다(재조정기가 정리)
     */
    void onRoomFinished(WebhookEvent e) {
        if (e.roomName() == null) return;
        sessions.lockByRoomName(e.roomName()).filter(s -> !s.isEnded()).ifPresent(s -> {
            Booking b = bookings.find(s.getBookingId()).orElse(null);
            if (b != null && b.getStatus() == BookingStatus.DONE) s.end(clock.now());
            else log.warn("방이 먼저 끝났어요 {} (작업 상태 {}) — 재조정기가 정리", e.roomName(), b == null ? null : b.getStatus());
        });
    }
}
