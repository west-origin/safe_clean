package kr.ansimclean.api.offer;

import kr.ansimclean.api.ItSupport;
import kr.ansimclean.api.MutableClock;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.job.JobChecklistItemRepository;
import kr.ansimclean.api.live.LiveSessionRepository;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 통합 (Docker 필요, B4 완료 기준 MatchingIT): 예약 → 후보 2명 오퍼 → A 수락 성공 · B 수락 OFFER_TAKEN → 같은 매니저의 겹치는 두 예약 동시 수락은 하나만 ·
 * 만료 스케줄러 → expired → 재매칭(새 후보) · 일정 변경 → accepted 가 superseded · 우선 오퍼 → 재수락 → live_sessions 1행 유지.
 * 시계는 2026-09-15(화) 14:00 서울에서 시작한다.
 */
// 통합 테스트(CODE_GUIDE.md 3-12). HTTP 포트는 열지 않고 MockMvc(AutoConfigureMockMvc)로 컨트롤러부터 DB 까지 실제 코드를 탄다.
// ServiceConnection = Testcontainers 가 띄운 Postgres 16 의 주소를 스프링 DB 설정으로 넣는다.
// 이 클래스가 바꿔 끼우는 것: 시계(MutableClock — 테스트가 옮긴다), JwtDecoder(가짜 — ItSupport 가 토큰을 사용자로 풀리게 정한다),
// 운영자 토큰(TestPropertySource 로 app.admin.token 설정). test 프로필에서는 스케줄러가 꺼져 있어(SchedulingConfig),
// 만료·자동 재매칭은 스케줄러 시각을 기다리지 않고 OfferService 를 직접 부른다.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(MatchingIT.FixedClock.class)
@org.springframework.test.context.TestPropertySource(properties = "app.admin.token=" + MatchingIT.ADMIN)
class MatchingIT {
    static final String ADMIN = "it-admin"; // 테스트 전용 운영자 토큰 값 — 위 설정과 X-Admin-Token 헤더에 같이 쓴다

    // Container + static = 이 클래스의 테스트 전체가 DB 컨테이너 하나를 같이 쓴다 (그래서 setUp 에서 데이터를 비운다)
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    // 2026-09-15 05:00 UTC = 서울 화요일 14:00. 서버의 AppClock 이 이 시계를 읽는다
    static final MutableClock CLOCK = new MutableClock(Instant.parse("2026-09-15T05:00:00Z"), AppClock.ZONE);

    // 서버의 AppClock 빈을 위 CLOCK 을 쓰는 것으로 바꿔 끼운다 (Primary = 같은 타입 빈이 둘이면 이쪽을 쓴다)
    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {
        @Bean
        @Primary
        AppClock testClock() {
            return new AppClock(CLOCK);
        }
    }

    // 진짜 빈들. ProfileService·AvailabilityService 는 준비 데이터(가입·승인)를 만들 때,
    // OfferService 는 스케줄러 대신 직접 부를 때(예: expireDue), 저장소(Repository)는 결과를 DB 에서 바로 확인할 때 쓴다
    @Autowired
    MockMvc mvc;
    @Autowired
    ProfileService profiles;
    @Autowired
    AvailabilityService availability;
    @Autowired
    OfferService offerService;
    @Autowired
    OfferRepository offers;
    @Autowired
    LiveSessionRepository liveSessions;
    @Autowired
    JobChecklistItemRepository checklist;
    @MockitoBean
    JwtDecoder jwtDecoder;

    final JsonMapper json = JsonMapper.builder().build(); // 응답 본문(JSON 문자열)을 JsonNode 로 읽는다
    ItSupport.User customer;
    ItSupport.User a; // 매니저 김미영
    ItSupport.User b; // 매니저 박서준

    // JdbcTemplate = SQL 을 직접 실행하는 도구. 데이터 비우기, '매칭 실패' 상태를 억지로 만들기, 실패 표시가 지워졌는지 확인에 쓴다
    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    /** 테스트마다: 사용자 데이터를 비우고, 시계를 처음 시각으로 되돌리고, 고객 1명과 강남구 승인 매니저 2명(a·b)을 만든다 */
    @BeforeEach
    void setUp() {
        ItSupport.resetUserData(jdbc); // 클래스당 컨테이너 하나 — 앞 테스트의 매니저가 후보로 남으면 오퍼 수·pending 수 단언이 순서에 따라 깨진다
        CLOCK.set(Instant.parse("2026-09-15T05:00:00Z"));
        customer = ItSupport.signedUp(profiles, jwtDecoder, "customer", "김하늘");
        a = ItSupport.approvedManager(profiles, availability, jwtDecoder, "김미영", "서울특별시 강남구", "서울특별시");
        b = ItSupport.approvedManager(profiles, availability, jwtDecoder, "박서준", "서울특별시 강남구", "서울특별시");
    }

    /** 고객이 예약을 만든다 (POST /customer/bookings, 가정 청소 1회). 만들 때 매칭이 돌아 후보들에게 콜이 간다. 결과 = 새 예약 id */
    UUID createBooking(String date, String time) throws Exception {
        String res = mvc.perform(post("/api/v1/customer/bookings").header("Authorization", customer.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(ItSupport.bookingBody(date, time))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return UUID.fromString(json.readTree(res).get("id").asString());
    }

    /** 매니저 콜 탭(GET /manager/offers) 응답의 calls 배열 = 그 매니저에게 살아 있는 콜 카드들 */
    JsonNode calls(ItSupport.User manager) throws Exception {
        String res = mvc.perform(get("/api/v1/manager/offers").header("Authorization", manager.bearer())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(res).get("calls");
    }

    /** DB 에서 이 매니저가 받은 이 예약의 대기(pending) 콜 id 를 찾는다. 없으면 orElseThrow 가 예외를 던져 테스트가 실패한다 */
    UUID offerFor(ItSupport.User manager, UUID bookingId) {
        return offers.findByBookingId(bookingId).stream().filter(o -> o.getManagerId().equals(manager.id()) && o.isPending()).findFirst().orElseThrow().getId();
    }

    /** 콜 수락을 보내고 HTTP 상태 코드만 돌려준다 (200 성공, 409 경합 등). Idempotency-Key 는 부를 때마다 새 UUID — 재시도가 아닌 새 요청이다 */
    int accept(ItSupport.User manager, UUID offerId) throws Exception {
        return mvc.perform(post("/api/v1/manager/offers/" + offerId + "/accept").header("Authorization", manager.bearer()).header("Idempotency-Key", UUID.randomUUID().toString()))
                .andReturn().getResponse().getStatus();
    }

    // 예약 하나 → a·b 둘 다 콜을 받는다(카드 문구 확인) → a 수락 200(jobId = 예약 id), b 수락 409 OFFER_TAKEN →
    // b 의 콜 목록은 비고, 작업 상세는 a 만 볼 수 있으며(b 는 404), 라이브 세션 행·체크리스트 9개가 생기고 고객 예약 카드에 a 이름이 보인다
    @Test
    void 두_후보_중_한_명만_수락하고_나머지는_OFFER_TAKEN() throws Exception {
        UUID bookingId = createBooking("2026-09-17", "10:00");
        assertThat(calls(a)).hasSize(1);
        assertThat(calls(b)).hasSize(1);
        assertThat(calls(a).get(0).get("title").asString()).isEqualTo("9월 17일 목요일 오전 10:00");
        assertThat(calls(a).get(0).get("pay").asString()).isEqualTo("90,000원"); // 100,000 − 10%
        assertThat(calls(a).get(0).get("note").asString()).isEqualTo("특이사항: 반려동물이 있어요");

        UUID offerA = offerFor(a, bookingId);
        UUID offerB = offerFor(b, bookingId);
        mvc.perform(post("/api/v1/manager/offers/" + offerA + "/accept").header("Authorization", a.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.jobId").value(bookingId.toString()));
        mvc.perform(post("/api/v1/manager/offers/" + offerB + "/accept").header("Authorization", b.bearer()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("OFFER_TAKEN"));
        assertThat(calls(b)).isEmpty();

        mvc.perform(get("/api/v1/manager/jobs/" + bookingId).header("Authorization", a.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("scheduled")).andExpect(jsonPath("$.pay").value("90,000원"));
        mvc.perform(get("/api/v1/manager/jobs/" + bookingId).header("Authorization", b.bearer())).andExpect(status().isNotFound());
        assertThat(liveSessions.findById(bookingId)).isPresent();
        assertThat(checklist.findByBookingIdOrderBySort(bookingId)).hasSize(9);
        mvc.perform(get("/api/v1/customer/reservations").header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$.reservations[0].status").value("scheduled")).andExpect(jsonPath("$.reservations[0].rows[2].value").value("김미영 매니저"));
    }

    // a 수락 뒤: b 가 닫힌 자기 콜을 거절하면 409 OFFER_TAKEN(앱의 마감 시트), a 가 수락한 콜을 거절하면 409 INVALID_STATE.
    // 다른 예약에서 b 가 거절한 뒤 같은 콜을 다시 거절하면 200 + 처음 저장한 사유 (문제 목록 10번)
    @Test
    void 닫힌_콜의_거절은_닫힌_이유대로_409_이미_거절한_콜은_200() throws Exception {
        UUID x = createBooking("2026-09-17", "10:00");
        UUID offerA = offerFor(a, x);
        UUID offerB = offerFor(b, x);
        assertThat(accept(a, offerA)).isEqualTo(200);
        mvc.perform(post("/api/v1/manager/offers/" + offerB + "/reject").header("Authorization", b.bearer()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"개인 사정\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("OFFER_TAKEN"));
        mvc.perform(post("/api/v1/manager/offers/" + offerA + "/reject").header("Authorization", a.bearer()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"개인 사정\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INVALID_STATE"));

        UUID y = createBooking("2026-09-18", "10:00");
        UUID offerY = offerFor(b, y);
        mvc.perform(post("/api/v1/manager/offers/" + offerY + "/reject").header("Authorization", b.bearer()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"개인 사정\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/manager/offers/" + offerY + "/reject").header("Authorization", b.bearer()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"시간이 맞지 않아요\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reason").value("개인 사정"));
    }

    // 서울 화 22:00 에 모레(9/17) 10:00 예약 → 콜 없이 보류 표시만(고객 카드는 배정 전 문구). 같은 밤 내일(9/16) 10:00 예약은
    // 미루면 약속까지 3시간이 안 남아 바로 간다. 아침 08:05 보내기(MatchingRetry 가 부르는 메서드)를 직접 부르면 미룬 예약이 a·b 에게 간다
    @Test
    void 밤에_만든_예약의_콜은_아침에_가고_급한_예약은_바로_간다() throws Exception { // 문제 목록 2번 (사용자 결정 ③)
        CLOCK.set(Instant.parse("2026-09-15T13:00:00Z")); // 서울 22:00
        UUID later = createBooking("2026-09-17", "10:00");
        assertThat(offers.findByBookingId(later)).isEmpty();
        assertThat(jdbc.queryForObject("select dispatch_held_at is not null from bookings where id = ?", Boolean.class, later)).isTrue();
        mvc.perform(get("/api/v1/customer/reservations").header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$.reservations[0].rows[2].value").value("배정 후 알려드려요"));
        UUID soon = createBooking("2026-09-16", "10:00");
        assertThat(offers.findByBookingId(soon)).extracting(Offer::getManagerId).containsExactlyInAnyOrder(a.id(), b.id());

        CLOCK.set(Instant.parse("2026-09-15T23:05:00Z")); // 서울 9/16 08:05
        assertThat(offerService.releaseHeldDispatches()).isEqualTo(1);
        assertThat(offers.findByBookingId(later)).extracting(Offer::getManagerId).containsExactlyInAnyOrder(a.id(), b.id());
        assertThat(jdbc.queryForObject("select dispatch_held_at is null from bookings where id = ?", Boolean.class, later)).isTrue();
        assertThat(offerService.releaseHeldDispatches()).isZero(); // 두 번 보내지 않는다
    }

    // a 가 수락한 뒤 b 가 정지돼(SQL) 다른 후보가 없다 → a 가 방문을 취소하면 재매칭이 곧바로 실패한다.
    // 고객 알림은 매칭 실패(MatchingFailed) 하나만 적히고 '다른 매니저를 찾고 있어요'(ManagerReleased)는 적히지 않는다 (문제 목록 5번)
    @Test
    void 방문_취소_뒤_대신할_매니저가_없으면_실패_알림_하나만_간다() throws Exception {
        UUID x = createBooking("2026-09-18", "10:00");
        assertThat(accept(a, offerFor(a, x))).isEqualTo(200);
        jdbc.update("update manager_profiles set suspended_until = ? where manager_id = ?", java.sql.Date.valueOf("2026-09-22"), b.id());
        mvc.perform(post("/api/v1/manager/jobs/" + x + "/cancel").header("Authorization", a.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"sick\"}")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select matching_failed_at is not null from bookings where id = ?", Boolean.class, x)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where type = 'MatchingFailed' and payload::text like ?", Integer.class, "%" + x + "%")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where type = 'ManagerReleased' and payload::text like ?", Integer.class, "%" + x + "%")).isZero();
    }

    // a·b 가 콜을 받은 뒤 a 가 7일 정지된다(SQL) → a 의 콜 탭은 곧바로 카드 없이 '콜 배정 정지 중' 안내(배지 0).
    // 만료 스케줄러의 단계(closeBlockedOffers)가 a 의 콜을 닫고, b 는 아직 대기 중이라 그 회차는 그대로다. 이어 b 가 거절하면 회차가 닫혀 바로 다음 회차로 간다
    @Test
    void 정지된_매니저의_대기_콜은_닫히고_콜_탭에는_정지_안내만_보인다() throws Exception { // 문제 목록 6번
        UUID x = createBooking("2026-09-17", "10:00");
        UUID offerA = offerFor(a, x);
        UUID offerB = offerFor(b, x);
        jdbc.update("update manager_profiles set suspended_until = ? where manager_id = ?", java.sql.Date.valueOf("2026-09-22"), a.id());
        mvc.perform(get("/api/v1/manager/offers").header("Authorization", a.bearer()))
                .andExpect(jsonPath("$.calls.length()").value(0)).andExpect(jsonPath("$.tabBadge").value(0))
                .andExpect(jsonPath("$.availability.status").value("콜 배정 정지 중"));

        assertThat(offerService.closeBlockedOffers()).isEqualTo(1);
        assertThat(offers.findById(offerA).orElseThrow().getStatus()).isEqualTo(Offer.CANCELLED);
        assertThat(calls(b)).hasSize(1);
        mvc.perform(post("/api/v1/manager/offers/" + offerB + "/reject").header("Authorization", b.bearer()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"개인 사정\"}"))
                .andExpect(status().isOk());
        // a 의 콜이 '대기' 로 남아 있었다면 여기서 회차가 닫히지 않았다 — 이제는 2회차로 넘어간다(a·b 모두 이미 받아 후보가 없으니 매칭 실패 표시)
        assertThat(jdbc.queryForObject("select dispatch_round from bookings where id = ?", Integer.class, x)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select matching_failed_at is not null from bookings where id = ?", Boolean.class, x)).isTrue();
    }

    // a·b 가 콜을 받은 뒤 a 가 그날 하루 휴무(근무 불가)를 넣는다 → a 의 수락은 409 INVALID_STATE(근무 일정 안내), 예약은 매칭 중 그대로라 b 가 수락할 수 있다
    @Test
    void 콜을_받은_뒤_그날_휴무를_넣은_매니저는_수락할_수_없다() throws Exception { // 문제 목록 7번 (사용자 결정 ①)
        UUID x = createBooking("2026-09-17", "10:00");
        UUID offerA = offerFor(a, x);
        availability.addUnavailable(a.id(), new kr.ansimclean.api.schedule.dto.UnavailableRequest("2026-09-17", true, null, null, "none", null));
        mvc.perform(post("/api/v1/manager/offers/" + offerA + "/accept").header("Authorization", a.bearer()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INVALID_STATE"))
                .andExpect(jsonPath("$.error.message").value("근무 일정과 맞지 않는 시간이라 수락할 수 없어요. 근무 일정 관리에서 확인해주세요"));
        assertThat(accept(b, offerFor(b, x))).isEqualTo(200);
    }

    // 같은 매니저 a 에게 온 겹치는 두 콜을 스레드 두 개로 동시에 수락 → 200 하나, 409 하나.
    // 서비스는 매니저 행을 먼저 잠가(lockManager) 두 수락이 줄을 서고, 뒤에 온 쪽은 겹치는 작업 검사에 걸려 OFFER_TAKEN 이 되며
    // 그 수락의 트랜잭션 전체가 되돌려진다. 이긴 수락은 **같은 예약**의 다른 대기 콜(b)만 닫는다(domain.md §6.2-3) —
    // 그래서 이긴 예약은 대기 콜 0건·scheduled, 진 예약은 a·b 의 콜 2건이 대기로 남은 assigning 이고, b 가 진 예약을 수락할 수 있다.
    // 어느 쪽이 이길지는 스레드 순서에 달려 있어 응답 코드로 이긴 쪽을 가린다
    @Test
    void 같은_매니저가_겹치는_두_예약을_동시에_수락하면_하나만_성공한다() throws Exception {
        UUID x = createBooking("2026-09-18", "10:00");
        UUID y = createBooking("2026-09-18", "11:00"); // 10–13 과 겹친다
        UUID offerX = offerFor(a, x);
        UUID offerY = offerFor(a, y);
        // ExecutorService = 작업을 별도 스레드에서 돌리는 도구. submit 은 바로 돌아오고, Future.get() 이 그 결과를 기다려 받는다
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Integer>> results = new ArrayList<>();
        for (UUID offerId : List.of(offerX, offerY)) {
            Callable<Integer> task = () -> accept(a, offerId);
            results.add(pool.submit(task));
        }
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) statuses.add(f.get());
        pool.shutdown();
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        UUID won = statuses.get(0).equals(200) ? x : y; // statuses 는 offerX, offerY 를 보낸 순서
        UUID lost = won.equals(x) ? y : x;
        long pendingCount = offers.findByBookingId(x).stream().filter(Offer::isPending).count() + offers.findByBookingId(y).stream().filter(Offer::isPending).count();
        assertThat(pendingCount).isEqualTo(2); // 진 예약의 a·b 콜
        // 이긴 예약: a 에게 배정됐고 b 의 콜은 닫혔다
        assertThat(offers.findByBookingId(won).stream().filter(Offer::isPending)).isEmpty();
        assertThat(bookingStatus(won)).isEqualTo("scheduled");
        // 진 예약: a 의 수락이 통째로 되돌려져 a·b 콜이 그대로 대기 중이고 예약은 여전히 매칭 중
        assertThat(offers.findByBookingId(lost).stream().filter(Offer::isPending).map(Offer::getManagerId)).containsExactlyInAnyOrder(a.id(), b.id());
        assertThat(bookingStatus(lost)).isEqualTo("assigning");
        // 다른 매니저 b 는 진 예약을 수락할 수 있고, 그러면 남아 있던 a 의 콜도 닫힌다
        assertThat(accept(b, offerFor(b, lost))).isEqualTo(200);
        assertThat(bookingStatus(lost)).isEqualTo("scheduled");
        assertThat(offers.findByBookingId(lost).stream().filter(Offer::isPending)).isEmpty();
    }

    /** DB 의 예약 상태 값 (bookings.status — "assigning"·"scheduled" …) */
    String bookingStatus(UUID bookingId) {
        return jdbc.queryForObject("select status from bookings where id = ?", String.class, bookingId);
    }

    // a 가 수락한 뒤 방문을 취소(POST /manager/jobs/{id}/cancel) → 새 회차로 다시 매칭된다. a 는 빠지고 b 가 다시 받아 수락한다
    @Test
    void 매니저가_방문을_취소하면_처음_콜을_받았던_다른_매니저가_다시_받아_수락할_수_있다() throws Exception { // 전체 재점검 O1 (offers_one_accepted)
        UUID x = createBooking("2026-09-18", "10:00");
        assertThat(accept(a, offerFor(a, x))).isEqualTo(200);
        mvc.perform(post("/api/v1/manager/jobs/" + x + "/cancel").header("Authorization", a.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"sick\"}"))
                .andExpect(status().isOk());
        assertThat(calls(a)).isEmpty();          // 취소한 매니저에게는 다시 가지 않는다
        assertThat(calls(b)).hasSize(1);          // 처음 콜을 받았던 매니저에게 새 회차로
        assertThat(accept(b, offerFor(b, x))).isEqualTo(200); // 예전에는 a 의 accepted 가 남아 409
        mvc.perform(get("/api/v1/manager/jobs/" + x).header("Authorization", b.bearer())).andExpect(status().isOk());
    }

    // a·b 에게 콜이 간 뒤 새 매니저 c 가 승인된다 → 콜 유효 시간(설정 30분)을 넘기도록 시계를 31분 옮기고 만료 처리를 직접 부른다 →
    // a·b 콜 2건이 expired, 2회차 콜은 아직 받지 않은 c 에게만 간다
    @Test
    void 만료되면_expired_가_되고_새_후보에게_다음_회차로_재매칭한다() throws Exception {
        UUID z = createBooking("2026-09-21", "10:00");
        assertThat(offers.findByBookingId(z)).hasSize(2);
        ItSupport.User c = ItSupport.approvedManager(profiles, availability, jwtDecoder, "이수현", "서울특별시 강남구", "서울특별시");
        CLOCK.set(CLOCK.instant().plus(Duration.ofMinutes(31)));
        assertThat(offerService.expireDue()).isEqualTo(2); // 스케줄러(OfferExpirer)가 부르는 메서드를 직접 부른다. 결과 = 만료시킨 콜 수
        List<Offer> all = offers.findByBookingId(z);
        assertThat(all.stream().filter(o -> Offer.EXPIRED.equals(o.getStatus())).count()).isEqualTo(2);
        Offer next = all.stream().filter(Offer::isPending).findFirst().orElseThrow();
        assertThat(next.getManagerId()).isEqualTo(c.id());
        assertThat(next.getRound()).isEqualTo(2);
        assertThat(calls(c)).hasSize(1);
        assertThat(calls(a)).isEmpty();
        assertThat(offerService.expireDue()).isZero(); // 두 번 돌려도 같다
    }

    // a 수락 → 고객이 일정을 9/22 14:00 으로 변경 → a 의 수락 콜은 superseded, 예약은 다시 매칭 중, 체크리스트는 비워진다.
    // a 에게 2회차 우선 콜(b 는 받지 않음) → a 가 다시 수락해도 라이브 세션 행은 하나뿐이고 체크리스트 9개가 다시 생기며, 일정 화면에 새 시간이 보인다
    @Test
    void 일정_변경은_accepted_를_superseded_로_하고_이전_매니저에게_우선_오퍼_재수락해도_라이브_세션은_한_행() throws Exception {
        UUID x = createBooking("2026-09-18", "10:00");
        assertThat(accept(a, offerFor(a, x))).isEqualTo(200);
        UUID firstOffer = offers.findByBookingId(x).stream().filter(o -> Offer.ACCEPTED.equals(o.getStatus())).findFirst().orElseThrow().getId();

        mvc.perform(patch("/api/v1/customer/reservations/" + x + "/schedule").header("Authorization", customer.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-09-22\",\"time\":\"14:00\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("assigning"));
        assertThat(offers.findById(firstOffer).orElseThrow().getStatus()).isEqualTo(Offer.SUPERSEDED);
        Offer preferred = offers.findByBookingId(x).stream().filter(Offer::isPending).findFirst().orElseThrow();
        assertThat(preferred.getManagerId()).isEqualTo(a.id());
        assertThat(preferred.isPreferred()).isTrue();
        assertThat(preferred.getRound()).isEqualTo(2);
        assertThat(calls(b)).isEmpty();
        assertThat(calls(a).get(0).get("title").asString()).isEqualTo("9월 22일 화요일 오후 2:00");
        assertThat(checklist.findByBookingIdOrderBySort(x)).isEmpty();

        assertThat(accept(a, preferred.getId())).isEqualTo(200);
        assertThat(liveSessions.findById(x)).isPresent();
        assertThat(liveSessions.count()).isEqualTo(1);
        assertThat(checklist.findByBookingIdOrderBySort(x)).hasSize(9);
        mvc.perform(get("/api/v1/manager/schedule").param("from", "2026-09-22").param("to", "2026-09-22").header("Authorization", a.bearer()))
                .andExpect(jsonPath("$.week[0].items[0].time").value("14:00 - 17:00"));
    }

    // 일정 변경으로 a 에게 간 우선 콜을 a 가 거절 → 회차는 그대로(2) 두고 b 에게 일반 콜이 간다
    @Test
    void 우선_오퍼가_거절되면_같은_회차에서_다른_후보에게_간다() throws Exception {
        UUID x = createBooking("2026-09-18", "10:00");
        assertThat(accept(a, offerFor(a, x))).isEqualTo(200);
        mvc.perform(patch("/api/v1/customer/reservations/" + x + "/schedule").header("Authorization", customer.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2026-09-22\",\"time\":\"14:00\"}")).andExpect(status().isOk());
        UUID preferred = offerFor(a, x);
        mvc.perform(post("/api/v1/manager/offers/" + preferred + "/reject").header("Authorization", a.bearer()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"시간이 맞지 않아요\"}"))
                .andExpect(status().isOk());
        Offer fallback = offers.findByBookingId(x).stream().filter(Offer::isPending).findFirst().orElseThrow();
        assertThat(fallback.getManagerId()).isEqualTo(b.id());
        assertThat(fallback.getRound()).isEqualTo(2);
        assertThat(fallback.isPreferred()).isFalse();
    }

    // SQL 로 '3회차까지 다 쓰고 실패' 상태를 만든 뒤 운영자 재매칭: 토큰 헤더가 없으면 401, 있으면 200 + a·b 두 명에게
    // 4회차 콜, 예약의 실패 표시(matching_failed_at)는 지워진다
    @Test
    void 매칭_실패로_멈춘_예약은_운영자가_다시_매칭하면_새_회차로_후보에게_간다() throws Exception { // 자체 점검 #5
        UUID z = createBooking("2026-09-21", "10:00");
        jdbc.update("update bookings set matching_failed_at = now(), dispatch_round = 3 where id = ?", z); // 회차를 다 썼다고 치고
        jdbc.update("update offers set status = 'expired' where booking_id = ?", z);
        mvc.perform(post("/api/v1/admin/bookings/" + z + "/redispatch")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/bookings/" + z + "/redispatch").header("X-Admin-Token", ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.offered").value(2));
        assertThat(offers.findByBookingId(z).stream().filter(Offer::isPending).map(Offer::getRound)).containsOnly(4);
        assertThat(jdbc.queryForObject("select matching_failed_at is null from bookings where id = ?", Boolean.class, z)).isTrue();
    }

    // 9/21(월)·9/22(화) 는 두 매니저의 기본 근무일이다. 17:00·18:00 에 시작하는 3시간 예약 모두 a·b 가 콜을 받는지 본다
    @Test
    void 근무_종료를_넘겨_끝나는_예약도_시작이_근무시간_안이면_기본_근무시간_매니저에게_콜이_간다() throws Exception { // 자체 점검 #16 후속 (사용자 결정 2026-09-27)
        UUID late = createBooking("2026-09-21", "17:00"); // 가정 3시간 → 20:00 에 끝난다, 두 매니저 모두 09~18시
        assertThat(offers.findByBookingId(late)).extracting(Offer::getManagerId).containsExactlyInAnyOrder(a.id(), b.id());
        UUID last = createBooking("2026-09-22", "18:00"); // 근무 종료 시각에 시작하는 마지막 슬롯도 (21:00 에 끝난다)
        assertThat(offers.findByBookingId(last)).extracting(Offer::getManagerId).containsExactlyInAnyOrder(a.id(), b.id());
    }

    // SQL 로 매칭 실패 상태를 만든 뒤 자동 재매칭(스케줄러가 부르는 메서드)을 직접 부른다:
    // 새 후보가 없으면 0 → c 가 승인되면 c 에게만 보내 1 → 실패 표시가 지워져 다시 불러도 0
    @Test
    void 매칭_실패_뒤_새로_승인된_매니저가_생기면_자동_재매칭이_그_매니저에게만_보낸다() throws Exception { // 사용자 결정 2026-09-27 (MatchingRetry)
        UUID z = createBooking("2026-09-21", "10:00");
        jdbc.update("update bookings set matching_failed_at = now(), dispatch_round = 3 where id = ?", z); // 회차를 다 썼다고 치고
        jdbc.update("update offers set status = 'expired' where booking_id = ?", z);
        assertThat(offerService.retryFailedMatches()).isZero(); // a·b 는 이미 받았다 — 새 후보 없음
        ItSupport.User c = ItSupport.approvedManager(profiles, availability, jwtDecoder, "이수현", "서울특별시 강남구", "서울특별시");
        assertThat(offerService.retryFailedMatches()).isEqualTo(1);
        assertThat(offers.findByBookingId(z).stream().filter(Offer::isPending).map(Offer::getManagerId)).containsExactly(c.id());
        assertThat(jdbc.queryForObject("select matching_failed_at is null from bookings where id = ?", Boolean.class, z)).isTrue();
        assertThat(offerService.retryFailedMatches()).isZero(); // 더 이상 실패 상태가 아니다
    }
}
