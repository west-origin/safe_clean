package kr.ansimclean.api.live;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * LiveKit 을 부르는 유일한 문 (BACKEND_PLAN.md §8). 서비스는 이 인터페이스만 알고, 구현은 둘:
 *  - LiveKitCloudGateway (app.livekit.mode=cloud): 서버 SDK 로 실제 호출
 *  - LoggingLiveKitGateway (logging, 기본): 아무 것도 부르지 않고 "카메라가 붙지 않는" 상태를 흉내 낸다 (로컬·테스트)
 * proto 타입은 여기서 record 로 바꿔 두어 테스트가 LiveKit 없이 돈다.
 * 어느 구현을 쓸지는 app.livekit.mode(환경변수 LIVEKIT_MODE) 가 정한다. 부르는 쪽 예: LiveService(시작·일시정지·정지), LiveReconciler(1분마다 맞추기).
 * 용어: Ingress = 바디캠 RTMP 영상을 받아 방에 올리는 입구, Egress = 방 영상을 파일로 내보내는 녹화, 방(room) = 영상이 오가는 채널.
 */
public interface LiveKitGateway {
    /** Ingress(바디캠 입구). active = 지금 송출 중(BUFFERING/PUBLISHING) */
    record IngressInfo(String ingressId, String url, String streamKey, String roomName, boolean active) {}

    /** 참가자가 올린 트랙 하나. sid = LiveKit 트랙 id(mute 할 때 쓴다), video = 영상(false 면 오디오), muted = 지금 막혀 있는지 */
    record Track(String sid, boolean video, boolean muted) {}

    /** canSubscribe = 지금 구독 권한(LiveKit ParticipantInfo.permission) — 재조정기가 참가자별로 실제 값과 맞춘다 */
    record Participant(String identity, List<Track> tracks, boolean canSubscribe) {
        /** 구독 권한을 모를 때(테스트·흉내 구현) 쓰는 생성자 — 구독 가능으로 둔다 */
        public Participant(String identity, List<Track> tracks) {
            this(identity, tracks, true);
        }

        /** 첫 번째 영상 트랙 (바디캠 영상). 영상 없이 들어온 참가자면 비어 있다 */
        public Optional<Track> videoTrack() {
            return tracks.stream().filter(Track::video).findFirst();
        }
    }

    /** status: starting | active | ending | complete | failed | aborted | limit_reached */
    // filename = 저장된 파일 경로(끝났을 때), durationSeconds = 녹화 길이(초), error = 실패 사유(없으면 null)
    record EgressInfo(String egressId, String roomName, String status, String filename, long durationSeconds, String error) {
        /** 파일이 다 올라가 녹화본을 쓸 수 있다 */
        public boolean isComplete() {
            return "complete".equals(status);
        }

        /** 실패·중단·한도 초과 — 녹화본이 없다 */
        public boolean isFailed() {
            return "failed".equals(status) || "aborted".equals(status) || "limit_reached".equals(status);
        }

        /** 성공이든 실패든 더 바뀌지 않는 끝 상태 — 웹훅(applyEgress)·재조정기 모두 이때만 녹화 결과를 세션에 반영한다 */
        public boolean isFinished() {
            return isComplete() || isFailed();
        }
    }

    /** 웹훅 1건 (서명 검증 뒤). roomName 은 room·ingress·egress 중 있는 것에서 */
    // track = track_published/track_unpublished 이벤트의 트랙(휴대폰 송출의 카메라 붙음·끊김, §8.0). 다른 이벤트면 null
    record WebhookEvent(String id, String event, String roomName, String participantIdentity, IngressInfo ingress, EgressInfo egress, Track track) {
        /** 트랙이 없는 이벤트 (ingress·egress·participant·room) */
        WebhookEvent(String id, String event, String roomName, String participantIdentity, IngressInfo ingress, EgressInfo egress) {
            this(id, event, roomName, participantIdentity, ingress, egress, null);
        }
    }

    /** Ingress 가 송출 중이라 방을 바꿀 수 없다 (§8.1 cameraBusy) */
    class IngressBusyException extends RuntimeException {
        public IngressBusyException(String message) {
            super(message);
        }
    }

    /**
     * RTMP Ingress 를 새로 만든다 (바디캠 등록 때 — DeviceService). participantIdentity 는 방에 들어갈 때 바디캠의 이름표("camera-매니저id").
     * 결과의 url·streamKey 를 기기에 넣으면 그 방으로 영상이 올라간다
     */
    IngressInfo createIngress(String name, String roomName, String participantIdentity, String participantName);

    /** Ingress 를 다른 방에 묶는다. 송출 중이면 IngressBusyException */
    IngressInfo updateIngressRoom(String ingressId, String roomName);

    /** Ingress 한 개 조회. LiveKit 에 없으면 비어 있다 (지금 묶인 방·송출 중인지를 볼 때) */
    Optional<IngressInfo> getIngress(String ingressId);

    /**
     * Ingress 가 새 연결을 받을지 (감사 H-02). false 면 바디캠이 다시 붙으려 해도 LiveKit 이 거부한다 — 지금 붙어 있는 연결은 끊지 않는다.
     * LiveKit 은 송출 중인 Ingress 를 수정하지 않으므로 송출 중이면 IngressBusyException
     */
    void setIngressEnabled(String ingressId, boolean enabled);

    /** Ingress 삭제(스트림 키 폐기) — 매니저 탈퇴·바디캠 분실 때. 없으면 그대로 */
    void deleteIngress(String ingressId);

    /**
     * 작업 방을 만든다 (라이브 시작 때 LiveService.start). emptyTimeout = 아무도 들어오지 않을 때 방을 열어 두는 초,
     * departureTimeout = 마지막 참가자가 나간 뒤 방을 열어 두는 초 (지나면 LiveKit 이 방을 닫는다)
     */
    void createRoom(String roomName, int emptyTimeoutSeconds, int departureTimeoutSeconds);

    /** 방을 닫는다 — 안에 있던 시청자 연결도 끊긴다. 이미 없으면 그대로 */
    void deleteRoom(String roomName);

    /** 방의 참가자 전부(바디캠·고객 시청자·매니저 미리보기). 방이 없으면 빈 목록 */
    List<Participant> listParticipants(String roomName);

    /** 방의 참가자 한 명. 없으면(아직 안 붙음·나감) 비어 있다 */
    Optional<Participant> getParticipant(String roomName, String identity);

    /**
     * 서버가 참가자의 트랙을 막거나(true) 푼다(false). 일시정지 영상 차단·바디캠 오디오 차단에 쓴다.
     * 요청만 보낸다 — 영상 쪽은 LiveService.setMuted 가 뒤이어 getParticipant 로 정말 바뀌었는지 확인한다
     */
    void mutePublishedTrack(String roomName, String identity, String trackSid, boolean muted);

    /** 참가자의 구독 권한을 바꾼다 (고객 시청자: 일시정지 동안 false). 발행·데이터 권한은 늘 없음 */
    void setCanSubscribe(String roomName, String identity, boolean canSubscribe);

    /** 방 합성 녹화(영상만) → Storage S3 objectKey 로 업로드 */
    EgressInfo startRecording(String roomName, String objectKey);

    /** 녹화를 멈춘다 — 멈춘 뒤 파일 업로드가 이어지고 끝나면 egress 웹훅이 온다. 이미 없으면 그대로 */
    void stopEgress(String egressId);

    /** 녹화 한 건 조회 (웹훅을 놓쳤을 때 재조정기가 끝났는지 본다). 없으면 비어 있다 */
    Optional<EgressInfo> getEgress(String egressId);

    // 매니저 미리보기(LiveService.start)와 고객 시청(LiveService.viewerToken) 둘 다 이 토큰을 받는다. ttl = 토큰 유효 시간
    /** 시청 전용 토큰 (publish 불가) */
    String joinToken(String roomName, String identity, String name, Duration ttl);

    // 휴대폰 송출(LIVE_SOURCE=app, §8.0) 때 매니저 앱이 받는다(LiveService.start). identity 는 바디캠과 같은 "camera-매니저id"
    /** 송출 토큰 — 이 방에 카메라 영상만 publish 가능, 구독·데이터 발행 불가 */
    String publishToken(String roomName, String identity, String name, Duration ttl);

    /** 앱이 connect 할 wss URL */
    String url();

    /** 서명 검증 + 파싱. 실패하면 ApiException(UNAUTHENTICATED) */
    WebhookEvent parseWebhook(String body, String authorizationHeader);
}
