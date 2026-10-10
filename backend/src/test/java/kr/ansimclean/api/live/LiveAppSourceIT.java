package kr.ansimclean.api.live;

import kr.ansimclean.api.ItSupport;
import kr.ansimclean.api.MutableClock;
import kr.ansimclean.api.common.supabase.SupabaseStorageClient;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.live.LiveKitGateway.EgressInfo;
import kr.ansimclean.api.live.LiveKitGateway.Participant;
import kr.ansimclean.api.live.LiveKitGateway.Track;
import kr.ansimclean.api.live.LiveKitGateway.WebhookEvent;
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
import org.springframework.test.context.TestPropertySource;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 통합 (Docker 필요): 휴대폰 송출 모드(app.livekit.source=app, BACKEND_PLAN.md §8.0) — 바디캠 등록 없이
 * start(송출 토큰) → track_published 웹훅 → live·녹화 시작·고객 시청 가능 → 일시정지(서버 mute 확인) → track_unpublished → 카메라 대기.
 * LiveKit 과 Storage 만 대역(게이트웨이 mock), 나머지는 실제 Postgres 위에서. 구성은 LiveSessionIT 와 같다
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestPropertySource(properties = "app.livekit.source=app") // test 프로필의 ingress 를 덮는다
@Import(LiveAppSourceIT.FixedClock.class)
class LiveAppSourceIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    static final MutableClock CLOCK = new MutableClock(Instant.parse("2026-09-15T05:00:00Z"), AppClock.ZONE);

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
    @MockitoBean
    LiveKitGateway livekit;
    @MockitoBean
    SupabaseStorageClient storage;
    @MockitoBean
    JwtDecoder jwtDecoder;
    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    final JsonMapper json = JsonMapper.builder().build();
    ItSupport.User customer;
    ItSupport.User manager;
    String cameraIdentity;

    // 바디캠 등록(devices.register)을 하지 않는다 — 휴대폰 송출은 기기 행 없이 시작돼야 한다
    @BeforeEach
    void setUp() {
        ItSupport.resetUserData(jdbc);
        CLOCK.set(Instant.parse("2026-09-15T05:00:00Z"));
        customer = ItSupport.signedUp(profiles, jwtDecoder, "customer", "김하늘");
        manager = ItSupport.approvedManager(profiles, availability, jwtDecoder, "김미영", "서울특별시 강남구", "서울특별시");
        cameraIdentity = "camera-" + manager.id();
        when(livekit.publishToken(anyString(), anyString(), anyString(), any())).thenReturn("pub-jwt");
        when(livekit.joinToken(anyString(), anyString(), anyString(), any())).thenReturn("jwt");
        when(livekit.url()).thenReturn("wss://lk.example");
        when(livekit.startRecording(anyString(), anyString())).thenAnswer(inv -> new EgressInfo("eg-1", inv.getArgument(0), "active", null, 0, null));
        when(livekit.listParticipants(anyString())).thenReturn(List.of());
        when(livekit.getParticipant(anyString(), anyString())).thenReturn(Optional.empty());
        when(livekit.getEgress(anyString())).thenReturn(Optional.empty());
    }

    /** 예약 생성 → 수락 → 당일 09:50 → 작업 시작 (LiveSessionIT.startedJob 과 같다). 돌려주는 값 = 예약 id */
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

    void webhook(String name, WebhookEvent event) throws Exception {
        when(livekit.parseWebhook(anyString(), eq(name))).thenReturn(event);
        mvc.perform(post("/api/v1/live/webhooks/livekit").header("Authorization", name).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNoContent());
    }

    @Test
    void 휴대폰_송출_시작_트랙_웹훅_일시정지_끊김() throws Exception {
        UUID id = startedJob();
        String room = "booking-" + id;

        // 바디캠 없이 시작 — 송출 토큰, 프리뷰 없음, Ingress 호출 없음. 상태 응답의 source 는 app
        mvc.perform(post("/api/v1/live/sessions/" + id + "/start").header("Authorization", manager.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("waiting_camera"))
                .andExpect(jsonPath("$.publish.token").value("pub-jwt")).andExpect(jsonPath("$.preview").isEmpty());
        verify(livekit).publishToken(eq(room), eq(cameraIdentity), anyString(), any());
        verify(livekit, never()).updateIngressRoom(anyString(), anyString());
        mvc.perform(get("/api/v1/live/sessions/" + id).header("Authorization", manager.bearer())).andExpect(jsonPath("$.source").value("app"));

        // 매니저 앱이 카메라 트랙을 올렸다 → live + 녹화 시작 + 고객 시청 가능 + 장비 상태 카메라 '연결됨'
        when(livekit.getParticipant(room, cameraIdentity)).thenReturn(Optional.of(new Participant(cameraIdentity, List.of(new Track("TR1", true, false)))));
        webhook("w1", new WebhookEvent("ev-1", "track_published", room, cameraIdentity, null, null, new Track("TR1", true, false)));
        mvc.perform(get("/api/v1/live/sessions/" + id).header("Authorization", manager.bearer()))
                .andExpect(jsonPath("$.status").value("live")).andExpect(jsonPath("$.cameraOnline").value(true));
        verify(livekit).startRecording(room, id + ".mp4");
        mvc.perform(post("/api/v1/live/sessions/" + id + "/token").header("Authorization", customer.bearer()).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"viewer\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roomName").value(room));
        mvc.perform(get("/api/v1/manager/device/status").header("Authorization", manager.bearer()))
                .andExpect(jsonPath("$[0].label").value("카메라")).andExpect(jsonPath("$[0].status").value("ok"));

        // 일시정지 ①: 서버가 휴대폰 트랙을 mute 하고 확인 → paused
        when(livekit.getParticipant(room, cameraIdentity)).thenReturn(Optional.of(new Participant(cameraIdentity, List.of(new Track("TR1", true, true)))));
        mvc.perform(post("/api/v1/live/sessions/" + id + "/pause").header("Authorization", manager.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("paused"));
        verify(livekit).mutePublishedTrack(room, cameraIdentity, "TR1", true);

        // 재개 뒤 앱이 트랙을 내렸다(앱 종료·끊김) → 카메라 대기
        mvc.perform(post("/api/v1/live/sessions/" + id + "/resume").header("Authorization", manager.bearer())).andExpect(status().isOk());
        webhook("w2", new WebhookEvent("ev-2", "track_unpublished", room, cameraIdentity, null, null, new Track("TR1", true, false)));
        mvc.perform(get("/api/v1/live/sessions/" + id).header("Authorization", manager.bearer()))
                .andExpect(jsonPath("$.status").value("waiting_camera")).andExpect(jsonPath("$.cameraOnline").value(false));
    }
}
