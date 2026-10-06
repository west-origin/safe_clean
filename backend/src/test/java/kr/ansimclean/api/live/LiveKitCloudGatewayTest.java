package kr.ansimclean.api.live;

import com.google.protobuf.util.JsonFormat;
import io.livekit.server.AccessToken;
import kr.ansimclean.api.common.config.AppProperties;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import livekit.LivekitEgress;
import livekit.LivekitIngress;
import livekit.LivekitModels;
import livekit.LivekitWebhook;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 웹훅은 위조 가능한 외부 입력 — 서명 검증(§8.3)과 protobuf → 앱 모델 변환. LiveKit 서버는 부르지 않는다 */
// 단위 테스트: 실제 LiveKitCloudGateway 를 테스트용 키·비밀값으로 만든다. protobuf 는 LiveKit 이 쓰는 이진 메시지 형식이고,
// livekit.* 클래스(LivekitWebhook·LivekitIngress 등)가 그 타입이다. 웹훅 본문은 그 메시지를 JSON 으로 찍은 것이다.
// Ingress 잠금 테스트(Twirp UpdateIngress)만 이 컴퓨터(127.0.0.1)에 가짜 HTTP 서버를 띄워 실제로 나가는 요청 바이트를 받아 본다.
class LiveKitCloudGatewayTest {
    // 테스트 전용 가짜 키·비밀값 (실제 LiveKit 계정 값이 아니다)
    static final String KEY = "APIkey1234567";
    static final String SECRET = "secret-secret-secret-secret-secret-32";
    static final AppProperties.Livekit LIVEKIT = new AppProperties.Livekit("cloud", "wss://example.livekit.cloud", KEY, SECRET, 3, 1);
    static final AppProperties.Storage STORAGE = new AppProperties.Storage("https://s3.example", "ak", "sk", "ap-northeast-2", "recordings");
    // 생성자는 livekit·storage 설정만 읽는다 — 나머지(cors·supabase·admin)는 null 로 둔다
    LiveKitCloudGateway gateway = new LiveKitCloudGateway(new AppProperties(null, null, LIVEKIT, STORAGE, null));

    /**
     * 웹훅 Authorization 헤더 값을 LiveKit SDK 의 AccessToken 으로 만든다: 본문의 SHA-256 해시(Base64)를 담은 JWT 를 secret 으로 서명.
     * secret 을 다르게 주면 '서명이 틀린 웹훅', 다른 본문으로 만들면 '해시가 안 맞는 웹훅' 이 된다
     */
    static String signed(String body, String secret) throws Exception {
        String sha = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
        AccessToken token = new AccessToken(KEY, secret);
        token.setSha256(sha);
        return token.toJwt();
    }

    /** protobuf 웹훅 메시지를 JSON 문자열로 (LiveKit 이 보내는 본문 모양) */
    static String body(LivekitWebhook.WebhookEvent event) throws Exception {
        return JsonFormat.printer().print(event);
    }

    // 1) ingress_started(room 없이 ingressInfo 만 있음): 바른 서명이면 받아서 방 이름을 ingress 에서 꺼내고, 송출 중(PUBLISHING)이라 active.
    // 2) 틀린 비밀값·바뀐 본문·헤더 없음·아무 문자열 → 모두 오류. 3) participant_joined(room 있음): 방 이름과 참가자 identity
    @Test
    void 서명이_맞는_웹훅만_받고_방_이름은_room_ingress_egress_순으로_찾는다() throws Exception {
        LivekitWebhook.WebhookEvent ingressStarted = LivekitWebhook.WebhookEvent.newBuilder().setId("ev-1").setEvent("ingress_started")
                .setIngressInfo(LivekitIngress.IngressInfo.newBuilder().setIngressId("ing-1").setRoomName("booking-x").setUrl("rtmp://x").setStreamKey("k")
                        .setState(LivekitIngress.IngressState.newBuilder().setStatus(LivekitIngress.IngressState.Status.ENDPOINT_PUBLISHING)))
                .build();
        String json = body(ingressStarted);

        LiveKitGateway.WebhookEvent parsed = gateway.parseWebhook(json, signed(json, SECRET));
        assertThat(parsed.id()).isEqualTo("ev-1");
        assertThat(parsed.event()).isEqualTo("ingress_started");
        assertThat(parsed.roomName()).isEqualTo("booking-x"); // room 이 없으면 ingress 의 방
        assertThat(parsed.ingress().ingressId()).isEqualTo("ing-1");
        assertThat(parsed.ingress().active()).isTrue();

        assertThatThrownBy(() -> gateway.parseWebhook(json, signed(json, "wrong-secret-wrong-secret-wrong-secret")))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.UNAUTHENTICATED);
        assertThatThrownBy(() -> gateway.parseWebhook(json.replace("ev-1", "ev-2"), signed(json, SECRET))) // 본문이 바뀌면 sha256 불일치
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.UNAUTHENTICATED);
        assertThatThrownBy(() -> gateway.parseWebhook(json, null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> gateway.parseWebhook(json, "garbage")).isInstanceOf(ApiException.class);

        LivekitWebhook.WebhookEvent joined = LivekitWebhook.WebhookEvent.newBuilder().setId("ev-3").setEvent("participant_joined")
                .setRoom(LivekitModels.Room.newBuilder().setName("booking-y"))
                .setParticipant(LivekitModels.ParticipantInfo.newBuilder().setIdentity("customer-abc")).build();
        String joinedJson = body(joined);
        LiveKitGateway.WebhookEvent p = gateway.parseWebhook(joinedJson, signed(joinedJson, SECRET));
        assertThat(p.roomName()).isEqualTo("booking-y");
        assertThat(p.participantIdentity()).isEqualTo("customer-abc");
    }

    // Egress = LiveKit 의 녹화 작업. 완료(파일 하나, 길이 630.5초를 나노초로) → "complete"·파일명·630초·오류 없음·끝남.
    // 실패(파일 없음) → "failed"·파일명 null·0초·오류 문구. 진행 중(ACTIVE)은 끝난 것으로 치지 않는다
    @Test
    void egress_변환은_상태_문자열_파일명_초_단위_길이_오류를_뽑는다() {
        LivekitEgress.EgressInfo done = LivekitEgress.EgressInfo.newBuilder().setEgressId("eg-1").setRoomName("booking-x")
                .setStatus(LivekitEgress.EgressStatus.EGRESS_COMPLETE)
                .addFileResults(LivekitEgress.FileInfo.newBuilder().setFilename("x.mp4").setDuration(630_500_000_000L)).build();
        LiveKitGateway.EgressInfo e = LiveKitCloudGateway.toEgress(done);
        assertThat(e.status()).isEqualTo("complete");
        assertThat(e.filename()).isEqualTo("x.mp4");
        assertThat(e.durationSeconds()).isEqualTo(630); // ns → 초
        assertThat(e.error()).isNull();
        assertThat(e.isFinished()).isTrue();

        LivekitEgress.EgressInfo failed = LivekitEgress.EgressInfo.newBuilder().setEgressId("eg-2").setStatus(LivekitEgress.EgressStatus.EGRESS_FAILED).setError("disk full").build();
        LiveKitGateway.EgressInfo f = LiveKitCloudGateway.toEgress(failed);
        assertThat(f.status()).isEqualTo("failed");
        assertThat(f.filename()).isNull();
        assertThat(f.durationSeconds()).isZero();
        assertThat(f.error()).isEqualTo("disk full");
        assertThat(LiveKitCloudGateway.toEgress(LivekitEgress.EgressInfo.newBuilder().setStatus(LivekitEgress.EgressStatus.EGRESS_ACTIVE).build()).isFinished()).isFalse();
    }

    // Ingress 상태: BUFFERING 은 active, INACTIVE·상태 없음은 아님.
    // 참가자: 오디오·비디오 트랙 중 비디오 트랙(TV)을 골라 sid 와 mute 여부를 꺼내는지
    @Test
    void ingress_는_buffering_publishing_만_active_참가자는_비디오_트랙과_muted_를_뽑는다() {
        assertThat(LiveKitCloudGateway.toInfo(LivekitIngress.IngressInfo.newBuilder().setIngressId("i")
                .setState(LivekitIngress.IngressState.newBuilder().setStatus(LivekitIngress.IngressState.Status.ENDPOINT_BUFFERING)).build()).active()).isTrue();
        assertThat(LiveKitCloudGateway.toInfo(LivekitIngress.IngressInfo.newBuilder().setIngressId("i")
                .setState(LivekitIngress.IngressState.newBuilder().setStatus(LivekitIngress.IngressState.Status.ENDPOINT_INACTIVE)).build()).active()).isFalse();
        assertThat(LiveKitCloudGateway.toInfo(LivekitIngress.IngressInfo.newBuilder().setIngressId("i").build()).active()).isFalse(); // state 없음

        LiveKitGateway.Participant p = LiveKitCloudGateway.toParticipant(LivekitModels.ParticipantInfo.newBuilder().setIdentity("camera-m")
                .addTracks(LivekitModels.TrackInfo.newBuilder().setSid("TA").setType(LivekitModels.TrackType.AUDIO).setMuted(false))
                .addTracks(LivekitModels.TrackInfo.newBuilder().setSid("TV").setType(LivekitModels.TrackType.VIDEO).setMuted(true)).build());
        assertThat(p.identity()).isEqualTo("camera-m");
        assertThat(p.videoTrack()).isPresent();
        assertThat(p.videoTrack().get().sid()).isEqualTo("TV");
        assertThat(p.videoTrack().get().muted()).isTrue();
    }

    // app.livekit.mode 가 logging(기본)일 때 쓰는 대역(LoggingLiveKitGateway)은 서명을 확인할 키가 없어 어떤 웹훅도 UNAUTHENTICATED(401)로 거부한다
    @Test
    void logging_대역은_웹훅을_항상_거부한다() {
        assertThatThrownBy(() -> new LoggingLiveKitGateway().parseWebhook("{}", "x")).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    // 준비: JDK 내장 HttpServer 를 빈 포트(0 = 아무 빈 포트)에 띄우고, UpdateIngress 경로로 온 본문·Authorization 헤더를 저장한 뒤
    // ingressId 만 채운 IngressInfo 를 protobuf 로 답한다. 게이트웨이 주소를 이 서버(ws://127.0.0.1:포트)로 두고 setIngressEnabled(false) 를 부른다.
    // 확인: 받은 바이트를 UpdateIngressRequest 로 읽으면 ingressId·enabled=false 만 채워져 있고, 토큰이 Bearer 로 붙었는지
    @Test
    void Ingress_잠금은_Twirp_UpdateIngress_에_enabled_만_protobuf_로_보낸다() throws Exception { // 감사 H-02 — 로컬 가짜 서버, LiveKit 은 부르지 않는다
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        java.util.concurrent.atomic.AtomicReference<byte[]> received = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<String> auth = new java.util.concurrent.atomic.AtomicReference<>();
        server.createContext("/twirp/livekit.Ingress/UpdateIngress", ex -> {
            received.set(ex.getRequestBody().readAllBytes());
            auth.set(ex.getRequestHeaders().getFirst("Authorization"));
            byte[] out = LivekitIngress.IngressInfo.newBuilder().setIngressId("ing-1").build().toByteArray();
            ex.getResponseHeaders().add("Content-Type", "application/protobuf");
            ex.sendResponseHeaders(200, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        try {
            AppProperties.Livekit local = new AppProperties.Livekit("cloud", "ws://127.0.0.1:" + server.getAddress().getPort(), KEY, SECRET, 3, 1);
            new LiveKitCloudGateway(new AppProperties(null, null, local, STORAGE, null)).setIngressEnabled("ing-1", false);
            LivekitIngress.UpdateIngressRequest req = LivekitIngress.UpdateIngressRequest.parseFrom(received.get());
            assertThat(req.getIngressId()).isEqualTo("ing-1");
            assertThat(req.hasEnabled()).isTrue();
            assertThat(req.getEnabled()).isFalse();
            assertThat(req.getRoomName()).isEmpty(); // 다른 필드는 비워 LiveKit 이 바꾸지 않게
            assertThat(auth.get()).startsWith("Bearer ");
        } finally {
            server.stop(0);
        }
    }
}
