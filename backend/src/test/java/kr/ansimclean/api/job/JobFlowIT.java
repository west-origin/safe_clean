package kr.ansimclean.api.job;

import kr.ansimclean.api.ItSupport;
import kr.ansimclean.api.MutableClock;
import kr.ansimclean.api.common.time.AppClock;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 통합 (Docker 필요, B4 완료 기준 JobFlowIT): 수락 → 시작 → 체크리스트 → 일시정지 → 재개 → 완료 → 정산 pending · 다른 날짜 작업 시작 409 · 동시에 두 작업 409 ·
 * 고객 내 예약 완료 탭. 라이브(바디캠)는 B5 — 세션이 created 라 일시정지 선행 조건은 통과한다.
 * 준비: 통합 테스트(CODE_GUIDE.md 3-12). 요청은 MockMvc 로 실제 API 경로에 보낸다.
 * 로그인 토큰 검증(JwtDecoder)만 가짜이고, 사용자·예약·콜은 ItSupport 와 API 로 만든다.
 * 시계는 옮길 수 있는 MutableClock(CLOCK.set) — 서버 코드가 쓰는 AppClock 을 이것으로 바꿔 '다음 날 09:50' 같은 시각을 만든다.
 * 운영자 토큰(app.admin.token)은 @TestPropertySource 로 ADMIN 값을 넣는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(JobFlowIT.FixedClock.class)
@org.springframework.test.context.TestPropertySource(properties = "app.admin.token=" + JobFlowIT.ADMIN)
class JobFlowIT {
    static final String ADMIN = "it-admin"; // 테스트용 운영자 토큰 값 — 위 @TestPropertySource 가 app.admin.token 에 넣는다

    // 클래스에 하나인 Postgres 16 컨테이너. static 이라 이 클래스의 테스트들이 같이 쓴다 (그래서 setUp 이 데이터를 비운다)
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    static final MutableClock CLOCK = new MutableClock(Instant.parse("2026-09-15T05:00:00Z"), AppClock.ZONE); // 시작 시각 = 9월 15일(화) 14:00 서울

    /** 테스트 전용 설정: 서버의 AppClock 대신 위 CLOCK 을 쓰는 AppClock 을 등록한다. @Primary = 같은 종류의 빈이 둘이면 이것을 쓴다 */
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
    kr.ansimclean.api.booking.LateStartNotifier lateNotifier;
    @MockitoBean
    JwtDecoder jwtDecoder;

    final JsonMapper json = JsonMapper.builder().build();
    ItSupport.User customer;
    ItSupport.User manager;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    // 테스트마다: 사용자 데이터를 비우고 시계를 되돌린 뒤 고객 1명, 승인된 매니저 1명(평일 09–18 근무, 서울 강남구)을 새로 만든다
    @BeforeEach
    void setUp() {
        ItSupport.resetUserData(jdbc);
        CLOCK.set(Instant.parse("2026-09-15T05:00:00Z"));
        customer = ItSupport.signedUp(profiles, jwtDecoder, "customer", "김하늘");
        manager = ItSupport.approvedManager(profiles, availability, jwtDecoder, "김미영", "서울특별시 강남구", "서울특별시");
    }

    /** 예약을 만들고 매니저가 수락한 상태로 */
    // 고객 API 로 예약 생성 → 그 예약에 생긴 대기(pending) 콜을 DB 에서 찾아 → 매니저 API 로 수락. 돌려주는 값 = 예약 id (= 작업 id)
    UUID scheduled(String date, String time) throws Exception {
        String res = mvc.perform(post("/api/v1/customer/bookings").header("Authorization", customer.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(ItSupport.bookingBody(date, time))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        UUID id = UUID.fromString(json.readTree(res).get("id").asString());
        Offer offer = offers.findByBookingId(id).stream().filter(Offer::isPending).findFirst().orElseThrow();
        mvc.perform(post("/api/v1/manager/offers/" + offer.getId() + "/accept").header("Authorization", manager.bearer())).andExpect(status().isOk());
        return id;
    }

    /** 매니저 토큰으로 GET 을 보내 200 인지 확인하고 응답 본문을 JSON 트리로 읽는다 */
    JsonNode getJson(String path) throws Exception {
        return json.readTree(mvc.perform(get(path).header("Authorization", manager.bearer())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    // 한 매니저에게 9/16(수) 10:00·14:00 두 건과 9/17 10:00 한 건을 배정해 두고, 시각을 옮겨 가며 작업 한 건의 전 과정을 API 로 밟는다.
    // CLOCK 값은 UTC 다 — 서울 시각은 9시간을 더해 읽는다 (예: 00:50Z = 09:50 서울)
    @Test
    void 시작부터_완료_정산까지() throws Exception {
        UUID today1 = scheduled("2026-09-16", "10:00");
        UUID today2 = scheduled("2026-09-16", "14:00");
        UUID later = scheduled("2026-09-17", "10:00");

        // 9/16 09:50 — 다른 날짜 작업은 시작 불가
        CLOCK.set(Instant.parse("2026-09-16T00:50:00Z"));
        mvc.perform(post("/api/v1/manager/jobs/" + later + "/start").header("Authorization", manager.bearer()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
        mvc.perform(post("/api/v1/manager/jobs/" + today1 + "/start").header("Authorization", manager.bearer()).header("Idempotency-Key", "s1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.startedAt").value("2026-09-16T00:50:00Z"));
        // 같은 키 재요청 = 같은 응답, 다른 작업 동시 시작은 409
        mvc.perform(post("/api/v1/manager/jobs/" + today1 + "/start").header("Authorization", manager.bearer()).header("Idempotency-Key", "s1")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/manager/jobs/" + today2 + "/start").header("Authorization", manager.bearer()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.message").value("이미 진행 중인 작업이 있어요"));

        // 매니저 홈: 진행 중 카드 = today1, 오늘 작업 2건, 남은 일정(오늘 아직 시작 안 한 작업) = today2 한 건
        JsonNode home = getJson("/api/v1/manager/home");
        assertThat(home.get("inProgress").get("jobId").asString()).isEqualTo(today1.toString());
        assertThat(home.get("today").get("count").asInt()).isEqualTo(2);
        assertThat(home.get("remaining")).hasSize(1);

        // 진행 중 화면: 막 시작해 경과 0초, 바디캠이 붙지 않아 '카메라 연결 대기 중', 체크리스트 묶음 3개. 첫 항목을 체크해 둔다
        JsonNode active = getJson("/api/v1/manager/jobs/active");
        assertThat(active.get("id").asString()).isEqualTo(today1.toString());
        assertThat(active.get("elapsed").asString()).isEqualTo("00:00:00");
        assertThat(active.get("since").asString()).isEqualTo("09:50부터 진행 · 카메라 연결 대기 중");
        assertThat(active.get("checklist")).hasSize(3);
        String itemId = active.get("checklist").get(0).get("items").get(0).get("id").asString();
        mvc.perform(put("/api/v1/manager/jobs/" + today1 + "/checklist/" + itemId).header("Authorization", manager.bearer()).contentType(MediaType.APPLICATION_JSON).content("{\"done\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.done").value(true));
        mvc.perform(put("/api/v1/manager/jobs/" + today1 + "/checklist/" + itemId).header("Authorization", manager.bearer()).contentType(MediaType.APPLICATION_JSON).content("{\"done\":true}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from job_checklist_events where item_id = ?::uuid", Integer.class, itemId)).isEqualTo(1);
        mvc.perform(get("/api/v1/customer/bookings/" + today1 + "/checklist").header("Authorization", customer.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.done").value(1))
                .andExpect(jsonPath("$.timeline[0].id").value(itemId)).andExpect(jsonPath("$.timeline[0].offsetSeconds").doesNotExist());
        ItSupport.User stranger = ItSupport.signedUp(profiles, jwtDecoder, "customer", "다른 고객");
        mvc.perform(get("/api/v1/customer/bookings/" + today1 + "/checklist").header("Authorization", stranger.bearer())).andExpect(status().isNotFound());

        // 일시정지 (라이브 세션은 created → 영상이 안 나가므로 통과) → 재개
        CLOCK.set(Instant.parse("2026-09-16T01:00:00Z")); // 10:00 서울
        // 중단 사유 목록(catalog_reasons)에 없는 문구는 400
        mvc.perform(post("/api/v1/manager/jobs/" + today1 + "/pause").header("Authorization", manager.bearer()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"없는 사유\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/manager/jobs/" + today1 + "/pause").header("Authorization", manager.bearer()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"고객 요청\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.since").value("10:00부터 중단됨"));
        assertThat(getJson("/api/v1/manager/jobs/active/pause").get("note").asString()).isEqualTo("촬영이 중단된 동안에는 고객 화면과 녹화 영상에 아무것도 나오지 않아요. 정산 금액은 그대로예요.");
        CLOCK.set(Instant.parse("2026-09-16T01:10:00Z")); // 10:10 서울 — 10분(600초) 멈췄다가 재개
        mvc.perform(post("/api/v1/manager/jobs/" + today1 + "/resume").header("Authorization", manager.bearer())).andExpect(status().isOk());
        // 경과 = 시작 뒤 20분 − 중단 10분 = 10분
        JsonNode resumed = getJson("/api/v1/manager/jobs/active");
        assertThat(resumed.get("pausedSeconds").asLong()).isEqualTo(600);
        assertThat(resumed.get("elapsed").asString()).isEqualTo("00:10:00");

        // 완료 → 정산 pending, 완료 화면, 고객 완료 탭
        CLOCK.set(Instant.parse("2026-09-16T04:00:00Z")); // 13:00 서울
        mvc.perform(post("/api/v1/manager/jobs/" + today1 + "/complete").header("Authorization", manager.bearer()).header("Idempotency-Key", "c1")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/manager/jobs/active").header("Authorization", manager.bearer())).andExpect(status().isNotFound());
        // 완료 화면 요약 행: [0] 청소 시간 [1] 총 소요 [2] 체크리스트(묶음 3개의 항목 9개 중 위에서 체크한 1개) [3] 촬영 기록(녹화가 없어 '기록 없음')
        JsonNode done = getJson("/api/v1/manager/jobs/last-completed");
        assertThat(done.get("summary").get(0).get("value").asString()).isEqualTo("09:50 - 13:00");
        assertThat(done.get("summary").get(2).get("value").asString()).isEqualTo("9개 중 1개 완료");
        assertThat(done.get("summary").get(3).get("value").asString()).isEqualTo("기록 없음");
        assertThat(done.get("payout").get("amount").asString()).isEqualTo("90,000원");
        // 완료와 같은 트랜잭션에서 정산 행이 생겼다: 정산 목록(이번 달 합계·항목 상태 settlementPending)·상세 금액,
        // 매니저 프로필 stats[1](완료 청소 건수), 고객 예약 목록에서 이 예약이 완료(done) 탭에 들어갔는지
        JsonNode settlement = getJson("/api/v1/manager/settlements");
        assertThat(settlement.get("monthly").get("amount").asString()).isEqualTo("90,000원");
        assertThat(settlement.get("items").get(0).get("status").asString()).isEqualTo("settlementPending");
        assertThat(settlement.get("items").get(0).get("title").asString()).isEqualTo("강남구 기본청소");
        String sid = settlement.get("items").get(0).get("id").asString();
        assertThat(getJson("/api/v1/manager/settlements/" + sid).get("final").get("value").asString()).isEqualTo("90,000원");
        mvc.perform(get("/api/v1/manager/profile").header("Authorization", manager.bearer())).andExpect(jsonPath("$.stats[1].value").value("1건"));
        String reservations = mvc.perform(get("/api/v1/customer/reservations").header("Authorization", customer.bearer())).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(json.readTree(reservations).get("reservations").valueStream().filter(r -> r.get("id").asString().equals(today1.toString())).findFirst().orElseThrow().get("tab").asString()).isEqualTo("done");

        // 첫 작업이 끝났으니 두 번째를 시작할 수 있다 (남은 작업 때문에 탈퇴가 막히는지는 여기서 보지 않는다 — BookingDeletionGuardTest)
        mvc.perform(post("/api/v1/manager/jobs/" + today2 + "/start").header("Authorization", manager.bearer())).andExpect(status().isOk());
        // today2 는 오늘 방문이라 고객 연락처(방문일 ±1일에만 열림)가 보이고, 지도 검색어는 예약의 도로명 주소다
        mvc.perform(get("/api/v1/manager/jobs/" + today2 + "/contact").header("Authorization", manager.bearer())).andExpect(status().isOk()).andExpect(jsonPath("$.mapQuery").value("서울특별시 강남구 테헤란로 123"));
    }

    // 작업을 시작만 하고 이틀을 보낸 뒤 → 운영자 API: 토큰 없으면 401, 토큰(X-Admin-Token)이 맞으면 완료되고
    // 두 번 불러도 200(이미 완료면 그대로) → 진행 중 작업이 없어지고 정산은 한 건만 생겼는지 본다
    @Test
    void 청소_완료를_누르지_않고_떠난_작업은_운영자가_닫고_정산이_생긴다() throws Exception { // 자체 점검 #17
        UUID job = scheduled("2026-09-16", "10:00");
        CLOCK.set(Instant.parse("2026-09-16T00:50:00Z"));
        mvc.perform(post("/api/v1/manager/jobs/" + job + "/start").header("Authorization", manager.bearer())).andExpect(status().isOk());

        CLOCK.set(Instant.parse("2026-09-18T00:00:00Z")); // 이틀이 지나도 in_progress
        mvc.perform(post("/api/v1/admin/jobs/" + job + "/complete")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/jobs/" + job + "/complete").header("X-Admin-Token", ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.jobId").value(job.toString()));
        mvc.perform(post("/api/v1/admin/jobs/" + job + "/complete").header("X-Admin-Token", ADMIN)).andExpect(status().isOk()); // 다시 불러도 그대로
        mvc.perform(get("/api/v1/manager/jobs/active").header("Authorization", manager.bearer())).andExpect(status().isNotFound());
        assertThat(getJson("/api/v1/manager/settlements").get("items")).hasSize(1);
    }

    @Test
    void 동시에_같은_항목을_체크해도_이력은_한_줄이고_완료_뒤_변경은_거부한다() throws Exception {
        UUID job = scheduled("2026-09-16", "10:00");
        CLOCK.set(Instant.parse("2026-09-16T01:00:00Z"));
        mvc.perform(post("/api/v1/manager/jobs/" + job + "/start").header("Authorization", manager.bearer())).andExpect(status().isOk());
        String item = getJson("/api/v1/manager/jobs/active").get("checklist").get(0).get("items").get(0).get("id").asString();
        var gate = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> check = () -> {
                gate.await();
                return mvc.perform(put("/api/v1/manager/jobs/" + job + "/checklist/" + item).header("Authorization", manager.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"done\":true}")).andReturn().getResponse().getStatus();
            };
            var first = pool.submit(check);
            var second = pool.submit(check);
            gate.countDown();
            assertThat(first.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(second.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(200);
        }
        assertThat(jdbc.queryForObject("select count(*) from job_checklist_events where item_id=?::uuid", Integer.class, item)).isEqualTo(1);
        mvc.perform(post("/api/v1/manager/jobs/" + job + "/complete").header("Authorization", manager.bearer())).andExpect(status().isOk());
        mvc.perform(put("/api/v1/manager/jobs/" + job + "/checklist/" + item).header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"done\":false}")).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from job_checklist_events where item_id=?::uuid", Integer.class, item)).isEqualTo(1);
    }

    // LateStartNotifier.notifyDue 를 스케줄러 대신 직접 부른다 (돌려주는 값 = 이번에 안내를 쌓은 예약 수).
    // 10:29 에는 0건 → 10:31 에 1건 → 한 번 더 불러도 0건(예약에 '알림 보냄' 표시가 남아 두 번 보내지 않는다).
    // 푸시는 아웃박스에 먼저 적히므로 outbox_events 에 ManagerLate 행이 이 예약 id 로 1개인지 SQL 로 센다
    @Test
    void 약속_시간에서_30분이_지나도_시작하지_않으면_고객_안내가_한_번_쌓인다() throws Exception { // 자체 점검 #25
        UUID job = scheduled("2026-09-16", "10:00");
        CLOCK.set(Instant.parse("2026-09-16T01:29:00Z")); // 10:29 — 아직
        assertThat(lateNotifier.notifyDue()).isZero();
        CLOCK.set(Instant.parse("2026-09-16T01:31:00Z")); // 10:31
        assertThat(lateNotifier.notifyDue()).isEqualTo(1);
        assertThat(lateNotifier.notifyDue()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where type = 'ManagerLate' and payload::text like ?", Integer.class, "%" + job + "%")).isEqualTo(1);
        mvc.perform(post("/api/v1/manager/jobs/" + job + "/start").header("Authorization", manager.bearer())).andExpect(status().isOk()); // 늦게라도 시작하면 0.5점은 JobService 가
    }
}
