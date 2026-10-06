package kr.ansimclean.api.booking;

import kr.ansimclean.api.MutableClock;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.profile.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import kr.ansimclean.api.ItSupport;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 통합 (Docker 필요, BACKEND_PLAN.md §6.9): 같은 키 재요청은 예약 1건·같은 응답, 본문이 다르면 422, 동시 요청은 하나만 통과.
 */
// 멱등 키 규칙은 CODE_GUIDE.md 3-7, 구현은 common/web/Idempotent. 여기서는 예약 생성·콜 수락 API 로 실제 DB 에서 확인한다.
// MockMvc 로 HTTP 요청을 보낸다. 로그인 토큰 검증기는 가짜(@MockitoBean)다.
// 시계는 낮(서울 화 14:00)에 고정한다 — 실제 시계면 밤(21시~08시)에 돌릴 때 새 콜이 아침으로 미뤄져(문제 목록 2번) 콜을 찾는 테스트가 실패했다.
// 날짜는 그 '오늘부터 7일 뒤 평일' 로 고른다. DB 는 테스트 사이에 비우지 않고, 테스트마다 새 고객을 만든다.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(IdempotencyIT.FixedClock.class)
class IdempotencyIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    static final MutableClock CLOCK = new MutableClock(Instant.parse("2026-09-15T05:00:00Z"), AppClock.ZONE); // 서울 2026-09-15(화) 14:00

    /** 테스트에서만 쓰는 스프링 설정: @Primary 로 서버의 원래 AppClock 빈 대신 CLOCK 을 감싼 AppClock 을 쓰게 한다 */
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
    ProfileService profiles; // 가입을 마친 사용자 만들기 (ItSupport)
    @Autowired
    kr.ansimclean.api.schedule.AvailabilityService availability; // 승인 매니저의 기본 근무시간 만들기
    @Autowired
    kr.ansimclean.api.offer.OfferRepository offers; // 예약 생성으로 만들어진 콜(오퍼)의 id 를 찾을 때

    @MockitoBean
    JwtDecoder jwtDecoder;

    final JsonMapper json = JsonMapper.builder().build(); // 응답 문자열을 JsonNode 로 읽는 도구
    String auth; // 이번 테스트 고객의 Authorization 헤더 값
    String date; // 예약 날짜 ("YYYY-MM-DD")

    /** 테스트마다 가입을 마친 새 고객과 예약할 날짜를 준비한다 */
    @BeforeEach
    void setUp() {
        auth = ItSupport.signedUp(profiles, jwtDecoder, "customer", "김하늘").bearer();
        // 고정한 시계의 "다음 주 평일" 을 고른다
        LocalDate d = LocalDate.now(CLOCK).plusDays(7);
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) d = d.plusDays(1);
        date = d.toString();
    }

    /** 1회 가정 청소 생성 본문. request(요청사항) 글자만 바꿔 '같은 본문 / 다른 본문' 을 만든다 */
    String body(String request) {
        return "{\"category\":\"home\",\"frequency\":\"oneTime\",\"date\":\"" + date + "\",\"time\":\"10:00\",\"roadAddress\":\"부산광역시 중앙대로 2127-23\"," +
                "\"freeOptions\":[],\"paidOptions\":[],\"notices\":[],\"request\":\"" + request + "\"}";
    }

    /** 이 고객으로 POST /api/v1/customer/bookings 를 보내고 응답을 그대로 돌려준다 (상태 코드 검사는 부르는 쪽이 한다) */
    MvcResult create(String key, String request) throws Exception {
        return mvc.perform(post("/api/v1/customer/bookings").header("Authorization", auth).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body(request))).andReturn();
    }

    @Test
    void 같은_키라도_다른_사용자면_별개다() throws Exception {
        // (key, userId) 로 행을 나눈다 (§6.9) — 다른 고객이 같은 키를 보내도 남의 응답을 받지 않는다
        String key = UUID.randomUUID().toString();
        assertThat(create(key, "a").getResponse().getStatus()).isEqualTo(200);
        ItSupport.User other = ItSupport.signedUp(profiles, jwtDecoder, "customer", "박지훈");
        MvcResult res = mvc.perform(post("/api/v1/customer/bookings").header("Authorization", other.bearer()).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body("a"))).andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        mvc.perform(get("/api/v1/customer/reservations").header("Authorization", other.bearer())).andExpect(jsonPath("$.reservations.length()").value(1));
        mvc.perform(get("/api/v1/customer/reservations").header("Authorization", auth)).andExpect(jsonPath("$.reservations.length()").value(1));
        // 키가 너무 길면 400 VALIDATION — Idempotent.MAX_KEY_LENGTH 검사 (키 열이 임의 문자열로 자라지 않게)
        mvc.perform(post("/api/v1/customer/bookings").header("Authorization", auth).header("Idempotency-Key", "k".repeat(200))
                .contentType(MediaType.APPLICATION_JSON).content(body("a"))).andExpect(status().isBadRequest());
    }

    // 준비: 부산 활동 승인 매니저 + 부산 주소 예약 두 건(다른 날). 아래 조회는 두 예약 모두 그 매니저에게 콜이 갔다고 기대한다.
    // 확인: 같은 키로 콜1 수락 두 번은 200·200(두 번째는 저장된 응답), 같은 키로 콜2 수락은 422 IDEMPOTENCY_MISMATCH
    @Test
    void 매니저_수락은_같은_키로_다른_오퍼를_수락하면_422() throws Exception {
        ItSupport.User manager = ItSupport.approvedManager(profiles, availability, jwtDecoder, "김미영", "부산광역시", "부산광역시");
        String key = UUID.randomUUID().toString();
        // 둘째 예약 날짜 = 첫 예약 다음 날, 주말이면 다음 평일까지 민다
        String d2 = LocalDate.parse(date).plusDays(1).toString();
        while (LocalDate.parse(d2).getDayOfWeek() == DayOfWeek.SATURDAY || LocalDate.parse(d2).getDayOfWeek() == DayOfWeek.SUNDAY) d2 = LocalDate.parse(d2).plusDays(1).toString();
        UUID b1 = UUID.fromString(json.readTree(create(UUID.randomUUID().toString(), "1").getResponse().getContentAsString(StandardCharsets.UTF_8)).get("id").asString());
        UUID b2 = UUID.fromString(json.readTree(mvc.perform(post("/api/v1/customer/bookings").header("Authorization", auth).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body("2").replace(date, d2))).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).get("id").asString());
        // 각 예약의 콜 중 이 매니저에게 간 것 (콜이 안 갔으면 orElseThrow 가 테스트를 실패시킨다)
        UUID o1 = offers.findByBookingId(b1).stream().filter(o -> o.getManagerId().equals(manager.id())).findFirst().orElseThrow().getId();
        UUID o2 = offers.findByBookingId(b2).stream().filter(o -> o.getManagerId().equals(manager.id())).findFirst().orElseThrow().getId();
        mvc.perform(post("/api/v1/manager/offers/" + o1 + "/accept").header("Authorization", manager.bearer()).header("Idempotency-Key", key)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/manager/offers/" + o1 + "/accept").header("Authorization", manager.bearer()).header("Idempotency-Key", key)).andExpect(status().isOk()); // 재시도 = 저장된 응답
        // 수락은 "accept:<콜 id>" 를 본문 해시로 쓰므로(OfferService.accept) 콜이 다르면 같은 키라도 내용이 다르다
        mvc.perform(post("/api/v1/manager/offers/" + o2 + "/accept").header("Authorization", manager.bearer()).header("Idempotency-Key", key))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_MISMATCH"));
    }

    // 같은 키·같은 본문 두 번 → 둘 다 200 이고 응답 글자까지 같다. 같은 키·다른 본문 → 422. 예약은 끝까지 1건
    @Test
    void 같은_키_재요청은_예약_1건이고_본문이_다르면_422() throws Exception {
        String key = UUID.randomUUID().toString();
        MvcResult first = create(key, "a");
        MvcResult second = create(key, "a");
        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        assertThat(second.getResponse().getContentAsString(StandardCharsets.UTF_8)).isEqualTo(first.getResponse().getContentAsString(StandardCharsets.UTF_8));

        mvc.perform(post("/api/v1/customer/bookings").header("Authorization", auth).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body("b")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_MISMATCH"));

        mvc.perform(get("/api/v1/customer/reservations").header("Authorization", auth)).andExpect(jsonPath("$.reservations.length()").value(1));
    }

    // 시각을 10:30(운영 시간대 밖)으로 바꿔 409 가 나게 한다 — 업무가 실패하면 키 기록도 같은 트랜잭션에서 되돌려진다.
    // 그래서 같은 키로 고친 본문을 보내면 422 가 아니라 새 요청으로 처리돼 200
    @Test
    void 실패한_요청은_키를_남기지_않아_고친_뒤_같은_키로_다시_보낼_수_있다() throws Exception {
        String key = UUID.randomUUID().toString();
        mvc.perform(post("/api/v1/customer/bookings").header("Authorization", auth).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body("x").replace("\"time\":\"10:00\"", "\"time\":\"10:30\"")))
                .andExpect(status().isConflict());
        assertThat(create(key, "x").getResponse().getStatus()).isEqualTo(200);
    }

    // 스레드 4개가 같은 키·같은 본문으로 거의 동시에 보낸다. 끝나면 예약은 1건이어야 한다
    @Test
    void 동시에_같은_키가_오면_하나만_저장된다() throws Exception {
        String key = UUID.randomUUID().toString();
        // ExecutorService = 스레드 묶음. submit 한 작업마다 Future(나중에 결과를 받을 표)를 준다
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Callable<Integer> task = () -> create(key, "c").getResponse().getStatus();
            results.add(pool.submit(task));
        }
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) statuses.add(f.get()); // get() = 그 작업이 끝날 때까지 기다려 상태 코드를 받는다
        pool.shutdown();
        // 200 (처음 또는 저장된 응답) 아니면 409 IN_PROGRESS — 그 밖의 상태는 없다
        assertThat(statuses).allMatch(s -> s == 200 || s == 409);
        assertThat(statuses).contains(200);
        mvc.perform(get("/api/v1/customer/reservations").header("Authorization", auth)).andExpect(jsonPath("$.reservations.length()").value(1));
    }
}
