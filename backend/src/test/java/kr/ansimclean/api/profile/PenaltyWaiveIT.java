package kr.ansimclean.api.profile;

import kr.ansimclean.api.ItSupport;
import kr.ansimclean.api.MutableClock;
import kr.ansimclean.api.common.time.AppClock;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 통합 (Docker 필요): 운영자 패널티 면제 API — 면제하면 점수와 콜 배정 정지가 한 트랜잭션에서 함께 바뀐다 (문제 목록 3번).
 * 예전에는 API 가 없어 운영자가 SQL 로 status 만 'waived' 로 바꿨고, 점수는 빠졌지만 manager_profiles.suspended_until 이 남았다.
 * 시계는 2026-09-15(화) 14:00 서울에 멈춰 있다.
 */
// 통합 테스트(CODE_GUIDE.md 3-12). MockMvc 로 컨트롤러부터 DB 까지 실제 코드를 탄다.
// 바꿔 끼우는 것: 시계(MutableClock), JwtDecoder(가짜 — ItSupport 가 토큰을 사용자로 풀리게 정한다), 운영자 토큰(TestPropertySource).
// 벌점은 매니저 앱 흐름(미방문 = StaleBookingCleaner) 대신 ManagerPenaltyService.record 를 직접 불러 쌓는다 — 예약 없이(bookingId null) 적을 수 있다.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(PenaltyWaiveIT.FixedClock.class)
@org.springframework.test.context.TestPropertySource(properties = "app.admin.token=" + PenaltyWaiveIT.ADMIN)
class PenaltyWaiveIT {
    static final String ADMIN = "it-admin"; // 테스트 전용 운영자 토큰 값 — 위 설정과 X-Admin-Token 헤더에 같이 쓴다

    // Container + static = 이 클래스의 테스트 전체가 DB 컨테이너 하나를 같이 쓴다 (그래서 setUp 에서 데이터를 비운다)
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    static final MutableClock CLOCK = new MutableClock(Instant.parse("2026-09-15T05:00:00Z"), AppClock.ZONE); // 서울 화 14:00

    /** 서버의 AppClock 을 위 CLOCK 을 쓰는 것으로 바꿔 끼운다 (Primary = 같은 타입 빈이 둘이면 이쪽) */
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
    ManagerPenaltyService penalties;
    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc; // 데이터 비우기와 결과(행 상태·정지 칸) 확인
    @Autowired
    org.springframework.transaction.PlatformTransactionManager txManager; // 테스트 안에서 트랜잭션 두 개(바깥·안쪽 새 것)를 직접 연다
    @MockitoBean
    JwtDecoder jwtDecoder;

    ItSupport.User manager;

    /** 테스트마다: 사용자 데이터를 비우고 시계를 되돌린 뒤 강남구 승인 매니저 한 명을 만든다 */
    @BeforeEach
    void setUp() {
        ItSupport.resetUserData(jdbc);
        CLOCK.set(Instant.parse("2026-09-15T05:00:00Z"));
        manager = ItSupport.approvedManager(profiles, availability, jwtDecoder, "김미영", "서울특별시 강남구", "서울특별시");
    }

    /** DB 의 이 매니저 정지 종료일 (없으면 null) */
    LocalDate suspendedUntil() {
        return jdbc.queryForObject("select suspended_until from manager_profiles where manager_id = ?", LocalDate.class, manager.id());
    }

    // 미방문 두 번(6점) → 9/22 까지 정지. 토큰 없이 부르면 401 이고 아무것도 바뀌지 않는다.
    // 토큰과 메모로 두 번째를 면제 → { points "3", suspendedUntil null }, DB 에는 waived + 메모, 정지 칸이 비고, 매니저 패널티 내역도 3점·정지 없음.
    // 같은 요청을 다시 보내도 같은 응답(이미 면제). 없는 id 는 404
    @Test
    void 운영자가_면제하면_점수가_빠지고_정지도_함께_풀린다() throws Exception {
        penalties.record(manager.id(), null, null, ManagerPenalty.NO_SHOW, null, null);                          // 3
        ManagerPenalty second = penalties.record(manager.id(), null, null, ManagerPenalty.NO_SHOW, null, null);  // 6 → 정지
        assertThat(suspendedUntil()).isEqualTo(LocalDate.of(2026, 9, 22));
        String url = "/api/v1/admin/penalties/" + second.getId() + "/waive";
        String body = "{\"note\":\"진단서 확인\"}";

        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        assertThat(suspendedUntil()).isEqualTo(LocalDate.of(2026, 9, 22));

        for (int i = 0; i < 2; i++) { // 두 번째는 이미 면제된 기록 — 같은 응답
            mvc.perform(post(url).header("X-Admin-Token", ADMIN).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.penaltyId").value(second.getId().toString()))
                    .andExpect(jsonPath("$.managerId").value(manager.id().toString()))
                    .andExpect(jsonPath("$.points").value("3"))
                    .andExpect(jsonPath("$.suspendedUntil").doesNotExist());
        }
        Map<String, Object> row = jdbc.queryForMap("select status, waived_note, waived_at from manager_penalties where id = ?", second.getId());
        assertThat(row.get("status")).isEqualTo("waived");
        assertThat(row.get("waived_note")).isEqualTo("진단서 확인");
        assertThat(row.get("waived_at")).isNotNull();
        assertThat(suspendedUntil()).isNull();

        mvc.perform(get("/api/v1/manager/penalties").header("Authorization", manager.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.points").value("3")).andExpect(jsonPath("$.suspension").doesNotExist());

        mvc.perform(post("/api/v1/admin/penalties/" + UUID.randomUUID() + "/waive").header("X-Admin-Token", ADMIN)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
    }

    // 고객이 정기청소(매월, 첫 방문 9/18 금 10:00)를 만든다 → 매니저가 그 회차를 늦게 취소한 기록(1점, 시리즈 id 포함)을 적는다 →
    // 운영자가 면제해도 droppedSeries 는 true — 그 시리즈의 다음 회차 우선 제안에서 계속 빠진다 (문제 목록 27번, 사용자 결정 A).
    // 실제 DB 의 조회(existsByManagerIdAndSeriesIdAndCategoryIn)가 면제(waived) 행도 세는지 본다
    @Test
    void 면제된_정기청소_취소도_다음_회차_우선_제안에서_빠진다() throws Exception {
        ItSupport.User customer = ItSupport.signedUp(profiles, jwtDecoder, "customer", "김하늘");
        String res = mvc.perform(post("/api/v1/customer/bookings").header("Authorization", customer.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(ItSupport.bookingBody("2026-09-18", "10:00").replace("\"oneTime\"", "\"recurring\"")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        tools.jackson.databind.JsonNode booking = tools.jackson.databind.json.JsonMapper.builder().build().readTree(res);
        UUID bookingId = UUID.fromString(booking.get("id").asString());
        UUID seriesId = UUID.fromString(booking.get("seriesId").asString());

        ManagerPenalty cancel = penalties.record(manager.id(), bookingId, seriesId, ManagerPenalty.CANCEL_LATE, "sick", null);
        assertThat(penalties.droppedSeries(manager.id(), seriesId)).isTrue();
        mvc.perform(post("/api/v1/admin/penalties/" + cancel.getId() + "/waive").header("X-Admin-Token", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"진단서 확인\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.points").value("0"));

        assertThat(penalties.droppedSeries(manager.id(), seriesId)).isTrue();          // 예전: 면제되면 false 라 다음 회차 우선 콜이 다시 갔다
        assertThat(penalties.bookingDroppers(bookingId)).containsExactly(manager.id()); // 같은 예약 후보 제외와 같은 규칙
    }

    // OfferExpirer(closeBlockedOffers)가 하는 순서를 흉내 낸다: 한 트랜잭션에서 approvedManagers() 로 매니저 객체를 먼저 올린다(정지 중이라 목록에는 없다) →
    // 그사이 다른 트랜잭션(REQUIRES_NEW = 바깥과 따로 여는 새 트랜잭션)이 면제로 정지를 풀고 커밋한다 → 같은 바깥 트랜잭션에서 다시 본다.
    // isApprovedManager 는 먼저 올려 둔 객체(1차 캐시)의 옛 정지 값을 보지만, isApprovedManagerNow 는 DB 의 지금 값을 본다 (합친 38건 검토 R3)
    @Test
    void 정지가_풀렸는지_다시_볼_때는_먼저_올려_둔_객체가_아니라_DB_의_지금_값을_본다() {
        penalties.record(manager.id(), null, null, ManagerPenalty.NO_SHOW, null, null);                          // 3
        ManagerPenalty second = penalties.record(manager.id(), null, null, ManagerPenalty.NO_SHOW, null, null);  // 6 → 정지
        org.springframework.transaction.support.TransactionTemplate outer = new org.springframework.transaction.support.TransactionTemplate(txManager);
        org.springframework.transaction.support.TransactionTemplate inner = new org.springframework.transaction.support.TransactionTemplate(txManager);
        inner.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        outer.executeWithoutResult(tx -> {
            assertThat(profiles.approvedManagers()).extracting(ManagerProfile::getManagerId).doesNotContain(manager.id()); // 정지 중 — 객체는 이미 올라왔다
            inner.executeWithoutResult(t -> penalties.waive(second.getId(), "진단서 확인"));                                   // 정지를 풀고 커밋
            assertThat(profiles.isApprovedManager(manager.id())).isFalse();   // 먼저 올려 둔 객체의 옛 값 (예전 재검사가 본 것)
            assertThat(profiles.isApprovedManagerNow(manager.id())).isTrue(); // DB 의 지금 값
        });
    }

    // 정지 중인 매니저가 이미 맡은 방문을 완료하는 트랜잭션(바깥 — 매니저 프로필을 잠그지 않고 읽는다)이 도는 사이, 운영자 면제(안쪽 REQUIRES_NEW)가
    // 정지를 풀고 먼저 커밋했다. 바깥이 완료 수를 올려 커밋해도 풀린 정지가 되살아나지 않고, 완료 수도 오른다 — ManagerProfile 의 @DynamicUpdate 로
    // 바뀐 칸만 UPDATE 하기 때문. 예전에는 전체 칸 UPDATE 가 옛 suspended_until 을 다시 적었다 (합친 38건 검토 R6)
    @Test
    void 완료_수를_올리는_사이_면제로_풀린_정지가_되살아나지_않는다() {
        penalties.record(manager.id(), null, null, ManagerPenalty.NO_SHOW, null, null);
        ManagerPenalty second = penalties.record(manager.id(), null, null, ManagerPenalty.NO_SHOW, null, null); // 6 → 9/22 까지 정지
        Integer completedBefore = jdbc.queryForObject("select completed_count from manager_profiles where manager_id = ?", Integer.class, manager.id());
        org.springframework.transaction.support.TransactionTemplate outer = new org.springframework.transaction.support.TransactionTemplate(txManager);
        org.springframework.transaction.support.TransactionTemplate inner = new org.springframework.transaction.support.TransactionTemplate(txManager);
        inner.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        outer.executeWithoutResult(tx -> {
            profiles.incrementCompleted(manager.id());                                        // 정지 값(9/22)이 든 객체를 읽고 완료 수만 바꾼다 — 저장은 커밋 때
            inner.executeWithoutResult(t -> penalties.waive(second.getId(), "진단서 확인"));   // 그사이 면제가 정지를 풀고 커밋
        });
        assertThat(suspendedUntil()).isNull();
        assertThat(jdbc.queryForObject("select completed_count from manager_profiles where manager_id = ?", Integer.class, manager.id())).isEqualTo(completedBefore + 1);
    }

    // 미방문 세 번(9점) → 30일 정지(10/15 까지). 하나를 면제해도 6점이라 가장 낮은 정지 기준(5점) 이상 — 정지는 그대로이고 응답에 종료일이 보인다
    @Test
    void 면제해도_정지_기준_이상이면_정지는_그대로다() throws Exception {
        penalties.record(manager.id(), null, null, ManagerPenalty.NO_SHOW, null, null);
        penalties.record(manager.id(), null, null, ManagerPenalty.NO_SHOW, null, null);
        ManagerPenalty third = penalties.record(manager.id(), null, null, ManagerPenalty.NO_SHOW, null, null); // 9 → 10/15 까지
        mvc.perform(post("/api/v1/admin/penalties/" + third.getId() + "/waive").header("X-Admin-Token", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"사고 확인서\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.points").value("6")).andExpect(jsonPath("$.suspendedUntil").value("2026-10-15"));
        assertThat(suspendedUntil()).isEqualTo(LocalDate.of(2026, 10, 15));
    }
}
