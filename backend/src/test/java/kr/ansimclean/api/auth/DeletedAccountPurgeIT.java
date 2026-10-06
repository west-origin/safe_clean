package kr.ansimclean.api.auth;

import kr.ansimclean.api.ItSupport;
import kr.ansimclean.api.MutableClock;
import kr.ansimclean.api.common.time.AppClock;
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
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 통합 (Docker 필요): 탈퇴 + 5년 파기가 실제 SQL 로 개인정보 칸만 비우고 예약 행은 남긴다 (BACKEND_PLAN §13-22, 사용자 결정 2026-09-27) */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(DeletedAccountPurgeIT.FixedClock.class)
class DeletedAccountPurgeIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    static final MutableClock CLOCK = new MutableClock(Instant.parse("2026-09-15T05:00:00Z"), AppClock.ZONE); // 서울 2026-09-15 14:00

    // 테스트에서만 쓰는 스프링 설정: @Primary 로 서버의 원래 AppClock 빈 대신 CLOCK 을 감싼 AppClock 을 쓰게 한다 (위 @Import 로 등록)
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
    DeletedAccountPurger purger;
    @Autowired
    kr.ansimclean.api.offer.OfferRepository offers;
    @Autowired
    JdbcTemplate jdbc; // SQL 을 직접 실행하는 도구 — 준비 데이터를 바꾸고 결과 칸을 읽는 데 쓴다
    @MockitoBean
    JwtDecoder jwtDecoder; // 가짜 토큰 해석기 (ItSupport 가 "토큰 → 사용자" 를 정한다)

    final JsonMapper json = JsonMapper.builder().build();

    // 매 테스트 전: 사용자 데이터를 비우고 시계를 처음 시각으로 되돌린다 (컨테이너 DB 는 테스트 사이에 롤백되지 않는다)
    @BeforeEach
    void setUp() {
        ItSupport.resetUserData(jdbc);
        CLOCK.set(Instant.parse("2026-09-15T05:00:00Z"));
    }

    @Test
    void 탈퇴_5년이_지난_계정은_개인정보_칸만_비우고_예약_행은_남기며_다시_보지_않는다() throws Exception {
        // 준비 1: 고객이 예약 → 승인된 매니저가 콜 수락 → 고객이 전화번호가 섞인 자유 입력 사유로 취소
        ItSupport.User customer = ItSupport.signedUp(profiles, jwtDecoder, "customer", "김하늘");
        ItSupport.User manager = ItSupport.approvedManager(profiles, availability, jwtDecoder, "김미영", "서울특별시 강남구", "서울특별시");
        String res = mvc.perform(post("/api/v1/customer/bookings").header("Authorization", customer.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(ItSupport.bookingBody("2026-09-21", "10:00"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        UUID booking = UUID.fromString(json.readTree(res).get("id").asString());
        // 예약 생성이 매니저에게 보낸 콜 중 아직 대기 중인 것 하나 (없으면 orElseThrow 로 테스트 실패)
        kr.ansimclean.api.offer.Offer offer = offers.findByBookingId(booking).stream().filter(kr.ansimclean.api.offer.Offer::isPending).findFirst().orElseThrow();
        mvc.perform(post("/api/v1/manager/offers/" + offer.getId() + "/accept").header("Authorization", manager.bearer())).andExpect(status().isOk()); // 배정된 예약 — 취소가 매니저 알림 이벤트를 남긴다
        mvc.perform(post("/api/v1/customer/reservations/" + booking + "/cancel").header("Authorization", customer.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"이사 가요 010-1234-5678\",\"wholeSeries\":false}")).andExpect(status().isOk());
        // 준비 2: ItSupport.signedUp 은 약관 단계를 건너뛰므로 동의 기록을 SQL 로 넣는다 — 파기가 지우는지 보려고
        for (UUID u : new UUID[] { customer.id(), manager.id() }) jdbc.update("insert into term_agreements (user_id, term_id, version) values (?, 'service', 1)", u);
        ItSupport.User recent = ItSupport.signedUp(profiles, jwtDecoder, "customer", "최근탈퇴");
        // 준비 3: 탈퇴 API 대신 SQL 로 deleted_at 을 과거로 적는다. 그래서 탈퇴 때 지우는 근무시간 등이 남아 있어 파기가 지우는지 볼 수 있다.
        // 기준은 서울 달력으로 지금(2026-09-15)의 5년 전 — 2021-03-01 탈퇴는 대상, 2023-03-01 탈퇴는 아직 아니다
        jdbc.update("update profiles set deleted_at = '2021-03-01T00:00:00Z', name = '탈퇴한 사용자' where id in (?, ?)", customer.id(), manager.id()); // 5년 반 전
        // 이 규칙(탈퇴 때 신원·교육 기록 비우기) 전에 탈퇴해 기록이 남은 매니저를 흉내 낸다 — 파기가 비우는지 실제로 본다
        jdbc.update("update manager_profiles set education = '[{\"label\":\"기본 청소 교육\"}]'::jsonb, identity_verified_at = now() where manager_id = ?", manager.id());
        jdbc.update("update profiles set deleted_at = '2023-03-01T00:00:00Z', name = '탈퇴한 사용자' where id = ?", recent.id()); // 3년 반 전 — 아직

        // test 프로필에서는 스케줄러가 꺼져 있어(SchedulingConfig) 파기 메서드를 직접 부른다. 5년이 지난 고객·매니저 둘만 파기
        assertThat(purger.purgeDue()).isEqualTo(2);

        // 확인: 예약 행의 개인정보 칸 · 상태 이력 사유 · 약관 동의 · 근무시간 · 매니저 신원/교육 기록 · 파기 표시(purged_at)
        Map<String, Object> b = jdbc.queryForMap("select road_address, detail_address, request, cardinality(notices) as notices, cancel_reason from bookings where id = ?", booking);
        assertThat(b.get("road_address")).isEqualTo("서울특별시 강남구"); // 지역만 남긴다
        assertThat(b.get("detail_address")).isNull();
        assertThat(b.get("request")).isNull();
        assertThat(((Number) b.get("notices")).intValue()).isZero();
        assertThat(b.get("cancel_reason")).isEqualTo("customer"); // 번호가 든 자유 입력 사유
        assertThat(jdbc.queryForObject("select count(*) from booking_status_history where booking_id = ? and reason like '%010-%'", Integer.class, booking)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from bookings where id = ?", Integer.class, booking)).isEqualTo(1); // 행은 남는다
        assertThat(jdbc.queryForObject("select count(*) from term_agreements where user_id in (?, ?)", Integer.class, customer.id(), manager.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from manager_hours where manager_id = ?", Integer.class, manager.id())).isZero();
        assertThat(jdbc.queryForObject("select education::text from manager_profiles where manager_id = ?", String.class, manager.id())).isEqualTo("[]");
        assertThat(jdbc.queryForObject("select identity_verified_at is null from manager_profiles where manager_id = ?", Boolean.class, manager.id())).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from profiles where purged_at is not null", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select purged_at is null from profiles where id = ?", Boolean.class, recent.id())).isTrue();

        assertThat(purger.purgeDue()).isZero(); // 다시 보지 않는다
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where payload::text like '%010-1234%'", Integer.class)).isZero(); // 아웃박스 원장에도 원문이 없다
    }
}
