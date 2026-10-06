package kr.ansimclean.api.booking;

import kr.ansimclean.api.MutableClock;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.profile.ProfileService;
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
import org.springframework.security.oauth2.jwt.Jwt;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 통합 (Docker 필요, B3 완료 기준): 생성 → 목록 upcoming/assigning → 변경 → 취소 → cancelled 탭 → 다시 취소는 같은 카드 · 시스템 취소된 예약의 고객 취소 409 ·
 * 기한 초과 409 · 무료 옵션 3개 400 · 주말 409 ·
 * 정기청소 생성 → 매월 1회 × 6회차 즉시 requested(첫 회차만 바로 매칭) → SeriesDispatcher → 7일 안 회차만 매칭 → 전체 취소는 기한 안 회차만.
 * 시계는 2026-09-15(화) 14:00 서울에 고정하고 필요할 때 옮긴다.
 */
// 통합 테스트의 기본 틀은 CODE_GUIDE.md 3-12. MockMvc 로 실제 포트 없이 컨트롤러에 HTTP 요청을 보낸다.
// 컨테이너가 static 이라 이 클래스의 테스트가 DB 하나를 같이 쓰고, 테스트 사이에 비우지 않는다.
// @ActiveProfiles("test") = application-test.yml 로 돈다(스케줄러가 꺼져 있어 dispatchDue 는 테스트가 직접 부른다).
// @Import(FixedClock) = 서버가 쓰는 AppClock 을 아래 CLOCK(옮길 수 있는 시계)으로 바꾼다.
// 테스트마다 새 고객을 만들어(signedInCustomer) 앞 테스트의 예약이 목록에 섞이지 않게 한다.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(BookingFlowIT.FixedClock.class)
class BookingFlowIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    // 05:00Z = 서울 14:00. CLOCK.set(…) 으로 옮기면 서버의 '지금'·'오늘' 이 같이 바뀐다 (스케줄러를 며칠 뒤로 돌려 볼 때)
    static final MutableClock CLOCK = new MutableClock(Instant.parse("2026-09-15T05:00:00Z"), AppClock.ZONE);

    /** 테스트에서만 쓰는 스프링 설정: @Primary 로 원래 AppClock 빈 대신 CLOCK 을 감싼 AppClock 을 쓰게 한다 */
    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {
        @Bean
        @Primary
        AppClock testClock() {
            return new AppClock(CLOCK);
        }
    }

    // @Autowired = 스프링이 만들어 둔 객체를 이 칸에 넣어 준다
    @Autowired
    MockMvc mvc;

    @Autowired
    ProfileService profiles;

    @Autowired
    BookingSeriesService series;

    @Autowired
    StaleBookingCleaner cleaner; // 매일 03:10 지난 예약 정리 — test 프로필은 스케줄러가 꺼져 있어 테스트가 clean() 을 직접 부른다

    // @MockitoBean = 스프링의 로그인 토큰(JWT) 검증기를 가짜로 바꾼다 — Supabase 없이, signedInCustomer 가 정한 토큰을 그 고객으로 통과시킨다
    @MockitoBean
    JwtDecoder jwtDecoder;

    final JsonMapper json = JsonMapper.builder().build(); // 응답 문자열을 JsonNode 로 읽는 도구
    UUID customer; // 이번 테스트의 고객 id
    String auth; // Authorization 헤더 값 ("Bearer t-<고객 id>")

    /** 테스트마다: 시계를 처음 시각으로 되돌리고, 가입을 마친 새 고객을 만들고, 그 고객으로 통과하는 가짜 토큰을 준비한다 */
    @BeforeEach
    void signedInCustomer() {
        CLOCK.set(Instant.parse("2026-09-15T05:00:00Z"));
        customer = UUID.randomUUID();
        // 살아 있는 계정끼리 번호가 겹치면 안 되므로(profiles_phone_live 유니크 인덱스) 고객 id 에서 8자리를 만든다
        profiles.createForSignup(customer, "customer", "김하늘", "하늘", "+8210" + String.format("%08d", Math.abs(customer.hashCode()) % 100000000));
        profiles.completeSignup(customer);
        String token = "t-" + customer;
        // 검증을 통과한 것처럼 보일 JWT 내용: subject = 고객 id, aud = authenticated, 1시간 유효
        Jwt jwt = Jwt.withTokenValue(token).header("alg", "HS256").subject(customer.toString()).claim("aud", List.of("authenticated"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
        when(jwtDecoder.decode(token)).thenReturn(jwt);
        auth = "Bearer " + token;
    }

    /** 예약 생성 요청 본문(JSON 문자열). 가정 청소·주소·요청사항은 고정이고 반복·날짜·시각·무료 옵션만 바꾼다. saveAddress=true 라 주소 목록에도 남는다 */
    String body(String frequency, String date, String time, List<String> free) {
        return "{\"category\":\"home\",\"frequency\":\"" + frequency + "\",\"date\":\"" + date + "\",\"time\":\"" + time + "\"," +
                "\"roadAddress\":\"서울특별시 강남구 테헤란로 123\",\"detailAddress\":\"3층\",\"freeOptions\":" + jsonList(free) +
                ",\"paidOptions\":[],\"notices\":[],\"request\":\"잘 부탁드려요\",\"saveAddress\":true}";
    }

    /** 문자열 목록 → JSON 배열 글자 (예: ["dishes","laundry"]) */
    static String jsonList(List<String> items) {
        return "[" + String.join(",", items.stream().map(s -> "\"" + s + "\"").toList()) + "]";
    }

    /** 내 예약 화면 응답(GET /api/v1/customer/reservations)의 reservations 배열 = 카드 목록 */
    JsonNode reservations() throws Exception {
        String res = mvc.perform(get("/api/v1/customer/reservations").header("Authorization", auth)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(res).get("reservations");
    }

    // 흐름: 견적 → 생성(멱등 키 포함) → 상세 → 기본 주소 → 목록 카드 → 일정 변경 → 취소 → 다시 취소(같은 카드 200).
    // 견적의 회당 금액을 먼저 받아 두고, 1회 예약이라 생성 응답의 totalAmount 가 그 값과 같아야 한다
    @Test
    void 일회_예약_생성_목록_변경_취소() throws Exception {
        String home = "{\"category\":\"home\",\"frequency\":\"oneTime\",\"date\":\"2026-09-17\",\"time\":\"10:00\",\"freeOptions\":[\"dishes\"],\"paidOptions\":[]}";
        String quote = mvc.perform(post("/api/v1/customer/bookings/quote").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(home))
                .andExpect(status().isOk()).andExpect(jsonPath("$.visitsPlanned").value(1)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long perVisit = json.readTree(quote).get("perVisit").get("total").asLong();

        String created = mvc.perform(post("/api/v1/customer/bookings").header("Authorization", auth).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body("oneTime", "2026-09-17", "10:00", List.of("dishes"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cleaner").value("매니저를 찾지 못했어요 · 가능한 매니저가 생기면 다시 찾아요")) // 이 테스트엔 매니저가 없어 바로 매칭 실패 — 목록과 같은 안내 (V041, 자체 점검 후속 검토)
                .andExpect(jsonPath("$.matchingFailed").value(true)) // 앱 수리 완료 화면이 '배정 중' 대신 위 안내를 보인다 (문제 목록 8번)
                .andExpect(jsonPath("$.totalAmount").value(perVisit))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String id = json.readTree(created).get("id").asString();

        mvc.perform(get("/api/v1/customer/bookings/" + id).header("Authorization", auth)).andExpect(status().isOk()).andExpect(jsonPath("$.serviceLabel").value("가정/원룸 청소"));
        // saveAddress → 주소 목록에 남고 첫 주소라 기본 주소
        mvc.perform(get("/api/v1/customer/addresses/default").header("Authorization", auth)).andExpect(status().isOk()).andExpect(jsonPath("$.roadAddress").value("서울특별시 강남구 테헤란로 123"));
        // 주소 관리에서 다른 주소를 기본으로 → 예약 "기존 주소" 가 바뀐다 (직접 사용 점검 U15 — 부분 유니크 인덱스를 실제 DB 에서)
        String office = mvc.perform(post("/api/v1/customer/addresses").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"회사\",\"roadAddress\":\"서울특별시 강남구 역삼로 1\",\"detailAddress\":\"5층\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isDefault").value(false)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String officeId = json.readTree(office).get("id").asString();
        mvc.perform(put("/api/v1/customer/addresses/" + officeId + "/default").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == '" + officeId + "')].isDefault").value(true));
        mvc.perform(get("/api/v1/customer/addresses/default").header("Authorization", auth)).andExpect(jsonPath("$.roadAddress").value("서울특별시 강남구 역삼로 1"));

        // 내 예약 목록: 방금 만든 한 장이 '예정' 탭에 매칭 중(assigning)으로 있고, 첫 행(일정)은 한국어 날짜
        JsonNode list = reservations();
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("tab").asString()).isEqualTo("upcoming");
        assertThat(list.get(0).get("status").asString()).isEqualTo("assigning");
        assertThat(list.get(0).get("rows").get(0).get("value").asString()).isEqualTo("2026년 9월 17일 (목) 오전 10시");

        // 일정 변경: 응답 카드의 일정 행이 새 날짜·시각으로 바뀐다
        mvc.perform(patch("/api/v1/customer/reservations/" + id + "/schedule").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-09-18\",\"time\":\"14:00\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows[0].value").value("2026년 9월 18일 (금) 오후 2시"));

        // 취소: 취소 탭으로 가고, 매니저 없이 취소됐으니 rows[2](매니저 행)는 '배정 전 취소'
        mvc.perform(post("/api/v1/customer/reservations/" + id + "/cancel").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tab").value("cancelled")).andExpect(jsonPath("$.rows[2].value").value("배정 전 취소"));
        // 취소한 예약을 다시 열어도(예약 완료 화면) 매니저 칸은 목록과 같은 '배정 전 취소' (문제 목록 9번)
        mvc.perform(get("/api/v1/customer/bookings/" + id).header("Authorization", auth)).andExpect(status().isOk()).andExpect(jsonPath("$.cleaner").value("배정 전 취소"))
                .andExpect(jsonPath("$.cancelled").value(true)); // 수리 완료 화면이 '배정 중' 대신 이 문구를 보인다 (합친 38건 검토 R16)
        assertThat(reservations().get(0).get("tab").asString()).isEqualTo("cancelled");
        // 다시 취소(응답이 늦어 다시 누른 경우): 고객이 이미 취소한 예약이면 오류 대신 같은 취소 카드(200)를 돌려준다 (자체 점검 #58, api.md).
        // 시스템이 취소한 예약은 409 — 아래 '시스템이_취소한_예약을…' 테스트
        mvc.perform(post("/api/v1/customer/reservations/" + id + "/cancel").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tab").value("cancelled")).andExpect(jsonPath("$.status").value("cancelled"))
                .andExpect(jsonPath("$.rows[2].value").value("배정 전 취소"));
        assertThat(reservations()).hasSize(1); // 다시 취소해도 카드가 늘거나 바뀌지 않는다
    }

    // 방문일이 지나도 매니저가 없어 시스템(StaleBookingCleaner, 매일 03:10)이 먼저 취소한 예약에 고객 취소를 보내면 409 INVALID_STATE.
    // 고객이 취소한 적이 없으니 위의 재시도(200)처럼 '취소됐어요' 로 답하지 않는다 (자체 점검 #58). 실제 DB 의 cancel_reason 으로 가른다
    @Test
    void 시스템이_취소한_예약을_고객이_취소하면_409() throws Exception {
        String created = mvc.perform(post("/api/v1/customer/bookings").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(body("oneTime", "2026-09-16", "10:00", List.of()))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String id = json.readTree(created).get("id").asString();
        // 9/17 03:10(서울): 방문일 9/16 이 지났는데 매니저가 없다(이 테스트엔 매니저가 없다) → cancelled(unassigned).
        // clean() 은 이 클래스의 다른 테스트가 남긴 지난 예약도 함께 정리하므로 돌려준 수는 보지 않는다
        CLOCK.set(Instant.parse("2026-09-16T18:10:00Z"));
        cleaner.clean();
        assertThat(reservations().get(0).get("tab").asString()).isEqualTo("cancelled");
        mvc.perform(post("/api/v1/customer/reservations/" + id + "/cancel").header("Authorization", auth))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
    }

    // 거절되는 요청들을 실제 HTTP 응답 코드·오류 코드로 확인한다: 기한이 지난 취소·변경 → 입력 검증 → 주말·시간대 → 없는 예약 id(남의 예약과 같은 404)
    @Test
    void 기한_초과_409_무료_옵션_3개_400_주말_409() throws Exception {
        // 내일 10:00 은 지금(14:00)부터 20시간 뒤 → 만들 수는 있지만 취소·변경은 안 된다
        String created = mvc.perform(post("/api/v1/customer/bookings").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(body("oneTime", "2026-09-16", "10:00", List.of()))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String id = json.readTree(created).get("id").asString();
        // 거절 문구의 시간은 설정(cancel_deadline_hours, 시드 24)에서 온다 (문제 목록 25번)
        mvc.perform(post("/api/v1/customer/reservations/" + id + "/cancel").header("Authorization", auth))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("BOOKING_NOT_CANCELLABLE"))
                .andExpect(jsonPath("$.error.message").value("방문 24시간 전까지만 취소할 수 있어요"));
        mvc.perform(patch("/api/v1/customer/reservations/" + id + "/schedule").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2026-09-18\",\"time\":\"10:00\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("BOOKING_NOT_CHANGEABLE"))
                .andExpect(jsonPath("$.error.message").value("방문 24시간 전까지만 변경할 수 있어요"));
        // 내 예약·홈 응답도 같은 기한 시간을 싣는다 — 앱의 잠긴 카드·취소 시트 문구가 이 값을 쓴다
        mvc.perform(get("/api/v1/customer/reservations").header("Authorization", auth)).andExpect(status().isOk()).andExpect(jsonPath("$.changeDeadlineHours").value(24));
        mvc.perform(get("/api/v1/customer/home").header("Authorization", auth)).andExpect(status().isOk()).andExpect(jsonPath("$.changeDeadlineHours").value(24));

        // 무료 옵션 3개 — 한도(app_settings.free_option_limit, 시드 값 2)를 넘어 400 VALIDATION
        mvc.perform(post("/api/v1/customer/bookings").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(body("oneTime", "2026-09-17", "10:00", List.of("dishes", "laundry", "trash"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION"));
        // 9/19 는 토요일 — 주말은 409 SLOT_UNAVAILABLE
        mvc.perform(post("/api/v1/customer/bookings").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(body("oneTime", "2026-09-19", "10:00", List.of())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SLOT_UNAVAILABLE"));
        // 10:30 은 운영 시간대(app_settings.time_slots)에 없는 시각 — 같은 409
        mvc.perform(post("/api/v1/customer/bookings").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(body("oneTime", "2026-09-17", "10:30", List.of())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SLOT_UNAVAILABLE"));
        // 남의 예약은 404
        mvc.perform(get("/api/v1/customer/bookings/" + UUID.randomUUID()).header("Authorization", auth)).andExpect(status().isNotFound());
    }

    // 흐름: 정기청소 생성 → 목록의 회차 카드 → 시계를 옮겨 가며 series.dispatchDue()(매일 09:00 SeriesDispatcher 가 부르는 것 — 2026-09-28 전 03:00, 문제 목록 2번)를 직접 부른다
    // → 한 회차에서 '전체 취소' → 기한 안 회차만 취소됐는지 → 취소된 시리즈에 남은 회차는 더 매칭하지 않는지.
    // 정기청소는 '매월 같은 날짜에 1회 × series_term_months 회'(V021 에서 6, 2026-09-22 사용자 결정)이고 주말이면 다음 평일로 민다(BookingSeries).
    // dispatchDue 는 이 클래스의 모든 고객 회차를 센다 — 정기청소를 만드는 테스트가 이것 하나뿐이라 수가 정확하다(DB 를 비우지 않는다)
    @Test
    void 정기청소_매월_6회차_스케줄러_매칭_전체_취소() throws Exception {
        // 9/18(금) 10:00 부터 매월: 9/18 · 10/19(10/18 이 일요일이라 다음 월요일) · 11/18 · 12/18 · 2027-01-18 · 2027-02-18
        String created = mvc.perform(post("/api/v1/customer/bookings").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(body("recurring", "2026-09-18", "10:00", List.of()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.seriesId").isNotEmpty())
                .andExpect(jsonPath("$.visits.length()").value(6))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode booking = json.readTree(created);
        assertThat(booking.get("visits").valueStream().map(v -> v.get("date").asString()).toList())
                .containsExactly("2026-09-18", "2026-10-19", "2026-11-18", "2026-12-18", "2027-01-18", "2027-02-18");
        String quote = mvc.perform(post("/api/v1/customer/bookings/quote").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"home\",\"frequency\":\"recurring\",\"date\":\"2026-09-18\",\"time\":\"10:00\"}"))
                .andExpect(jsonPath("$.visitsPlanned").value(6))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(booking.get("totalAmount").asLong()).isEqualTo(json.readTree(quote).get("totalAmount").asLong()); // 저장된 금액 = 앱이 본 견적 (회당 × 6)
        // 회차마다 카드 한 장. 첫 회차는 assigning(바로 매칭 — 이 테스트엔 매니저가 없어 매칭 실패로 남는다), 나머지는 requested 지만 카드 상태는 둘 다 "assigning"(BookingMapper.status)
        JsonNode list = reservations();
        assertThat(list).hasSize(6);
        assertThat(list).allMatch(r -> r.get("tab").asString().equals("upcoming") && r.get("series").asBoolean() && r.get("status").asString().equals("assigning"));

        // 9/15: 7일 안(≤ 9/22)에 requested 회차 없음 (9/18 은 이미 assigning)
        assertThat(series.dispatchDue()).isZero();
        // 10/11(일) 14:00: 두 번째 회차 10/19 는 8일 뒤라 아직 매칭하지 않는다 (dispatch_lead_days = 7)
        CLOCK.set(Instant.parse("2026-10-11T05:00:00Z"));
        assertThat(series.dispatchDue()).isZero();
        // 10/12(월) 14:00: 10/19 가 7일 안에 들어와 매칭을 시작한다
        CLOCK.set(Instant.parse("2026-10-12T05:00:00Z"));
        assertThat(series.dispatchDue()).isEqualTo(1);
        assertThat(series.dispatchDue()).isZero(); // 두 번 돌려도 같은 결과

        // 11/17(화) 13:00: 세 번째 회차 11/18 10:00 은 21시간 뒤 → 취소 기한(24시간)이 지나 남는다.
        // 11/11 이후 dispatchDue 를 부르지 않아 11/18 은 requested 그대로다(스케줄러가 며칠 돌지 못한 경우). 12/18 · 1/18 · 2/18 은 취소, 시리즈 cancelled.
        // 지난 9/18 · 10/19(매칭 실패로 assigning)도 기한이 지나 그대로 남는다
        CLOCK.set(Instant.parse("2026-11-17T04:00:00Z"));
        String target = booking.get("visits").get(3).get("id").asString(); // 네 번째 회차(12/18)에서 wholeSeries=true 로 취소한다
        mvc.perform(post("/api/v1/customer/reservations/" + target + "/cancel").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("{\"wholeSeries\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tab").value("cancelled"));
        JsonNode after = reservations();
        long cancelled = after.valueStream().filter(r -> r.get("tab").asString().equals("cancelled")).count(); // 취소 탭에 있는 카드 수
        assertThat(cancelled).isEqualTo(3);
        assertThat(after.valueStream().filter(r -> r.get("date").asString().equals("2026-11-18")).findFirst().orElseThrow().get("tab").asString()).isEqualTo("upcoming");
        // 취소된 시리즈에 남은 requested 회차(11/18)는 7일 안에 있어도 매칭하지 않는다 (BookingSeriesService.dispatchDue 의 isActive 검사)
        assertThat(series.dispatchDue()).isZero();
        // 취소 뒤에도 새 정기청소 견적(11/23 부터)을 받을 수 있고 회차 수가 설정(series_term_months = 6)과 같은지 본다. 탈퇴 가드는 여기서 부르지 않는다
        mvc.perform(post("/api/v1/customer/bookings/quote").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"home\",\"frequency\":\"recurring\",\"date\":\"2026-11-23\",\"time\":\"10:00\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.visitsPlanned").value(6));
    }
}
