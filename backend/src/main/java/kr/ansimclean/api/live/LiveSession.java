package kr.ansimclean.api.live;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * live_sessions — 예약 1건 = 방 1개 (PK 가 booking_id 라 재생성하지 않고 리셋한다, §6.1). 상태는 메서드로만 (§8.1 전이).
 *  created → (start) waiting_camera → (바디캠 붙음) live ⇄ paused → (stop) ended
 * 되돌아가는 길: 바디캠이 끊기면 live·paused → waiting_camera, 끝난 뒤 다시 start 하면 ended → waiting_camera, 배정·일정 변경 때 reset 으로 created.
 * 녹화 상태(recording_status)는 영상 상태와 따로 간다: none → recording → (uploading) → ready | failed, 지우면 다시 none.
 * 값을 바꾸는 곳 예: LiveService(시작·일시정지·재개·정지), LiveReconciler(1분 재조정). 웹훅·배정·녹화본 삭제 쪽도 같은 전이 메서드를 부른다.
 */
@Entity
@Table(name = "live_sessions")
public class LiveSession {
    // status 칸의 값 (영상 상태)
    public static final String CREATED = "created"; // 배정됨, 라이브 시작 전
    public static final String WAITING_CAMERA = "waiting_camera"; // 방을 만들고 바디캠(영상 트랙)을 기다림 — 끊긴 뒤에도 여기로
    public static final String LIVE = "live"; // 영상이 나가는 중 (mute 를 보냈지만 확인 못 한 경우도 여기)
    public static final String PAUSED = "paused"; // mute 가 확인돼 영상이 막힘
    public static final String ENDED = "ended"; // 끝남 — 작업이 끝나 방을 닫았다

    // recording_status 칸의 값 (녹화 상태)
    public static final String REC_NONE = "none"; // 녹화 없음 (시작 전이거나 지움)
    public static final String REC_RECORDING = "recording"; // 녹화 중
    public static final String REC_UPLOADING = "uploading"; // 정지 명령 뒤 파일을 올리는 중
    public static final String REC_READY = "ready"; // 파일이 올라가 재생할 수 있음
    public static final String REC_FAILED = "failed"; // 녹화 실패 — 녹화본 없음

    // @Id = 기본 키(행을 가리키는 값). @Column = 이 필드가 대응하는 칸 (name 이 없으면 필드 이름, nullable=false 는 비울 수 없음)
    @Id
    @Column(name = "booking_id", columnDefinition = "uuid")
    private UUID bookingId; // 예약 id 가 곧 세션 id — 예약 하나에 세션 하나

    @Column(name = "room_name", columnDefinition = "text", nullable = false)
    private String roomName; // LiveKit 방 이름 'booking-예약id'

    @Column(columnDefinition = "text", nullable = false)
    private String status;

    @Column(name = "ingress_id", columnDefinition = "text")
    private String ingressId; // 라이브 시작 때 적어 둔 그 매니저 바디캠의 Ingress id

    /** 바디캠이 마지막으로 붙은 시각. null 이면 이 세션에서 아직 한 번도 안 붙음 — 첫 연결 푸시를 가른다(reset 때만 지운다) */
    @Column(name = "camera_online_at")
    private Instant cameraOnlineAt;
    /** paused 로 확정된 시각 — 재조정기가 방금 paused 가 된 세션(jobs/pause 가 곧 따라옴)을 unmute 하지 않게 */
    @Column(name = "paused_at")
    private Instant pausedAt;

    @Column(name = "camera_track_sid", columnDefinition = "text")
    private String cameraTrackSid; // 바디캠 영상 트랙의 LiveKit id — mute/unmute 명령에 쓴다. 카메라가 없으면 null

    @Column(name = "mute_pending", nullable = false)
    private boolean mutePending; // 영상을 막아 달라는 요청이 아직 확인되지 않았다 — 카메라가 붙을 때·재조정기가 이어서 처리

    @Column(name = "unmute_pending", nullable = false)
    private boolean unmutePending; // 영상을 다시 켜 달라는 요청이 아직 확인되지 않았다 — 재조정기가 이어서 처리

    /** 작업 중 바디캠이 끊긴 시각 — 5분 대기 푸시는 여기서부터 잰다 (없으면 세션 시작 시각) */
    @Column(name = "camera_offline_at")
    private Instant cameraOfflineAt;

    @Column(name = "camera_wait_notified_at")
    private Instant cameraWaitNotifiedAt; // '바디캠이 5분 넘게 안 붙음' 푸시를 보낸 시각 — 같은 대기 구간에 한 번만 보내려고

    @Column(name = "started_at")
    private Instant startedAt; // 라이브를 시작한(방을 만든) 시각

    @Column(name = "ended_at")
    private Instant endedAt; // 끝난 시각 — 녹화본 보존 기간(RecordingRetention)을 여기서부터 잰다

    @Column(name = "egress_id", columnDefinition = "text")
    private String egressId; // 녹화(Egress) 한 건의 LiveKit id

    @Column(name = "recording_status", columnDefinition = "text", nullable = false)
    private String recordingStatus;

    @Column(name = "recording_path", columnDefinition = "text")
    private String recordingPath; // 녹화 버킷 안 파일 경로 (준비됐을 때만)

    @Column(name = "duration_seconds")
    private Integer durationSeconds; // 녹화본 길이(초). Integer 라 비어 있을(null) 수 있다

    @Column(name = "recording_started_at")
    private Instant recordingStartedAt; // 실제 파일 녹화 시작 시각. 요청 시각이면 confirmed=false (V053)
    @Column(name = "recording_start_confirmed", nullable = false)
    private boolean recordingStartConfirmed;

    @Column(name = "viewer_count", nullable = false)
    private int viewerCount; // 지금 보고 있는 고객 수 — 웹훅으로 늘고 줄며, 재조정기가 참가자 목록으로 바로잡는다

    /** 작업 일시정지(jobs/pause)가 된 마지막 시각 (V034, 자체 점검 #15) */
    @Column(name = "job_paused_at")
    private Instant jobPausedAt;

    /** 고객 참가자의 구독 권한을 막아 두었는가 — 일시정지 동안 (재감사 H-02, V030) */
    @Column(name = "viewers_blocked", nullable = false)
    private boolean viewersBlocked;

    // 매니저가 영상 일시정지를 '성공' 으로 안내받았고 아직 재개하지 않았다 (V045). 카메라가 끊겼다 다시 붙어도 남아서,
    // 재접속·재조정기가 작업 상태(진행 중)만 보고 영상을 다시 내보내지 않게 한다. 지우는 곳은 매니저의 재개 요청뿐(live/resume·jobs/resume)
    @Column(name = "video_held", nullable = false)
    private boolean videoHeld;

    // JPA 가 DB 행을 읽어 객체를 만들 때 쓰는 빈 생성자. 코드에서 새 세션을 만들 때는 create() 를 쓴다
    protected LiveSession() {}

    /** 배정 시: 방 이름 'booking-<id>', status created */
    // 부르는 곳: LiveSessionService.ensureCreated(배정), LiveService.start(행이 없을 때). 저장은 호출한 쪽이 save 로 한다
    public static LiveSession create(UUID bookingId) {
        LiveSession s = new LiveSession();
        s.bookingId = bookingId;
        s.roomName = "booking-" + bookingId;
        s.status = CREATED;
        s.recordingStatus = REC_NONE;
        return s;
    }

    // 읽기용 getter. 값은 아래 전이 메서드로 바꾼다 (단순 setter 는 시청자 수·구독 차단 표시처럼 상태 규칙이 없는 값에만 둔다)
    public UUID getBookingId() { return bookingId; }
    public String getRoomName() { return roomName; }
    public String getStatus() { return status; }
    public String getIngressId() { return ingressId; }
    public Instant getCameraOnlineAt() { return cameraOnlineAt; }
    public Instant getPausedAt() { return pausedAt; }
    public Instant getCameraOfflineAt() { return cameraOfflineAt; }
    public String getCameraTrackSid() { return cameraTrackSid; }
    public boolean isMutePending() { return mutePending; }
    public boolean isUnmutePending() { return unmutePending; }
    public Instant getCameraWaitNotifiedAt() { return cameraWaitNotifiedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public String getEgressId() { return egressId; }
    public String getRecordingStatus() { return recordingStatus; }
    public String getRecordingPath() { return recordingPath; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public Instant getRecordingStartedAt() { return recordingStartedAt; }
    public boolean isRecordingStartConfirmed() { return recordingStartConfirmed; }
    public int getViewerCount() { return viewerCount; }

    /** 영상이 고객에게 나가고 있는가 (live). 일시정지 선행 조건(§8.2.1)은 이것이 아니라 아래 mayBeStreaming 이다. paused = mute 가 확인된 상태 */
    public boolean isStreaming() {
        return LIVE.equals(status);
    }

    /**
     * 영상이 나가고 있을 수 있는가 — 작업 일시정지의 선행 조건에 쓴다.
     * paused 여도 unmute_pending 이면 재개 요청(unmute)이 이미 카메라에 먹었는데 확인만 못 한 것일 수 있으므로 '나갈 수 있음' 으로 본다.
     */
    public boolean mayBeStreaming() {
        return LIVE.equals(status) || (PAUSED.equals(status) && unmutePending);
    }

    /** 바디캠이 붙어 있는가 */
    public boolean isCameraOnline() {
        return LIVE.equals(status) || PAUSED.equals(status);
    }

    public boolean isEnded() {
        return ENDED.equals(status);
    }

    /** 방이 열려 있는 상태인가 (카메라 대기·진행·일시정지). created·ended 는 아니다 */
    public boolean isActive() {
        return WAITING_CAMERA.equals(status) || LIVE.equals(status) || PAUSED.equals(status);
    }

    /** 녹화본을 재생할 수 있는가 */
    public boolean isRecordingReady() {
        return REC_READY.equals(recordingStatus);
    }

    /** 녹화가 돌고 있거나 업로드 중 — 곧 ready 가 될 수 있다 */
    // 방이 열려 있는데(카메라 대기·진행·일시정지) 아직 녹화 전(none)인 것도 '곧 생길 수 있음' 으로 본다
    public boolean isRecordingPending() {
        return REC_RECORDING.equals(recordingStatus) || REC_UPLOADING.equals(recordingStatus) || (isActive() && REC_NONE.equals(recordingStatus)); // created 는 라이브를 시작한 적이 없어 영영 안 끝난다 — 대기로 보면 고객 앱이 폴링을 멈추지 못한다
    }

    /** 일정 변경·재배정: created 로 되돌린다 */
    // 부르는 곳: LiveSessionService.ensureCreated. 녹화 관련 값(egressId·recordingStatus·recordingPath)과 viewersBlocked 등은 건드리지 않는다
    public void reset() {
        status = CREATED;
        cameraOnlineAt = null;
        cameraTrackSid = null;
        mutePending = false;
        unmutePending = false;
        cameraWaitNotifiedAt = null;
        startedAt = null;
        endedAt = null;
        viewerCount = 0;
        videoHeld = false; // 일정이 바뀌어 새로 준비하는 세션 — 옛 일시정지를 잇지 않는다
    }

    // ── 전이 (§8.1) ──

    /** live/start: 방을 만들고 바디캠을 기다린다 */
    public void startWaiting(String ingressId, Instant now) {
        // 시작 전·끝난 세션만 대기로 바꾸고 시작 시각을 새로 적는다. 이미 열린 세션이면 상태·시각은 그대로 두고 Ingress id 만 적는다
        if (CREATED.equals(status) || ENDED.equals(status)) {
            status = WAITING_CAMERA;
            startedAt = now;
            endedAt = null;
        }
        this.ingressId = ingressId;
    }

    /**
     * 트랙이 붙었다. muted 는 **확인된** 트랙 상태다 — 작업이 paused 라고 해서 paused 로 적지 않는다
     * (mute 를 확인하지 못했는데 paused 로 적으면 '일시정지인데 영상이 나가는' 구간이 생긴다, §8.2.1). 호출자가 mute 를 확인한 뒤 muted()/requestMute() 로 잇는다.
     */
    public void cameraAttached(String trackSid, boolean muted, Instant now) {
        cameraOnlineAt = now;
        cameraOfflineAt = null;
        cameraTrackSid = trackSid;
        status = muted ? PAUSED : LIVE;
        if (muted) {
            mutePending = false; // 이미 막혀 있으니 막아 달라는 요청은 이뤄졌다
            pausedAt = now;
        }
        cameraWaitNotifiedAt = null; // 붙었으니 다음에 끊기면 5분 대기 푸시를 다시 보낼 수 있게
    }

    /** ingress_ended 인데 작업이 아직 진행 중: 다시 기다린다 */
    public void cameraDetached(Instant now) {
        if (isCameraOnline()) status = WAITING_CAMERA;
        cameraTrackSid = null;
        cameraOfflineAt = now;
        cameraWaitNotifiedAt = null; // 다시 5분을 잰다
    }

    /** 일시정지가 끝났는데 카메라가 없다 — 대기 시간을 지금부터 다시 잰다 (일시정지 동안 꺼 둔 시간은 장애가 아니다, 자체 점검 #4) */
    public void restartCameraWait(Instant now) {
        cameraOfflineAt = now;
        cameraWaitNotifiedAt = null;
    }

    /** 카메라 대기가 언제부터인가 (끊긴 시각, 없으면 세션 시작) */
    public Instant cameraWaitSince() {
        return cameraOfflineAt != null ? cameraOfflineAt : startedAt;
    }

    /** live/pause 가 mute 를 확인했다 */
    public void muted(Instant now) {
        status = PAUSED;
        mutePending = false;
        unmutePending = false;
        pausedAt = now;
    }

    /** live/resume 이 unmute 를 확인했다 */
    public void unmuted() {
        status = LIVE;
        mutePending = false;
        unmutePending = false;
    }

    /** mute 확인 실패·카메라 없음: 붙는 즉시(또는 재조정기가) mute */
    // 단, 그때 작업(예약)이 일시정지가 아니면 이 요청은 mute 되지 않고 버려진다 (LiveService.cameraAttached·재조정기의 clearMuteRequests)
    public void requestMute() {
        mutePending = true;
        unmutePending = false;
    }

    /** mute 를 보냈지만 확인하지 못했다: 영상이 나가고 있다고 본다(live) — paused+mute_pending 이면 mayBeStreaming 이 false 라 jobs/pause 가 통과해 §8.2.1 을 깬다 */
    // 이 요청도 작업이 진행 중이면 재조정기가 다시 mute 하지 않고 버린다(mute 가 실제로 먹었으면 unmute) — LiveReconciler.reconcileOne
    public void muteUnconfirmed() {
        status = LIVE;
        mutePending = true;
        unmutePending = false;
    }

    /** unmute 확인 실패: 재조정기가 다시 시도 */
    public void requestUnmute() {
        unmutePending = true;
        mutePending = false;
    }

    /** 카메라가 안 붙은 채로 재개: 아무것도 막을 게 없다 */
    // 재개 때만이 아니라, 작업이 진행 중일 때 카메라가 다시 붙거나(LiveService.cameraAttached) 재조정기가 옛 요청을 버릴 때도 쓴다
    public void clearMuteRequests() {
        mutePending = false;
        unmutePending = false;
    }

    // ── 녹화 (§8.2) ──

    /** 녹화(Egress)를 시작했다 — LiveService.ensureRecording, 또는 서버가 egressId 를 아직 모를 때 온 egress 웹훅(LiveWebhookService) */
    public void recordingStarted(String egressId) {
        if (!java.util.Objects.equals(this.egressId, egressId)) {
            recordingStartedAt = null;
            recordingStartConfirmed = false;
        }
        this.egressId = egressId;
        this.recordingStatus = REC_RECORDING;
    }

    /** exact는 LiveKit 파일의 시작 시각이다. 웹훅·종료 응답·재조정기가 같은 Egress를 확인한 뒤 부른다. */
    public void noteRecordingStart(Instant at, boolean exact) {
        if (at == null) return;
        if (exact || recordingStartedAt == null) recordingStartedAt = at;
        if (exact) recordingStartConfirmed = true;
    }

    /** 정지 명령을 보냈다: 녹화 중이던 것만 '업로드 중' 으로. 파일이 다 올라가면 웹훅·재조정기가 ready/failed 로 적는다 */
    public void recordingStopping() {
        if (REC_RECORDING.equals(recordingStatus)) recordingStatus = REC_UPLOADING;
    }

    /** 파일이 다 올라갔다: 경로와 길이(초)를 적는다. 음수 길이는 0 으로 */
    public void recordingReady(String path, long seconds) {
        recordingStatus = REC_READY;
        recordingPath = path;
        durationSeconds = (int) Math.max(0, seconds);
    }

    /** 녹화가 실패·중단됐다 — 녹화본이 없다 */
    public void recordingFailed() {
        recordingStatus = REC_FAILED;
    }

    /** Egress가 발급되기 전의 시작 실패. 종료된 녹화 실패와 달리 재조정기가 다시 시도할 수 있다. */
    public boolean isRecordingStartFailed() {
        return REC_FAILED.equals(recordingStatus) && egressId == null;
    }

    /** 보존 기간 뒤 삭제 */
    // 탈퇴 정리(AccountMediaDeletionHandler, LiveService.discardRecordingOfDeletedCustomer)도 이걸 쓴다. Storage 파일은 호출한 쪽이 먼저 지운다
    public void recordingDeleted() {
        recordingStatus = REC_NONE;
        recordingPath = null;
        durationSeconds = null;
        recordingStartedAt = null;
        recordingStartConfirmed = false;
    }

    /**
     * 세션을 끝낸다: ended 로, 트랙·mute 요청·시청자 수·구독 차단 표시를 비운다. 녹화 상태는 그대로 둔다(업로드가 끝나면 웹훅·재조정기가 채운다).
     * 부르는 곳: LiveService.stopSession, room_finished 웹훅(작업이 done 일 때)
     */
    public void end(Instant now) {
        status = ENDED;
        endedAt = now;
        cameraTrackSid = null;
        mutePending = false;
        unmutePending = false;
        viewerCount = 0;
        viewersBlocked = false;
        videoHeld = false;
    }

    /** jobs/pause 가 작업을 일시정지한 시각을 적는다 (LiveSessionService.jobPaused). 기록만 남긴다 — 재개 판단은 videoHeld 가 한다 */
    public void jobPaused(Instant now) { jobPausedAt = now; }

    /** 매니저가 영상을 멈췄고 아직 재개하지 않았는가 (V045) — 이 동안은 작업 상태와 관계없이 영상·고객 시청을 막는다 */
    public boolean isVideoHeld() { return videoHeld; }

    /** live/pause 가 매니저에게 '멈췄다' 고 답할 때 (LiveService.pause) */
    public void holdVideo() { videoHeld = true; }

    /** 매니저의 재개 요청(live/resume·jobs/resume)이 서버에 닿았을 때만 부른다 */
    public void releaseVideo() { videoHeld = false; }

    /** 고객 구독을 막아 둔다는 '뜻' 이 적혀 있는가 (실제 LiveKit 권한은 LiveService.setViewerAccess·재조정기가 맞춘다) */
    public boolean isViewersBlocked() { return viewersBlocked; }

    public void setViewersBlocked(boolean blocked) { viewersBlocked = blocked; }

    /**
     * 고객의 구독을 막아야 하는가: 작업이 일시정지거나, 영상이 일시정지거나, mute 를 요청했는데 아직 확인하지 못했을 때 — 재개 요청 중이면 아니다.
     * 이미 들어와 있는 고객에게, 일시정지 중 다시 붙은 바디캠의 새 트랙(아직 mute 전)이 흘러가지 않게 (재감사 H-02).
     * @param jobPaused 예약(작업)이 일시정지인가. 세션 상태만 보면, 일시정지 중 카메라가 끊겨 waiting_camera 가 되는 순간 '막을 뜻' 이 사라져
     *                  다음 동기화가 구독을 다시 열었다 — 바로 그 뒤의 재접속이 막아야 할 상황이다 (재감사 2차 #1)
     */
    public boolean shouldBlockViewers(boolean jobPaused) {
        if (videoHeld) return true; // 매니저가 멈춘 뒤 재개하지 않았다 — 카메라 재접속 중이어도 막는다
        if (unmutePending) return false;
        return jobPaused || PAUSED.equals(status) || mutePending;
    }

    // 시청자 수: participant_joined/left 웹훅이 하나씩 늘리고 줄인다. 웹훅이 빠지거나 순서가 뒤바뀌어도 0 아래로 내려가지 않게 하고,
    // 재조정기가 방의 실제 고객 참가자 수로 덮어쓴다(setViewerCount)
    public void viewerJoined() {
        viewerCount++;
    }

    public void viewerLeft() {
        viewerCount = Math.max(0, viewerCount - 1);
    }

    public void setViewerCount(int n) {
        viewerCount = Math.max(0, n);
    }

    /** '바디캠이 5분 넘게 안 붙음' 푸시를 보냈다 — 재조정기가 같은 대기 구간에 다시 보내지 않게 */
    public void cameraWaitNotified(Instant now) {
        cameraWaitNotifiedAt = now;
    }
}
