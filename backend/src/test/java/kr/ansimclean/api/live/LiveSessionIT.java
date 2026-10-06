package kr.ansimclean.api.live;

import kr.ansimclean.api.ItSupport;
import kr.ansimclean.api.MutableClock;
import kr.ansimclean.api.common.supabase.SupabaseStorageClient;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.live.LiveKitGateway.EgressInfo;
import kr.ansimclean.api.live.LiveKitGateway.IngressInfo;
import kr.ansimclean.api.live.LiveKitGateway.Participant;
import kr.ansimclean.api.live.LiveKitGateway.Track;
import kr.ansimclean.api.live.LiveKitGateway.WebhookEvent;
import kr.ansimclean.api.live.dto.RegisterDeviceRequest;
import kr.ansimclean.api.offer.Offer;
import kr.ansimclean.api.offer.OfferRepository;
import kr.ansimclean.api.profile.ProfileService;
import kr.ansimclean.api.schedule.AvailabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 통합 (Docker 필요, B5 완료 기준 LiveSessionIT): start → ingress_started 웹훅 → live → pause(mute 확인) → jobs/pause 가 muted 전엔 409 → resume → stop
 * · 고객 토큰 상태별 409 · 웹훅 서명 실패 401 · ingress_ended → waiting_camera · 웹훅 없이 LiveReconciler 가 LiveKit 조회만으로 live/ready 를 맞춤.
 * LiveKit 과 Storage 만 대역(게이트웨이 mock), 나머지는 실제 Postgres 위에서.
 */
// 통합 테스트의 기본 구성은 CODE_GUIDE.md 3-12. 이 파일만의 점:
// AutoConfigureMockMvc = MockMvc(실제 네트워크 없이 HTTP 요청을 컨트롤러에 넣어 보는 도구)를 준비한다.
// ActiveProfiles("test") = test 프로필 — SchedulingConfig 가 꺼져 재조정기 같은 스케줄이 알아서 돌지 않는다(테스트가 reconcile() 을 직접 부른다).
// Import(FixedClock) = 아래의 옮길 수 있는 시계를 서버의 AppClock 으로 쓴다
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(LiveSessionIT.FixedClock.class)
class LiveSessionIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    // 테스트가 set 으로 옮길 수 있는 시계. 처음은 2026-09-15 14:00(서울). 테스트끼리 같이 쓰므로 setUp 에서 매번 되돌린다
    static final MutableClock CLOCK = new MutableClock(Instant.parse("2026-09-15T05:00:00Z"), AppClock.ZONE);

    // 테스트 전용 설정: 위 CLOCK 으로 만든 AppClock 빈을 등록한다. Primary = 같은 타입 빈이 둘일 때 이쪽을 주입한다
    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {
        @Bean
        @Primary
        AppClock testClock() {
            return new AppClock(CLOCK);
        }
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    ProfileService profiles;
    @Autowired
    AvailabilityService availability;
    @Autowired
    OfferRepository offers;
    @Autowired
    DeviceService devices;
    @Autowired
    LiveSessionRepository sessions;
    @Autowired
    LiveReconciler reconciler;
    // MockitoBean = 스프링 빈 자리에 Mockito 목을 넣는다. LiveKit·Storage 는 밖으로 나가지 않게, JwtDecoder 는 가짜 로그인 토큰을 풀게(ItSupport)
    @MockitoBean
    LiveKitGateway livekit;
    @MockitoBean
    SupabaseStorageClient storage;
    @MockitoBean
    JwtDecoder jwtDecoder;

    final JsonMapper json = JsonMapper.builder().build();
    ItSupport.User customer;
    ItSupport.User manager;
    String cameraIdentity;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc; // SQL 을 직접 보내는 도구 — 여기서는 테스트 사이 데이터 비우기에만 쓴다

    // 테스트마다: 사용자 데이터 비우기 → 시계 되돌리기 → 고객·승인된 매니저(강남구) 만들기 → LiveKit 목의 기본 답 정하기 → 바디캠 등록.
    // LiveKit 기본 답: Ingress 는 "ing-매니저id", 토큰 "jwt", 녹화 시작 시 egress "eg-1", 방 참가자·바디캠·녹화 조회는 모두 '없음'.
    // Ingress 조회(getIngress)는 정하지 않아 목 기본값(빈 Optional) — '어느 방에 묶였는지 모름' 이라 정지해도 대기 방으로 옮기지 않는다(필요한 테스트가 따로 정한다)
    @BeforeEach
    void setUp() {
        ItSupport.resetUserData(jdbc); // 앞 테스트의 매니저·오퍼가 남아 '첫 pending 오퍼' 를 잘못 잡지 않게
        CLOCK.set(Instant.parse("2026-09-15T05:00:00Z"));
        customer = ItSupport.signedUp(profiles, jwtDecoder, "customer", "김하늘");
        manager = ItSupport.approvedManager(profiles, availability, jwtDecoder, "김미영", "서울특별시 강남구", "서울특별시");
        cameraIdentity = "camera-" + manager.id();
        when(livekit.createIngress(anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(inv -> new IngressInfo("ing-" + manager.id(), "rtmp://lk.example/x", "full-key", inv.getArgument(1), false));
        when(livekit.updateIngressRoom(anyString(), anyString())).thenAnswer(inv -> new IngressInfo(inv.getArgument(0), "rtmp://lk.example/x", "full-key", inv.getArgument(1), false));
        when(livekit.joinToken(anyString(), anyString(), anyString(), any())).thenReturn("jwt");
        when(livekit.url()).thenReturn("wss://lk.example");
        when(livekit.startRecording(anyString(), anyString())).thenAnswer(inv -> new EgressInfo("eg-1", inv.getArgument(0), "active", null, 0, null));
        when(livekit.listParticipants(anyString())).thenReturn(List.of());
        when(livekit.getParticipant(anyString(), anyString())).thenReturn(Optional.empty());
        when(livekit.getEgress(anyString())).thenReturn(Optional.empty());
        devices.register(new RegisterDeviceRequest(manager.id(), "안심캠 #AC-1", "AC-1", false)); // 운영자 등록(POST /admin/devices)과 같은 서비스를 직접 부른다
    }

    /** 예약 생성 → 수락 → 당일 09:50 으로 이동 → 작업 시작 */
    // 9월 16일 10:00 예약을 만든 직후 그 예약의 콜(offer) 중 대기 중인 첫 콜을 찾아 매니저가 수락한다.
    // 시계를 16일 09:50(서울, UTC 00:50)으로 옮겨야 '오늘 작업' 으로 시작할 수 있다. 돌려주는 값 = 예약 id
    UUID startedJob() throws Exception {
        String res = mvc.perform(post("/api/v1/customer/bookings").header("Authorization", customer.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(ItSupport.bookingBody("2026-09-16", "10:00"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        UUID id = UUID.fromString(json.readTree(res).get("id").asString());
        Offer offer = offers.findByBookingId(id).stream().filter(Offer::isPending).findFirst().orElseThrow();
        mvc.perform(post("/api/v1/manager/offers/" + offer.getId() + "/accept").header("Authorization", manager.bearer())).andExpect(status().isOk());
        CLOCK.set(Instant.parse("2026-09-16T00:50:00Z"));
        mvc.perform(post("/api/v1/manager/jobs/" + id + "/start").header("Authorization", manager.bearer())).andExpect(status().isOk());
        return id;
    }

    // LiveKit 웹훅 한 건을 흉내 낸다. Authorization 헤더 값(name)마다 parseWebhook 목이 이 이벤트를 돌려주게 정한 뒤 웹훅 주소로 보낸다.
    // 서명 검증은 목이 대신하므로 본문은 "{}" 여도 된다. 처리되면 204(본문 없음)
    void webhook(String name, WebhookEvent event) throws Exception {
        when(livekit.parseWebhook(anyString(), eq(name))).thenReturn(event);
        mvc.perform(post("/api/v1/live/webhooks/livekit").header("Authorization", name).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNoContent());
    }

    // 한 작업의 라이브 전체 흐름을 HTTP 로 차례대로 밟는다. mvc.perform(요청).andExpect(...) = 요청을 보내고 상태 코드·JSON 값을 확인한다
    // (jsonPath("$.status") = 응답 JSON 의 status 칸)
    @Test
    void 시작_웹훅_일시정지_재개_정지_그리고_고객_토큰과_녹화본() throws Exception {
        UUID id = startedJob();
        String room = "booking-" + id;
        // 고객: 세션이 없으니 아직 못 본다
        mvc.perform(post("/api/v1/live/sessions/" + id + "/token").header("Authorization", customer.bearer()).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"viewer\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("LIVE_NOT_AVAILABLE"));

        mvc.perform(post("/api/v1/live/sessions/" + id + "/start").header("Authorization", manager.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("waiting_camera")).andExpect(jsonPath("$.cameraBusy").value(false)).andExpect(jsonPath("$.preview.token").value("jwt"));
        verify(livekit).createRoom(room, 21600, 21600);
        verify(livekit).updateIngressRoom("ing-" + manager.id(), room);

        // 서명이 틀린 웹훅
        when(livekit.parseWebhook(anyString(), eq("bad"))).thenThrow(new kr.ansimclean.api.common.error.ApiException(kr.ansimclean.api.common.error.ErrorCode.UNAUTHENTICATED));
        mvc.perform(post("/api/v1/live/webhooks/livekit").header("Authorization", "bad").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());

        // 바디캠이 붙었다 → live + 녹화 시작 + 고객 시청 가능
        when(livekit.getParticipant(room, cameraIdentity)).thenReturn(Optional.of(new Participant(cameraIdentity, List.of(new Track("T1", true, false)))));
        webhook("w1", new WebhookEvent("ev-1", "ingress_started", room, null, new IngressInfo("ing-" + manager.id(), "rtmp://x", "k", room, true), null));
        mvc.perform(get("/api/v1/live/sessions/" + id).header("Authorization", manager.bearer()))
                .andExpect(jsonPath("$.status").value("live")).andExpect(jsonPath("$.cameraOnline").value(true));
        verify(livekit).startRecording(room, id + ".mp4");
        mvc.perform(post("/api/v1/live/sessions/" + id + "/token").header("Authorization", customer.bearer()).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"viewer\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roomName").value(room));
        mvc.perform(get("/api/v1/customer/bookings/" + id + "/live").header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$.live.state").value("live")).andExpect(jsonPath("$.live.available").value(true)).andExpect(jsonPath("$.status.title").value("거실 청소 중"));

        // 영상이 나가는 중에는 작업 일시정지가 막힌다 (§8.2.1)
        mvc.perform(post("/api/v1/manager/jobs/" + id + "/pause").header("Authorization", manager.bearer()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"고객 요청\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.message").value("영상을 먼저 중단해주세요"));
        // mute 를 확인하지 못하면 502 + mute_pending
        // (바디캠 목이 계속 '안 막힌 트랙' 을 돌려준다. test 프로필도 application.yml 의 6번 × 500ms 확인을 그대로 쓰므로 이 요청은 약 3초 걸린다)
        mvc.perform(post("/api/v1/live/sessions/" + id + "/pause").header("Authorization", manager.bearer()))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.error.code").value("LIVE_PAUSE_UNCONFIRMED"));
        assertThat(sessions.findById(id).orElseThrow().isMutePending()).isTrue();
        // 확인되면 paused → 작업 일시정지도 된다
        when(livekit.getParticipant(room, cameraIdentity)).thenReturn(Optional.of(new Participant(cameraIdentity, List.of(new Track("T1", true, true)))));
        mvc.perform(post("/api/v1/live/sessions/" + id + "/pause").header("Authorization", manager.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("paused")).andExpect(jsonPath("$.muted").value(true));
        mvc.perform(post("/api/v1/manager/jobs/" + id + "/pause").header("Authorization", manager.bearer()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"고객 요청\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/customer/bookings/" + id + "/live").header("Authorization", customer.bearer())).andExpect(jsonPath("$.live.state").value("paused"));

        // 재개: 작업 먼저, 영상 다음 (§8.2.1)
        mvc.perform(post("/api/v1/manager/jobs/" + id + "/resume").header("Authorization", manager.bearer())).andExpect(status().isOk());
        when(livekit.getParticipant(room, cameraIdentity)).thenReturn(Optional.of(new Participant(cameraIdentity, List.of(new Track("T1", true, false)))));
        mvc.perform(post("/api/v1/live/sessions/" + id + "/resume").header("Authorization", manager.bearer())).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("live"));

        // 바디캠이 끊기면 waiting_camera
        webhook("w2", new WebhookEvent("ev-2", "ingress_ended", room, null, new IngressInfo("ing-" + manager.id(), "rtmp://x", "k", room, false), null));
        mvc.perform(get("/api/v1/live/sessions/" + id).header("Authorization", manager.bearer())).andExpect(jsonPath("$.status").value("waiting_camera"));
        webhook("w2", new WebhookEvent("ev-2", "ingress_ended", room, null, null, null)); // 같은 id 두 번 → 무시(204)

        // 완료 → 정지 → 녹화 끝 → 녹화본. 정지는 작업이 끝난 뒤에만(앱도 jobs/complete → live/stop 순서) — 그 전엔 409
        mvc.perform(post("/api/v1/live/sessions/" + id + "/stop").header("Authorization", manager.bearer()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
        mvc.perform(post("/api/v1/manager/jobs/" + id + "/complete").header("Authorization", manager.bearer())).andExpect(status().isOk());
        // 정지는 바디캠이 지금 이 방에 묶여 있을 때만 대기 방으로 옮긴다(LiveService.releaseDevice → boundTo 가 LiveKit 에 묻는다) —
        // 다음 작업 방에 이미 묶인 바디캠을 빼 오지 않게 (자체 점검 #51). 실제 LiveKit 처럼 '이 방에 묶여 있음' 을 돌려주게 한다
        when(livekit.getIngress("ing-" + manager.id())).thenReturn(Optional.of(new IngressInfo("ing-" + manager.id(), "rtmp://lk.example/x", "full-key", room, false)));
        mvc.perform(post("/api/v1/live/sessions/" + id + "/stop").header("Authorization", manager.bearer())).andExpect(status().isOk());
        verify(livekit).stopEgress("eg-1");
        verify(livekit).deleteRoom(room);
        verify(livekit).updateIngressRoom("ing-" + manager.id(), "parking-" + manager.id());
        mvc.perform(get("/api/v1/live/sessions/" + id + "/recording").header("Authorization", customer.bearer()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RECORDING_NOT_READY"));
        webhook("w3", new WebhookEvent("ev-3", "egress_ended", room, null, null, new EgressInfo("eg-1", room, "complete", id + ".mp4", 630, null)));
        // 녹화본 주소는 버킷 "recordings" 의 그 파일을 LiveService.RECORDING_URL_TTL_SECONDS(6시간) 동안 여는 서명 URL — 인자가 하나라도 다르면 목이 null 을 돌려준다
        when(storage.signedUrl("recordings", id + ".mp4", LiveService.RECORDING_URL_TTL_SECONDS)).thenReturn("https://signed");
        mvc.perform(get("/api/v1/live/sessions/" + id + "/recording").header("Authorization", customer.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.url").value("https://signed")).andExpect(jsonPath("$.durationSeconds").value(630));
        mvc.perform(get("/api/v1/customer/reservations").header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$.reservations[0].recordingReady").value(true));
        // 매니저 완료 화면 요약의 4번째 줄(인덱스 3)은 '촬영 기록' — 녹화 ready 면 "정상 업로드"
        mvc.perform(get("/api/v1/manager/jobs/last-completed").header("Authorization", manager.bearer())).andExpect(jsonPath("$.summary[3].value").value("정상 업로드"));
    }

    // 웹훅을 하나도 보내지 않고, LiveKit 목의 답(방 참가자·녹화 상태)만 바꿔 가며 reconcile() 을 직접 부른다:
    // 바디캠이 보임 → live + 녹화 시작, 안 보임 → 카메라 대기, 6분 뒤 → 5분 대기 알림 기록, 녹화 complete → ready, 작업 완료 → 세션 ended
    @Test
    void 웹훅_없이_재조정기만으로_live_와_ready_를_맞춘다() throws Exception {
        UUID id = startedJob();
        String room = "booking-" + id;
        mvc.perform(post("/api/v1/live/sessions/" + id + "/start").header("Authorization", manager.bearer())).andExpect(status().isOk());
        when(livekit.listParticipants(room)).thenReturn(List.of(new Participant(cameraIdentity, List.of(new Track("T1", true, false)))));
        reconciler.reconcile();
        LiveSession s = sessions.findById(id).orElseThrow();
        assertThat(s.getStatus()).isEqualTo(LiveSession.LIVE);
        assertThat(s.getEgressId()).isEqualTo("eg-1");

        // 방이 빈 채로 5분 → 매니저 푸시 1회(시각을 옮겨야 경로가 돈다), 그 뒤 녹화가 끝난 걸 조회로 알아챈다
        when(livekit.listParticipants(room)).thenReturn(List.of());
        reconciler.reconcile();
        assertThat(sessions.findById(id).orElseThrow().getStatus()).isEqualTo(LiveSession.WAITING_CAMERA);
        assertThat(sessions.findById(id).orElseThrow().getCameraWaitNotifiedAt()).isNull(); // 방금 끊겼다 — 대기 5분은 끊긴 시각부터 잰다
        CLOCK.set(CLOCK.instant().plus(java.time.Duration.ofMinutes(6)));
        reconciler.reconcile();
        assertThat(sessions.findById(id).orElseThrow().getCameraWaitNotifiedAt()).isNotNull();
        when(livekit.getEgress("eg-1")).thenReturn(Optional.of(new EgressInfo("eg-1", room, "complete", id + ".mp4", 100, null)));
        reconciler.reconcile();
        assertThat(sessions.findById(id).orElseThrow().getRecordingStatus()).isEqualTo(LiveSession.REC_READY);

        // 작업이 끝나면 세션도 정리한다
        mvc.perform(post("/api/v1/manager/jobs/" + id + "/complete").header("Authorization", manager.bearer())).andExpect(status().isOk());
        reconciler.reconcile();
        assertThat(sessions.findById(id).orElseThrow().isEnded()).isTrue();
        verify(livekit, org.mockito.Mockito.atLeastOnce()).deleteRoom(room); // 같은 reconcile() 안에서 '끝난 지 15분 안 세션' 루프가 한 번 더 닫는다(멱등)
    }
}
