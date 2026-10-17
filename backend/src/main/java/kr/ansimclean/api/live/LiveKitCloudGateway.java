package kr.ansimclean.api.live;

import io.livekit.server.AccessToken;
import io.livekit.server.AudioMixing;
import io.livekit.server.CanPublish;
import io.livekit.server.CanPublishData;
import io.livekit.server.CanPublishSources;
import io.livekit.server.CanSubscribe;
import io.livekit.server.EgressServiceClient;
import io.livekit.server.IngressAdmin;
import io.livekit.server.IngressServiceClient;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import io.livekit.server.RoomServiceClient;
import io.livekit.server.WebhookReceiver;
import kr.ansimclean.api.common.config.AppProperties;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import livekit.LivekitEgress;
import livekit.LivekitIngress;
import livekit.LivekitModels;
import livekit.LivekitWebhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * LiveKit Cloud 서버 SDK 래핑 (BACKEND_PLAN.md §8.1·§8.2). 실패는 UPSTREAM 으로, 404 는 Optional.empty 로.
 * Ingress 는 RTMP 입력·transcoding 켬(바디캠 코덱을 그대로 믿지 않는다), 녹화는 방 합성 MP4 영상만(§13-23) → Supabase Storage S3.
 * SDK 메서드는 바로 호출하지 않고 요청 객체(retrofit Call)를 돌려준다 — 아래 call()/callIgnoringNotFound() 가 실행하고 오류를 바꾼다.
 * 예외: setIngressEnabled 는 SDK 에 없어 HttpClient 로 직접 보내고, joinToken·parseWebhook 은 LiveKit 을 부르지 않고 서버에서 서명·검증만 한다.
 * LivekitIngress·LivekitEgress 같은 livekit.* 클래스는 LiveKit 의 protobuf(이진 메시지 형식) 타입이다. 밖으로는 LiveKitGateway 의 record 로만 내보낸다.
 */
@Component
// 설정 app.livekit.mode 가 "cloud" 일 때만 스프링이 이 클래스를 만든다. "logging" 이거나 값이 없으면 LoggingLiveKitGateway 가 대신한다
// (그 밖의 값이면 둘 다 만들어지지 않아 LiveKitGateway 를 받는 서비스가 생기지 못해 서버가 켜지지 않는다)
@ConditionalOnProperty(name = "app.livekit.mode", havingValue = "cloud")
public class LiveKitCloudGateway implements LiveKitGateway {
    private static final Logger log = LoggerFactory.getLogger(LiveKitCloudGateway.class);
    // 방 합성 녹화의 화면 배치 이름 (LiveKit 이 제공하는 레이아웃 중 하나)
    static final String RECORDING_LAYOUT = "single-speaker";

    private final RoomServiceClient rooms;
    private final IngressServiceClient ingress;
    // SDK 에 없는 호출(setIngressEnabled)을 직접 보내는 HTTP 클라이언트. 연결은 5초 안에 못 맺으면 실패로 본다
    private final HttpClient twirp = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final EgressServiceClient egress;
    private final WebhookReceiver webhooks;
    private final AppProperties.Livekit livekit;
    private final AppProperties.Storage storage;

    /**
     * 설정값으로 SDK 클라이언트(방·Ingress·녹화)와 웹훅 검증기를 만든다. 앱용 주소는 wss:// 이지만 서버 API 는 https:// 로 부른다(httpUrl).
     * 모두 같은 API 키·비밀값을 쓴다 — 비밀값은 환경변수로만 들어온다
     */
    public LiveKitCloudGateway(AppProperties props) {
        this.livekit = props.livekit();
        this.storage = props.storage();
        String host = livekit.httpUrl();
        this.rooms = RoomServiceClient.Companion.create(host, livekit.apiKey(), livekit.apiSecret());
        this.ingress = IngressServiceClient.Companion.create(host, livekit.apiKey(), livekit.apiSecret());
        this.egress = EgressServiceClient.Companion.create(host, livekit.apiKey(), livekit.apiSecret());
        this.webhooks = new WebhookReceiver(livekit.apiKey(), livekit.apiSecret());
    }

    @Override
    public IngressInfo createIngress(String name, String roomName, String participantIdentity, String participantName) {
        // SDK 인자 순서: …, 입력 종류(RTMP), 오디오 옵션(null = 기본), 영상 옵션(null = 기본), bypassTranscoding=false, enableTranscoding=true
        LivekitIngress.IngressInfo info = call("Ingress 생성", ingress.createIngress(name, roomName, participantIdentity, participantName,
                LivekitIngress.IngressInput.RTMP_INPUT, null, null, false, true));
        return toInfo(info);
    }

    /**
     * 먼저 읽어서: LiveKit 에 없으면 UPSTREAM, 이미 그 방이면 아무것도 하지 않고, 송출 중이면 busy 예외, 아니면 방 이름만 바꾼다.
     * busy 를 받은 LiveService.bindIngress 는 pending_room 에 적어 두고, 카메라가 끊긴 뒤(웹훅·재조정기) 다시 묶는다
     */
    @Override
    public IngressInfo updateIngressRoom(String ingressId, String roomName) {
        IngressInfo current = getIngress(ingressId).orElseThrow(() -> new ApiException(ErrorCode.UPSTREAM, "바디캠 Ingress 를 찾지 못했어요"));
        if (current.roomName().equals(roomName)) return current;
        if (current.active()) throw new IngressBusyException("Ingress 가 송출 중이라 방을 바꿀 수 없어요: " + ingressId);
        try {
            // updateIngress(ingressId, 이름, 방, 참가자 identity, 참가자 이름) — 방 이름만 넘긴다
            return toInfo(call("Ingress 방 변경", ingress.updateIngress(ingressId, null, roomName, null, null)));
        } catch (ApiException e) {
            // call() 은 오류 본문을 로그에만 남기고 UPSTREAM 만 던지므로 문구로는 busy 를 알 수 없다 — 조회와 변경 사이에 바디캠이 켜졌으면 한 번 더 읽어 판정한다 (검수 3차)
            boolean nowActive;
            try {
                nowActive = getIngress(ingressId).map(IngressInfo::active).orElse(false);
            } catch (ApiException ignored) {
                nowActive = false; // 다시 읽기도 실패하면 busy 로 단정하지 않고 원래 오류(UPSTREAM)를 던진다
            }
            if (nowActive) throw new IngressBusyException("Ingress 가 송출 중이라 방을 바꿀 수 없어요: " + ingressId);
            throw e;
        }
    }

    /**
     * SDK(0.16)의 updateIngress 에는 enabled 인자가 없어 Twirp(`/twirp/livekit.Ingress/UpdateIngress`)를 protobuf 로 직접 부른다.
     * 다른 필드는 비워 두면 LiveKit 이 바꾸지 않는다. 송출 중이면 LiveKit 이 거부 → 다시 읽어 busy 로 판정한다
     * Twirp = LiveKit 서버 API 의 HTTP 호출 규칙: POST 로 서비스·메서드 경로를 부르고 본문은 protobuf 바이트.
     * 부르는 곳 예: LiveService.lockIngress(잠금, false)·unlockIngress(해제, true)
     */
    @Override
    public void setIngressEnabled(String ingressId, boolean enabled) {
        // 이 호출에만 쓰는 서버용 토큰: Ingress 관리 권한(IngressAdmin)만 담고 5분 뒤 만료된다
        AccessToken token = new AccessToken(livekit.apiKey(), livekit.apiSecret());
        token.setTtl(Duration.ofMinutes(5).toMillis());
        token.addGrants(new IngressAdmin(true));
        byte[] body = LivekitIngress.UpdateIngressRequest.newBuilder().setIngressId(ingressId).setEnabled(enabled).build().toByteArray();
        HttpRequest request = HttpRequest.newBuilder(URI.create(livekit.httpUrl() + "/twirp/livekit.Ingress/UpdateIngress"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token.toJwt())
                .header("Content-Type", "application/protobuf")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        // 2xx 면 error 는 null(성공). 그 외 응답은 본문을 오류 문구로 읽어 두었다가, busy 가 아니면 아래에서 로그에만 남긴다
        int status;
        String error;
        try {
            HttpResponse<byte[]> r = twirp.send(request, HttpResponse.BodyHandlers.ofByteArray());
            status = r.statusCode();
            error = status / 100 == 2 ? null : new String(r.body(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("LiveKit Ingress {} 연결 실패", enabled ? "열기" : "잠그기", e);
            throw new ApiException(ErrorCode.UPSTREAM);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 기다리던 중 스레드가 중단됐다 — 중단 표시를 되살려 두는 것이 자바의 관례다
            throw new ApiException(ErrorCode.UPSTREAM);
        }
        if (error == null) return;
        // 거부됐다 — 송출 중이라서인지 다시 읽어 판정한다 (updateIngressRoom 과 같은 방식)
        boolean active;
        try {
            active = getIngress(ingressId).map(IngressInfo::active).orElse(false);
        } catch (ApiException ignored) {
            active = false;
        }
        if (active) throw new IngressBusyException("Ingress 가 송출 중이라 잠글 수 없어요: " + ingressId);
        log.error("LiveKit Ingress {} 실패 {} {}", enabled ? "열기" : "잠그기", status, error);
        throw new ApiException(ErrorCode.UPSTREAM);
    }

    @Override
    public Optional<IngressInfo> getIngress(String ingressId) {
        // 한 건 조회 API 가 없어 목록 조회(listIngress(방 이름=null, ingressId))로 거른다. 없으면 빈 목록
        List<LivekitIngress.IngressInfo> list = call("Ingress 조회", ingress.listIngress(null, ingressId));
        return list == null || list.isEmpty() ? Optional.empty() : Optional.of(toInfo(list.get(0)));
    }

    @Override
    public void deleteIngress(String ingressId) {
        callIgnoringNotFound("Ingress 삭제", ingress.deleteIngress(ingressId));
    }

    @Override
    public void createRoom(String roomName, int emptyTimeoutSeconds, int departureTimeoutSeconds) {
        // SDK 인자: 이름, emptyTimeout, 최대 인원, nodeId, metadata, 최소·최대 재생 지연, syncStreams, departureTimeout — null 은 LiveKit 기본값
        call("방 생성", rooms.createRoom(roomName, emptyTimeoutSeconds, null, null, null, null, null, null, departureTimeoutSeconds));
    }

    @Override
    public void deleteRoom(String roomName) {
        callIgnoringNotFound("방 삭제", rooms.deleteRoom(roomName));
    }

    @Override
    public List<Participant> listParticipants(String roomName) {
        // 방이 없으면 callIgnoringNotFound 가 null → 빈 목록
        List<LivekitModels.ParticipantInfo> list = callIgnoringNotFound("참가자 목록", rooms.listParticipants(roomName));
        return list == null ? List.of() : list.stream().map(LiveKitCloudGateway::toParticipant).toList();
    }

    @Override
    public Optional<Participant> getParticipant(String roomName, String identity) {
        LivekitModels.ParticipantInfo p = callIgnoringNotFound("참가자 조회", rooms.getParticipant(roomName, identity));
        return p == null ? Optional.empty() : Optional.of(toParticipant(p));
    }

    @Override
    public void mutePublishedTrack(String roomName, String identity, String trackSid, boolean muted) {
        call(muted ? "트랙 mute" : "트랙 unmute", rooms.mutePublishedTrack(roomName, identity, trackSid, muted));
    }

    @Override
    public void setCanSubscribe(String roomName, String identity, boolean canSubscribe) {
        // updateParticipant(방, identity, 이름, metadata, 권한) — 권한만 바꾼다
        LivekitModels.ParticipantPermission permission = LivekitModels.ParticipantPermission.newBuilder()
                .setCanSubscribe(canSubscribe).setCanPublish(false).setCanPublishData(false).build(); // 시청 토큰과 같은 권한에서 구독만 바꾼다
        callIgnoringNotFound(canSubscribe ? "시청 허용" : "시청 막기", rooms.updateParticipant(roomName, identity, null, null, permission)); // 그 사이 나간 고객은 무시
    }

    @Override
    public EgressInfo startRecording(String roomName, String objectKey) {
        // 녹화 파일(MP4)을 LiveKit 이 직접 Supabase Storage 의 S3 호환 주소로 올린다. objectKey = 버킷 안 파일 경로(LiveService 는 "<예약id>.mp4").
        // forcePathStyle: 버킷 이름을 도메인이 아니라 경로에 넣는 주소 방식
        LivekitEgress.EncodedFileOutput output = LivekitEgress.EncodedFileOutput.newBuilder()
                .setFileType(LivekitEgress.EncodedFileType.MP4)
                .setFilepath(objectKey)
                .setS3(LivekitEgress.S3Upload.newBuilder()
                        .setEndpoint(storage.s3Endpoint())
                        .setBucket(storage.recordingsBucket())
                        .setAccessKey(storage.s3AccessKey())
                        .setSecret(storage.s3SecretKey())
                        .setRegion(storage.s3Region())
                        .setForcePathStyle(true))
                .build();
        // SDK 인자: 방, 출력, 레이아웃, 인코딩 프리셋(null), 세부 인코딩(null), audioOnly=false, videoOnly=true(소리 없이 영상만), customBaseUrl, 오디오 믹싱
        LivekitEgress.EgressInfo info = call("녹화 시작", egress.startRoomCompositeEgress(roomName, output, RECORDING_LAYOUT, null, null, false, true, "", AudioMixing.DEFAULT_MIXING));
        return toEgress(info);
    }

    @Override
    public void stopEgress(String egressId) {
        callIgnoringNotFound("녹화 정지", egress.stopEgress(egressId));
    }

    @Override
    public Optional<EgressInfo> getEgress(String egressId) {
        // listEgress(방 이름=null, egressId, 진행 중만=null) — getIngress 처럼 목록 조회로 한 건을 찾는다
        List<LivekitEgress.EgressInfo> list = call("녹화 조회", egress.listEgress(null, egressId, null));
        return list == null || list.isEmpty() ? Optional.empty() : Optional.of(toEgress(list.get(0)));
    }

    @Override
    public String joinToken(String roomName, String identity, String name, Duration ttl) {
        // 서버 비밀값으로 서명한 JWT. 권한: 이 방에만 입장, 구독(시청)만 가능, 영상·데이터 발행 불가
        AccessToken token = new AccessToken(livekit.apiKey(), livekit.apiSecret());
        token.setIdentity(identity);
        token.setName(name);
        token.setTtl(ttl.toMillis());
        token.addGrants(new RoomJoin(true), new RoomName(roomName), new CanPublish(false), new CanSubscribe(true), new CanPublishData(false));
        return token.toJwt();
    }

    @Override
    public String publishToken(String roomName, String identity, String name, Duration ttl) {
        // 휴대폰 송출용 JWT (§8.0). 권한: 이 방에만 입장, 카메라 영상만 발행(마이크·화면 공유 불가), 시청·데이터 발행 불가
        AccessToken token = new AccessToken(livekit.apiKey(), livekit.apiSecret());
        token.setIdentity(identity);
        token.setName(name);
        token.setTtl(ttl.toMillis());
        token.addGrants(new RoomJoin(true), new RoomName(roomName), new CanPublish(true), new CanPublishSources(List.of("camera")),
                new CanSubscribe(false), new CanPublishData(false));
        return token.toJwt();
    }

    @Override
    public String url() {
        return livekit.url();
    }

    @Override
    public WebhookEvent parseWebhook(String body, String authorizationHeader) {
        // receive = Authorization 헤더의 서명을 API 키·비밀값으로 검증하고 본문(JSON)을 읽는다. 검증이 실패하면 예외 → 401
        LivekitWebhook.WebhookEvent e;
        try {
            e = webhooks.receive(body, authorizationHeader);
        } catch (Exception ex) {
            log.warn("LiveKit 웹훅 서명 검증 실패: {}", ex.toString());
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "웹훅 서명이 올바르지 않아요");
        }
        // 이벤트 종류마다 들어 있는 정보가 다르다 — 방 이름은 room → ingressInfo → egressInfo 순으로 있는 곳에서 꺼낸다
        String room = e.hasRoom() ? e.getRoom().getName() : e.hasIngressInfo() ? e.getIngressInfo().getRoomName() : e.hasEgressInfo() ? e.getEgressInfo().getRoomName() : null;
        return new WebhookEvent(e.getId(), e.getEvent(), room, e.hasParticipant() ? e.getParticipant().getIdentity() : null,
                e.hasIngressInfo() ? toInfo(e.getIngressInfo()) : null, e.hasEgressInfo() ? toEgress(e.getEgressInfo()) : null,
                e.hasTrack() ? new Track(e.getTrack().getSid(), e.getTrack().getType() == LivekitModels.TrackType.VIDEO, e.getTrack().getMuted()) : null);
    }

    // ── 변환 ──
    // LiveKit protobuf 타입 → LiveKitGateway 의 record. static 이라 LiveKitCloudGatewayTest 가 LiveKit 없이 바로 부른다

    /** active = 상태가 BUFFERING(입력이 들어와 준비 중) 또는 PUBLISHING(송출 중). 상태 정보가 없으면 쉬는 것으로 본다 */
    static IngressInfo toInfo(LivekitIngress.IngressInfo i) {
        boolean active = i.hasState() && (i.getState().getStatus() == LivekitIngress.IngressState.Status.ENDPOINT_BUFFERING
                || i.getState().getStatus() == LivekitIngress.IngressState.Status.ENDPOINT_PUBLISHING);
        return new IngressInfo(i.getIngressId(), i.getUrl(), i.getStreamKey(), i.getRoomName(), active);
    }

    /** 참가자와 그가 올린 트랙들(영상 여부·mute 여부), 지금 구독 권한 */
    static Participant toParticipant(LivekitModels.ParticipantInfo p) {
        return new Participant(p.getIdentity(), p.getTracksList().stream()
                .map(t -> new Track(t.getSid(), t.getType() == LivekitModels.TrackType.VIDEO, t.getMuted())).toList(),
                !p.hasPermission() || p.getPermission().getCanSubscribe()); // 권한 정보가 없으면 토큰대로(구독 가능)로 본다
    }

    /**
     * 녹화 상태를 소문자 문자열로 (LiveKitGateway.EgressInfo 의 status 목록과 같다). 모르는 값은 "unknown" — 끝난 것으로 치지 않는다.
     * 파일은 첫 번째 결과만 본다. 길이는 LiveKit 값을 1_000_000_000 으로 나눠(나노초 → 초) 담고, 빈 오류 문구는 null 로
     */
    static EgressInfo toEgress(LivekitEgress.EgressInfo e) {
        String status = switch (e.getStatus()) {
            case EGRESS_STARTING -> "starting";
            case EGRESS_ACTIVE -> "active";
            case EGRESS_ENDING -> "ending";
            case EGRESS_COMPLETE -> "complete";
            case EGRESS_FAILED -> "failed";
            case EGRESS_ABORTED -> "aborted";
            case EGRESS_LIMIT_REACHED -> "limit_reached";
            default -> "unknown";
        };
        LivekitEgress.FileInfo file = e.getFileResultsList().isEmpty() ? null : e.getFileResultsList().get(0);
        // Egress 시작과 파일 녹화 시작은 다르다. 영상 구간은 실제 파일의 시작(나노초)으로 계산한다.
        Instant startedAt = file != null && file.getStartedAt() > 0 ? Instant.ofEpochSecond(0, file.getStartedAt()) : null;
        return new EgressInfo(e.getEgressId(), e.getRoomName(), status, file == null ? null : file.getFilename(),
                file == null ? 0 : file.getDuration() / 1_000_000_000L, e.getError().isBlank() ? null : e.getError(), startedAt);
    }

    // ── retrofit 호출 공통 ──
    // retrofit = SDK 가 쓰는 HTTP 라이브러리. Call.execute() 는 요청을 보내고 응답이 올 때까지 이 스레드에서 기다린다

    /** 요청을 보내 본문을 돌려준다. 실패 응답·연결 실패는 모두 UPSTREAM(502). what 은 로그에 남길 작업 이름 */
    private <T> T call(String what, Call<T> c) {
        try {
            Response<T> r = c.execute();
            if (!r.isSuccessful()) {
                String err = r.errorBody() == null ? "" : r.errorBody().string();
                log.error("LiveKit {} 실패 {} {}", what, r.code(), err);
                throw new ApiException(ErrorCode.UPSTREAM); // 오류 본문은 로그에만 — 앱 화면 문구로 나가지 않는다
            }
            return r.body();
        } catch (IOException e) {
            log.error("LiveKit {} 연결 실패", what, e);
            throw new ApiException(ErrorCode.UPSTREAM);
        }
    }

    /** 404 는 null (없는 방·참가자·녹화는 오류가 아니다) */
    private <T> T callIgnoringNotFound(String what, Call<T> c) {
        try {
            Response<T> r = c.execute();
            if (r.code() == 404) return null;
            if (!r.isSuccessful()) {
                String err = r.errorBody() == null ? "" : r.errorBody().string();
                // 404 가 아닌 코드로 와도 오류 본문 문구가 '없음' 이면 없는 것으로 본다
                if (err.contains("not found") || err.contains("does not exist")) return null;
                log.error("LiveKit {} 실패 {} {}", what, r.code(), err);
                throw new ApiException(ErrorCode.UPSTREAM); // 오류 본문은 로그에만 — 앱 화면 문구로 나가지 않는다
            }
            return r.body();
        } catch (IOException e) {
            log.error("LiveKit {} 연결 실패", what, e);
            throw new ApiException(ErrorCode.UPSTREAM);
        }
    }
}
