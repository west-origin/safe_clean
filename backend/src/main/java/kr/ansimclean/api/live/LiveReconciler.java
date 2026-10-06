package kr.ansimclean.api.live;

import kr.ansimclean.api.booking.Booking;
import kr.ansimclean.api.booking.BookingService;
import kr.ansimclean.api.booking.BookingStatus;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.events.CameraOffline;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.time.AppClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * 1분마다 LiveKit 을 조회해 DB 와 맞춘다 (BACKEND_PLAN.md §2.5 LiveReconciler) — 웹훅이 유실돼도 상태가 "해야 할 일" 을 말해 준다.
 *  · 카메라 참가자 유무 → waiting_camera ⇄ live/paused  · mute_pending/unmute_pending 재시도  · 녹화 없으면 시작, 끝났으면 ready/failed
 *  · 작업 done 인데 세션 미종료 → stop  · waiting_camera 5분 초과 → 매니저 푸시 1회  · pending_room 적용
 *  · 시청자 수를 참가자 목록으로 보정  · 끝난 세션의 남은 녹화(egress_ended 유실) 마무리 · stop 때 녹화 정지·방 삭제가 실패했으면 다시 정지
 * 한 번 돌 때의 순서(reconcile): ① 끝나지 않은 세션 하나씩 보정 ② 끝난 지 15분 안 된 세션의 방·바디캠 정리
 * ③ 기기 단위로 남은 Ingress 잠금 풀기 ④ 끝난 세션의 남은 녹화 마무리.
 * test 프로필에서는 스케줄이 꺼져 있어(SchedulingConfig) 테스트가 reconcile() 을 직접 부른다.
 * 위의 'mute_pending 재시도' 는 작업이 일시정지일 때만이다 — 작업이 진행 중이면 옛 mute 요청은 버린다(reconcileOne 의 mute_pending 분기).
 */
@Component
public class LiveReconciler {
    /** 작업 중(in_progress) 바디캠이 붙지 않은 채(또는 끊긴 뒤) 이 시간이 지나면 매니저에게 한 번 알린다 (일시정지 중에는 알리지 않는다) */
    static final Duration CAMERA_WAIT_LIMIT = Duration.ofMinutes(5);
    /** live/pause 뒤 이 시간 안에는 "세션 paused ∧ 작업 in_progress" 를 unmute 하지 않는다 — jobs/pause 가 곧 따라오는 창 */
    static final Duration PAUSE_GRACE = Duration.ofMinutes(2);
    /** 끝난 뒤 이 시간 동안은 방 삭제·Ingress 복귀를 매번 다시 확인한다 (deleteRoom 은 없으면 404 무시라 값싸다) */
    static final Duration ROOM_CLOSE_RECHECK = Duration.ofMinutes(15);
    private static final Logger log = LoggerFactory.getLogger(LiveReconciler.class);

    private final LiveSessionRepository sessions;
    private final ManagerDeviceRepository devices;
    private final LiveKitGateway livekit;
    private final LiveService live;
    private final LiveWebhookService webhooks;
    private final BookingService bookings;
    private final OutboxService outbox;
    private final AppClock clock;
    // 세션·기기마다 묶음을 따로 열고 커밋하는 데 쓴다 (이유는 reconcile 설명, CODE_GUIDE.md 3-3)
    private final TransactionTemplate tx;

    public LiveReconciler(LiveSessionRepository sessions, ManagerDeviceRepository devices, LiveKitGateway livekit, LiveService live, LiveWebhookService webhooks,
                          BookingService bookings, OutboxService outbox, AppClock clock, TransactionTemplate tx) {
        this.sessions = sessions;
        this.devices = devices;
        this.livekit = livekit;
        this.live = live;
        this.webhooks = webhooks;
        this.bookings = bookings;
        this.outbox = outbox;
        this.clock = clock;
        this.tx = tx;
    }

    /**
     * 스케줄러 입구: 앞 실행이 끝나고 60초 뒤마다 reconcile 을 부른다 (CODE_GUIDE.md 3-9). 예외는 여기서 잡아 로그만 남긴다
     */
    @Scheduled(fixedDelay = 60000)
    public void run() {
        try {
            reconcile();
        } catch (Exception e) {
            log.error("라이브 재조정 실패", e);
        }
    }

    /**
     * 세션마다 **별도 트랜잭션**으로 잠그고(SKIP LOCKED) 처리하고 커밋한다. 한 트랜잭션에 모으면 처리한 세션의 행 잠금이 배치가 끝날 때까지 남아,
     * 앞쪽 세션의 live/pause·웹훅·jobs/pause 가 LiveKit 호출·확인 폴링을 다 기다린다(앱 20초 타임아웃). 지금 남이 쥔 세션은 다음 분에.
     * 반환값 = 처리한 세션·기기 수 (②의 끝난 방 정리는 세지 않는다). 테스트가 이 값을 확인한다
     */
    public int reconcile() {
        int touched = 0;
        // ① 아직 끝나지 않은 세션. id 만 먼저 읽고, 잠근 뒤 다시 본다 — 그 사이 다른 요청이 끝냈으면 건너뛴다
        for (UUID id : sessions.findIdsByStatusIn(List.of(LiveSession.WAITING_CAMERA, LiveSession.LIVE, LiveSession.PAUSED))) {
            touched += each(id, s -> {
                if (s.isEnded()) return 0;
                reconcileOne(s);
                return 1;
            }, "재조정 건너뜀");
        }
        // ② 끝난 지 15분이 안 된 세션: 방 삭제·바디캠 잠금 해제·대기 중인 방 바인딩을 다시 한다 (이미 됐으면 효과 없음)
        Instant closeSince = clock.now().minus(ROOM_CLOSE_RECHECK);
        for (LiveSession found : sessions.findByStatusAndEndedAtAfter(LiveSession.ENDED, closeSince)) {
            each(found.getBookingId(), s -> {
                livekit.deleteRoom(s.getRoomName()); // stop 때 실패했으면 여기서 닫힌다 — 남아 있던 시청자 연결도 끊긴다
                Optional<ManagerDevice> d = bookings.find(s.getBookingId()).map(Booking::getManagerId).flatMap(devices::findById);
                // stop 때 잠금 해제가 실패했으면 여기서 (다음 작업에서 카메라가 붙게). 같은 매니저의 다음 작업이 이미 쓰고 있으면 건드리지 않는다 —
                // 매분 다음 작업의 일시정지 잠금을 다시 열던 것 (자체 점검 #1)
                d.filter(x -> !live.usedByAnotherJob(x, s.getBookingId())).ifPresent(x -> {
                    live.unlockIngress(x);
                    if (x.getPendingRoom() != null) live.bindIngressQuietly(x, x.getPendingRoom());
                });
                return 0;
            }, "끝난 방 정리 건너뜀");
        }
        touched += releaseStaleLocks(); // ③
        // ④ 끝난 세션인데 녹화가 아직 recording/uploading — LiveKit 에 물어 끝났으면 반영하고, 녹화 중으로 남았으면 정지를 다시 보낸다
        for (LiveSession found : sessions.findByStatusAndRecordingStatusIn(LiveSession.ENDED, List.of(LiveSession.REC_RECORDING, LiveSession.REC_UPLOADING))) {
            if (found.getEgressId() == null) continue;
            touched += each(found.getBookingId(), s -> {
                Optional<LiveKitGateway.EgressInfo> egress = livekit.getEgress(s.getEgressId());
                if (egress.isPresent() && egress.get().isFinished()) {
                    // 웹훅(egress_ended)을 받았을 때와 같은 처리로 세션에 녹화 결과를 적는다
                    webhooks.applyEgress(s, egress.get());
                } else if (LiveSession.REC_RECORDING.equals(s.getRecordingStatus())) {
                    // stop 때 LiveKit 이 안 받았다 — 세션은 ended 인데 녹화·방이 살아 있을 수 있다. 정지 명령을 다시 보낸다(없으면 404 무시)
                    livekit.stopEgress(s.getEgressId());
                    livekit.deleteRoom(s.getRoomName());
                    s.recordingStopping();
                    log.warn("끝난 세션의 녹화를 다시 정지했어요 {}", s.getRoomName());
                }
                return 1;
            }, "녹화 마무리 건너뜀");
        }
        return touched;
    }

    /**
     * 기기 단위: 잠긴 Ingress 인데 그 매니저에게 일시정지한 작업이 없으면 푼다. 세션 기준 정리는 끝난 지 15분까지만 보므로, 그 사이 LiveKit 이
     * 계속 실패하면 잠금이 남아 바디캠이 붙지 못하고 다음 작업의 청소 준비 화면이 '카메라 연결 실패' 로 막혔다 (자체 점검 #52)
     */
    int releaseStaleLocks() {
        int n = 0;
        for (ManagerDevice found : devices.findByIngressEnabledFalse()) {
            // 기기마다 트랜잭션 하나. 목록을 읽은 뒤 바뀌었을 수 있어 안에서 다시 읽고 판단한다 (그 사이 열렸으면 건너뜀)
            Integer done = tx.execute(status -> {
                ManagerDevice d = devices.findById(found.getManagerId()).orElse(null);
                if (d == null || d.isIngressEnabled()) return 0;
                // 진행 중인 작업이 일시정지거나 매니저가 영상을 멈춘 채면 잠금이 맞다 — 그대로 둔다
                boolean paused = bookings.activeJob(d.getManagerId()).filter(b -> LiveService.holdsVideo(b, sessions.findById(b.getId()).orElse(null))).isPresent();
                if (paused) return 0;
                live.unlockIngress(d);
                if (d.getPendingRoom() != null) live.bindIngressQuietly(d, d.getPendingRoom());
                return d.isIngressEnabled() ? 1 : 0; // 실제로 풀렸을 때만 센다 — unlockIngress 는 실패해도 던지지 않는다
            });
            n += done == null ? 0 : done; // tx.execute 는 null 을 돌려줄 수도 있어(자바 타입상) 0 으로 친다
        }
        return n;
    }

    /** 세션 하나 = 트랜잭션 하나. 잠그지 못하면(남이 쥠) 0. LiveKit 조회 실패(ApiException)는 로그만 남기고 다음 분에 */
    // work = 잠근 세션을 받아 처리하고 센 수를 돌려주는 함수(람다). skipMessage = 건너뛸 때 로그 앞머리
    private int each(UUID id, Function<LiveSession, Integer> work, String skipMessage) {
        Integer n;
        try {
            n = eachTx(id, work, skipMessage);
        } catch (RuntimeException e) {
            log.warn("{} {}: {} (다음 분에 다시)", skipMessage, id, e.toString()); // rollback-only·DB 오류 — 이 세션만 건너뛰고 나머지는 계속 (검수 3차)
            return 0;
        }
        return n == null ? 0 : n;
    }

    /**
     * 트랜잭션을 열고 세션 행을 잠근 뒤(이미 남이 쥐었으면 기다리지 않고 0) work 를 돌린다. 람다가 끝나면 커밋된다.
     * LiveKit 오류(ApiException)는 여기서 잡아 로그만 남긴다. 잡은 뒤에도 커밋하므로 예외 전까지 바꾼 값은 저장된다.
     * 커밋 자체가 실패하는 경우(LiveService 메서드 안에서 던져져 묶음이 rollback-only 가 됨 등)는 바깥 each 가 잡는다
     */
    private Integer eachTx(UUID id, Function<LiveSession, Integer> work, String skipMessage) {
        return tx.execute(status -> {
            LiveSession s = sessions.lockByIdIfFree(id).orElse(null); // 웹훅·pause 와 같은 행 — 잠그고 최신 상태로
            if (s == null) return 0;
            try {
                return work.apply(s);
            } catch (ApiException e) {
                log.warn("{} {}: {}", skipMessage, s.getRoomName(), e.getMessage());
                return 0;
            }
        });
    }

    /**
     * 끝나지 않은 세션 하나(잠근 상태)를 LiveKit 의 실제 상태·작업 상태와 맞춘다. 순서:
     * 작업이 끝났으면 세션 종료 → 카메라 참가자·영상 트랙 읽기 → 카메라가 있으면 mute 어긋남 보정·녹화 보장 /
     * 없으면 Ingress 잠금 보정·끊김 알림 → 시청 권한 보정 → 끝난 녹화 반영 → 미뤄 둔 방 바인딩.
     */
    void reconcileOne(LiveSession s) {
        Booking b = bookings.find(s.getBookingId()).orElse(null);
        if (b == null) return;
        ManagerDevice device = b.getManagerId() == null ? null : devices.findById(b.getManagerId()).orElse(null);
        // 작업이 완료·취소됐는데 세션이 아직 열려 있다 — 녹화 정지·방 삭제·바디캠 반납을 여기서 한다 (stopSession, live/stop 과 같은 처리)
        if (b.getStatus() == BookingStatus.DONE || b.getStatus() == BookingStatus.CANCELLED) {
            live.stopSession(s, device);
            return;
        }
        if (device == null) return; // 매니저가 없거나 바디캠이 등록되지 않았다 — LiveKit 에서 볼 카메라가 없다
        // 방 참가자를 한 번만 읽어 카메라 찾기·시청자 수·시청 권한 보정에 같이 쓴다. 바디캠은 identity "camera-매니저id" 로 들어온다
        List<LiveKitGateway.Participant> participants = livekit.listParticipants(s.getRoomName());
        Optional<LiveKitGateway.Participant> cameraParticipant = participants.stream().filter(p -> p.identity().equals(device.cameraIdentity())).findFirst();
        Optional<LiveKitGateway.Track> camera = cameraParticipant.flatMap(LiveKitGateway.Participant::videoTrack);
        cameraParticipant.ifPresent(p -> live.muteAudio(s, device, p)); // 영상만 송출 — 웹훅 뒤에 올라온 오디오 트랙도 (재감사)
        s.setViewerCount((int) participants.stream().filter(p -> p.identity().startsWith(LiveWebhookService.VIEWER_PREFIX)).count()); // 웹훅 유실·역순 보정

        if (camera.isPresent()) {
            LiveKitGateway.Track track = camera.get();
            if (LiveSession.WAITING_CAMERA.equals(s.getStatus())) {
                live.cameraAttached(s, device, track.sid()); // 웹훅을 놓친 경우
            } else {
                // 아래 분기는 세션 상태(live/paused)·mute 요청 표시·작업 상태·실제 트랙의 mute 가 어긋난 경우를 하나씩 맞춘다.
                // 지키려는 것: 작업이 일시정지인데 영상이 나가는 순간을 두지 않고, 작업이 진행 중인데 영상이 막혀 있지 않게 한다.
                // '막아야 함'(block) = 작업이 일시정지거나, 매니저가 영상을 멈추고 아직 재개하지 않았다(videoHeld — 작업 일시정지가 실패한 경우 포함).
                // videoHeld 는 카메라가 끊겼다 다시 붙어도 남으므로, 새 트랙이어도 매니저가 재개할 때까지 막는다 (2026-09-28 사용자 결정).
                // live/pause 직후 PAUSE_GRACE 동안도 풀지 않는다 (아래 paused ∧ in_progress 분기)
                boolean block = LiveService.holdsVideo(b, s);
                if (!track.sid().equals(s.getCameraTrackSid())) s.cameraAttached(track.sid(), track.muted(), clock.now()); // 웹훅 없이 재접속한 새 트랙(옛 sid 로 mute 를 보내면 영영 실패)
                // mute_pending: 막아야 하면 다시 mute, 아니면 버린다. live/pause 가 502 로 끝난 보통의 경우(작업은 진행 중,
                // jobs/pause 는 live 라서 거절됨)는 videoHeld 가 없어 '버린다' 쪽이다 — 앱이 '영상이 계속 전송되고 있다' 고 안내했으므로 영상을 되돌린다.
                // 매니저가 '멈췄다' 는 답을 받은 경우(videoHeld)만 작업이 진행 중이어도 막아 둔다 (자체 점검 #15).
                // (BACKEND_PLAN.md §8.2.1 표의 ① 행과 같다)
                if (s.isMutePending()) {
                    if (!block) {
                        // 작업이 다시 진행 중이면 옛 mute 요청은 버린다 (진행 중 영상을 막지 않게). mute 가 이미 카메라에 먹었으면(확인만 실패) 실제로 다시 켠다 (검수 3차)
                        if (track.muted() && b.getStatus() == BookingStatus.IN_PROGRESS) retryMute(s, device, false);
                        else s.clearMuteRequests();
                    }
                    else if (track.muted()) s.muted(clock.now()); // 이미 막혀 있으면 확정 (mute_pending 이 영원히 남지 않게)
                    else retryMute(s, device, true);
                } else if (s.isUnmutePending()) {
                    if (b.getStatus() == BookingStatus.IN_PROGRESS && !s.isVideoHeld()) { // 작업이 아직 paused 거나 매니저가 다시 멈췄으면 unmute 하지 않는다
                        if (!track.muted()) s.unmuted();
                        else retryMute(s, device, false);
                    }
                } else if (LiveSession.PAUSED.equals(s.getStatus()) && !track.muted()) {
                    retryMute(s, device, true);             // paused 인데 영상이 나감
                } else if (LiveSession.PAUSED.equals(s.getStatus()) && b.getStatus() == BookingStatus.IN_PROGRESS && !s.isVideoHeld() && !justPaused(s)) {
                    retryMute(s, device, false);            // 작업은 진행 중인데 영상이 막힘 (jobs/resume 뒤 live/resume 이 서버에 닿지 않은 경우, 계획 §9.2 — jobs/resume 이 videoHeld 를 풀었다). 방금 paused 가 된 세션은 live/pause → jobs/pause 사이의 창이므로 건너뛴다 (검수 3차).
                                                            // 영상만 막고 jobs/pause 가 실패했으면 videoHeld 가 남아 있어 막아 둔다 — 매니저는 '영상은 중단됐다' 를 믿고 자리를 비웠을 수 있다 (자체 점검 #15).
                                                            // 바디캠이 끊겼다 다시 붙어도 videoHeld 는 남으므로 재접속(cameraAttached·위의 '새 트랙' 줄) 뒤에도 아래 분기가 다시 막는다.
                                                            // 예전에는 '작업 일시정지 시각이 영상 일시정지보다 뒤인가'(jobPausedSinceVideoPause)로 짐작했는데, 일시정지 중 재접속으로 다시 mute 되면
                                                            // 영상 일시정지 시각이 새로 적혀 재개 뒤에도 영영 막혔다 — 이제 매니저의 재개 요청(videoHeld 해제)만 본다
                } else if (LiveSession.LIVE.equals(s.getStatus()) && block && !track.muted()) {
                    retryMute(s, device, true);             // 막아야 하는데 영상이 나감 (카메라 재접속이 jobs/pause 직전에 끼어든 경우, 멈춘 뒤 웹훅 없이 다시 붙은 새 트랙)
                } else if (LiveSession.LIVE.equals(s.getStatus()) && b.getStatus() == BookingStatus.IN_PROGRESS && !s.isVideoHeld() && track.muted()) {
                    retryMute(s, device, false);            // 작업은 진행 중인데 영상이 막힘 — 세션이 live 로 남은 채 트랙만 muted (pause 확인 실패 뒤 재개, 카메라 재접속 뒤 resume 등, 검수 3차)
                }
                live.ensureRecording(s); // 녹화가 없으면 시작 (mute 확인이 남은 동안은 ensureRecording 이 시작하지 않는다)
                device.markOnline(clock.now()); // 매니저 기기 조회(GET /manager/device·/status)의 연결 표시가 이 값을 쓴다
            }
        } else {
            // 카메라가 없다 = Ingress 가 쉬고 있어 잠금을 바꿀 수 있다. 막아야 하면(작업 일시정지·매니저가 멈춤) 잠그고(웹훅 유실·잠금 실패 보정), 아니면 연다 (감사 H-02)
            if (LiveService.holdsVideo(b, s)) live.lockIngress(device);
            else live.unlockIngress(device);
            boolean working = b.getStatus() == BookingStatus.IN_PROGRESS && !s.isVideoHeld(); // 작업 중일 때만 알린다 — 일시정지 중(매니저가 영상을 멈춘 채 포함) 꺼 둔 바디캠은 장애가 아니다 (자체 점검 #4)
            if (s.isCameraOnline()) {
                // 세션은 카메라가 붙어 있다고 아는데 방에는 없다(끊김 웹훅 유실) — 카메라 대기로 바꾸고, 작업 중이면 매니저에게 끊김 알림
                // (아웃박스라 이 세션의 묶음이 커밋돼야 나간다, CODE_GUIDE.md 3-6)
                s.cameraDetached(clock.now());
                device.markOffline();
                if (working) outbox.publish(new CameraOffline(s.getBookingId(), b.getManagerId(), "disconnected"));
            } else if (working && LiveSession.WAITING_CAMERA.equals(s.getStatus()) && s.cameraWaitSince() != null && s.getCameraWaitNotifiedAt() == null
                    && Duration.between(s.cameraWaitSince(), clock.now()).compareTo(CAMERA_WAIT_LIMIT) >= 0) { // 대기 시작(cameraWaitSince)부터 5분
                // 작업 중 카메라 대기가 5분을 넘었고 아직 알리지 않았으면 한 번만 알린다 (알린 시각을 적어 다음 분에 또 보내지 않는다).
                // 대기 시작은 cameraWaitSince: 끊긴 시각(재개 때 다시 잰 시각), 한 번도 붙지 않았으면 세션 시작 시각
                outbox.publish(new CameraOffline(s.getBookingId(), b.getManagerId(), "waiting"));
                s.cameraWaitNotified(clock.now());
                log.warn("바디캠이 5분 넘게 붙지 않음: {}", s.getRoomName());
            }
        }

        live.syncViewerAccess(s, participants); // 참가자마다 실제 권한을 뜻에 맞춘다 — 호출 실패·웹훅 유실·한 명 막기 실패 보정 (재감사 H-02·2차 #1)

        // 녹화가 진행·업로드 중으로 남았는데 LiveKit 에선 이미 끝났으면(egress 웹훅 유실) 결과를 세션에 적는다
        if (s.getEgressId() != null && (LiveSession.REC_RECORDING.equals(s.getRecordingStatus()) || LiveSession.REC_UPLOADING.equals(s.getRecordingStatus()))) {
            livekit.getEgress(s.getEgressId()).filter(LiveKitGateway.EgressInfo::isFinished).ifPresent(info -> webhooks.applyEgress(s, info));
        }
        // 송출 중이라 미뤄 둔 방 바인딩(pending_room)이 있으면 다시 시도한다 — 실패해도 던지지 않고 다음 분에 또
        if (device.getPendingRoom() != null) live.bindIngressQuietly(device, device.getPendingRoom());
    }

    /** live/pause 직후(jobs/pause 가 아직 안 옴)인가 — 이 창에서 unmute 하면 앱 문구('영상은 중단됐지만…')와 실제가 반대가 된다 */
    private boolean justPaused(LiveSession s) {
        return s.getPausedAt() != null && Duration.between(s.getPausedAt(), clock.now()).compareTo(PAUSE_GRACE) < 0;
    }

    /** 확인에 실패하면 pause/resume 과 같은 표시를 남긴다 — 명령은 카메라에 먹었을 수 있으므로 '이미 paused' 로 jobs/pause 가 통과하거나(unmute 실패) 영상이 나가는데 paused 로 남지 않게(mute 실패) (검수 3차) */
    private void retryMute(LiveSession s, ManagerDevice device, boolean muted) {
        if (live.setMuted(s, device, muted)) {
            if (muted) s.muted(clock.now());
            else s.unmuted();
        } else if (muted) s.muteUnconfirmed();
        else s.requestUnmute();
    }
}
