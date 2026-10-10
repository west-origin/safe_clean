package kr.ansimclean.api.live;

import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LiveKit 없이 도는 대역 (app.livekit.mode=logging, 기본). 카메라는 영원히 붙지 않고, 방·녹화·mute 는 로그만 남긴다.
 * 로컬에서 예약 → 콜 → 시작 → 완료 흐름을 영상 없이 끝까지 돌릴 수 있게 한다. 실제 확인은 cloud 모드 + ffmpeg/바디캠(§8.1).
 * 아래 ConditionalOnProperty = app.livekit.mode(환경변수 LIVEKIT_MODE)가 logging 이거나 아예 없을 때만 이 클래스를 빈으로 만든다.
 * cloud 면 LiveKitCloudGateway 가 대신 쓰인다. 테스트 프로필(application-test.yml)도 logging 으로 고정돼 있다.
 */
@Component
@ConditionalOnProperty(name = "app.livekit.mode", havingValue = "logging", matchIfMissing = true)
public class LoggingLiveKitGateway implements LiveKitGateway {
    private static final Logger log = LoggerFactory.getLogger(LoggingLiveKitGateway.class);

    // 만든 Ingress 를 메모리에만 기억한다(서버를 끄면 사라진다). ConcurrentHashMap = 여러 요청이 동시에 써도 안전한 Map
    private final Map<String, IngressInfo> ingresses = new ConcurrentHashMap<>();

    // 서버가 뜰 때 한 번: 운영에서 설정을 빠뜨린 것을 알아채도록 경고 로그를 남긴다
    public LoggingLiveKitGateway() {
        log.warn("LIVEKIT_MODE 미설정 → LiveKit 없이 동작합니다(카메라가 붙지 않고 웹훅은 401). 운영이라면 LIVEKIT_MODE=cloud + 키를 설정하세요");
    }

    @Override
    public IngressInfo createIngress(String name, String roomName, String participantIdentity, String participantName) {
        // 가짜 Ingress: id "local-…", 로컬 RTMP 주소, 무작위 키. 이 주소로 영상을 보내도 받는 곳은 없다
        IngressInfo info = new IngressInfo("local-" + UUID.randomUUID(), "rtmp://localhost/live", "key-" + UUID.randomUUID(), roomName, false);
        ingresses.put(info.ingressId(), info);
        log.info("LiveKit(logging) Ingress 생성 {} → {}", name, roomName);
        return info;
    }

    @Override
    public IngressInfo updateIngressRoom(String ingressId, String roomName) {
        // 방 이름만 바꿔 다시 기억한다. 모르는 id 면 가짜 값으로 새로 만든다. 늘 송출 중이 아니므로 IngressBusyException 은 던지지 않는다
        IngressInfo info = ingresses.getOrDefault(ingressId, new IngressInfo(ingressId, "rtmp://localhost/live", "key", roomName, false));
        IngressInfo moved = new IngressInfo(info.ingressId(), info.url(), info.streamKey(), roomName, false);
        ingresses.put(ingressId, moved);
        log.info("LiveKit(logging) Ingress {} → 방 {}", ingressId, roomName);
        return moved;
    }

    @Override
    public Optional<IngressInfo> getIngress(String ingressId) {
        // 이 서버가 메모리에 기억한 것만. 재시작 뒤에는 비어 있다
        return Optional.ofNullable(ingresses.get(ingressId));
    }

    // 잠금·풀기: 로그만 남긴다 (잠금 상태를 기억하지 않는다)
    @Override
    public void setIngressEnabled(String ingressId, boolean enabled) {
        log.info("LiveKit(logging) Ingress {} {}", ingressId, enabled ? "열기" : "잠그기");
    }

    // 메모리에서만 지운다
    @Override
    public void deleteIngress(String ingressId) {
        ingresses.remove(ingressId);
    }

    // 방 만들기·지우기: 로그만 남긴다
    @Override
    public void createRoom(String roomName, int emptyTimeoutSeconds, int departureTimeoutSeconds) {
        log.info("LiveKit(logging) 방 생성 {}", roomName);
    }

    @Override
    public void deleteRoom(String roomName) {
        log.info("LiveKit(logging) 방 삭제 {}", roomName);
    }

    // 참가자 조회는 둘 다 늘 비어 있다 — 방에 아무도 없는 것으로 보이고, 바디캠 영상 트랙도 찾지 못한다
    @Override
    public List<Participant> listParticipants(String roomName) {
        return List.of();
    }

    @Override
    public Optional<Participant> getParticipant(String roomName, String identity) {
        return Optional.empty();
    }

    // 트랙 mute·구독 권한: 로그만 남긴다
    @Override
    public void mutePublishedTrack(String roomName, String identity, String trackSid, boolean muted) {
        log.info("LiveKit(logging) {} {} muted={}", roomName, trackSid, muted);
    }

    @Override
    public void setCanSubscribe(String roomName, String identity, boolean canSubscribe) {
        log.info("LiveKit(logging) {} {} canSubscribe={}", roomName, identity, canSubscribe);
    }

    @Override
    public EgressInfo startRecording(String roomName, String objectKey) {
        // 파일은 만들지 않고 "active"(녹화 중) 상태의 가짜 녹화 정보만 돌려준다
        log.info("LiveKit(logging) 녹화 시작 {} → {}", roomName, objectKey);
        return new EgressInfo("local-egress-" + roomName, roomName, "active", null, 0, null);
    }

    @Override
    public void stopEgress(String egressId) { // 로그만 남긴다
        log.info("LiveKit(logging) 녹화 정지 {}", egressId);
    }

    @Override
    public Optional<EgressInfo> getEgress(String egressId) {
        // 녹화 조회는 늘 비어 있다 — 재조정기가 이 방법으로는 녹화가 끝났는지 알 수 없다
        return Optional.empty();
    }

    @Override
    public String joinToken(String roomName, String identity, String name, Duration ttl) {
        // 서명 없는 가짜 토큰 — 이것으로 실제 LiveKit 에 접속할 수는 없다
        return "local-token-" + identity;
    }

    @Override
    public String publishToken(String roomName, String identity, String name, Duration ttl) {
        // 서명 없는 가짜 송출 토큰 — 실제 LiveKit 에 접속할 수는 없다
        return "local-publish-token-" + identity;
    }

    @Override
    public String url() {
        // 앱에 알려 줄 접속 주소 자리를 채우는 로컬 주소
        return "ws://localhost:7880";
    }

    @Override
    public WebhookEvent parseWebhook(String body, String authorizationHeader) {
        // 서명을 확인할 키가 없으므로 어떤 웹훅도 받지 않는다 — UNAUTHENTICATED 라 응답은 401
        throw new ApiException(ErrorCode.UNAUTHENTICATED, "LiveKit 이 설정되지 않았어요 (logging 모드)");
    }
}
