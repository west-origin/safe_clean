package kr.ansimclean.api.managerhome;

import kr.ansimclean.api.ManagerMvc;
import kr.ansimclean.api.common.config.AppProperties;
import kr.ansimclean.api.common.config.CorsConfig;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.GlobalExceptionHandler;
import kr.ansimclean.api.common.security.CurrentUserResolver;
import kr.ansimclean.api.common.security.JwtToCurrentUserConverter;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.managerhome.dto.HomeResponse;
import kr.ansimclean.api.managerhome.dto.ScheduleResponse;
import kr.ansimclean.api.profile.ManagerProfileController;
import kr.ansimclean.api.profile.ProfileService;
import kr.ansimclean.api.profile.dto.ManagerProfileResponse;
import kr.ansimclean.api.schedule.AvailabilityController;
import kr.ansimclean.api.schedule.AvailabilityService;
import kr.ansimclean.api.schedule.dto.AvailabilityResponse;
import kr.ansimclean.api.settlement.SettlementController;
import kr.ansimclean.api.settlement.SettlementService;
import kr.ansimclean.api.settlement.dto.SettlementDetailResponse;
import kr.ansimclean.api.settlement.dto.SettlementResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 홈·일정·근무시간·프로필·정산 화면 API 의 파라미터·검증·JSON 키 (docs/api.md §5.3) */
// 컨트롤러 슬라이스(CODE_GUIDE.md 3-12). 아래 WebMvcTest 에 적은 컨트롤러들과 웹 계층만 뜬다.
// 보안 설정·오류 처리기는 Import 해서 진짜로 탄다 — 권한 검사와 { error } JSON 모양이 운영과 같다.
// 서비스는 가짜라, 정해 준 응답이 약속한 JSON 키로 나가는지와 요청 값 검사(400)만 본다.
// EnableConfigurationProperties = CorsConfig 가 받는 app.* 설정 객체(AppProperties)를 만든다.
// ActiveProfiles("test") = application-test.yml 설정으로 뜬다
@WebMvcTest({ ManagerHomeController.class, AvailabilityController.class, ManagerProfileController.class, SettlementController.class })
@Import({ SecurityConfig.class, JwtToCurrentUserConverter.class, CorsConfig.class, GlobalExceptionHandler.class })
@EnableConfigurationProperties(AppProperties.class)
@ActiveProfiles("test")
class ManagerScreensControllerTest {
    @Autowired
    MockMvc mvc; // 서버를 띄우지 않고 컨트롤러에 HTTP 요청을 흉내 내 보내는 도구

    // MockitoBean = 스프링 안의 그 객체를 가짜로 바꿔 넣는다. 아래 서비스들은 위 컨트롤러들이 생성자로 받는 것들이라
    // 하나라도 빠지면 컨트롤러를 만들지 못해 테스트가 뜨지 않는다
    @MockitoBean
    ManagerHomeService home;

    @MockitoBean
    AvailabilityService availability;

    @MockitoBean
    ProfileService profiles;

    @MockitoBean
    SettlementService settlements;

    @MockitoBean
    kr.ansimclean.api.profile.ManagerPenaltyService penalties; // ManagerProfileController 가 생성자로 받는다 (패널티 화면용)

    // 토큰 해석과 '사용자 id → 역할' 조회도 가짜 — ManagerMvc.manager 가 "Bearer m" 을 매니저로 풀리게 정해 준다
    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    CurrentUserResolver resolver;

    // 홈: 진행 중·새 콜 카드가 null 인 응답 → inProgress 는 null, remaining 은 배열로 나간다.
    // 일정: ?from&to 가 LocalDate 로 바뀌어 서비스에 간다 — 날짜가 다르면 when(…) 으로 정한 조건에 맞지 않아 week 검사가 실패한다
    @Test
    void 홈은_null_카드를_그대로_내리고_일정은_날짜_파라미터를_받는다() throws Exception {
        UUID id = ManagerMvc.manager(jwtDecoder, resolver, "m");
        // HomeResponse(manager, today, inProgress = null, newCalls = null, remaining = 빈 목록)
        when(home.home(id)).thenReturn(new HomeResponse(new HomeResponse.Manager("김매니저", true, null), new HomeResponse.Today("9월 15일 (화)", 0), null, null, List.of()));
        mvc.perform(get("/api/v1/manager/home").header("Authorization", "Bearer m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manager.name").value("김매니저"))
                .andExpect(jsonPath("$.inProgress").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.remaining").isArray());
        when(home.schedule(id, LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 21))).thenReturn(new ScheduleResponse(List.of(), List.of()));
        mvc.perform(get("/api/v1/manager/schedule").param("from", "2026-09-15").param("to", "2026-09-21").header("Authorization", "Bearer m"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.week").isArray());
    }

    // 근무시간 PUT: weekday 7 은 범위(0=월 … 6=일) 밖이라 400, 0 은 통과해 서비스 응답이 그대로 나간다.
    // 근무 불가 POST: repeat 는 none·weekly·monthly 만 받는다 — 화면 글자 '매주' 를 그대로 보내면 400 VALIDATION (앱이 바꿔 보낸다)
    @Test
    void 근무시간은_요일_범위와_반복_값을_검사한다() throws Exception {
        UUID id = ManagerMvc.manager(jwtDecoder, resolver, "m");
        mvc.perform(put("/api/v1/manager/availability/hours").header("Authorization", "Bearer m").contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"weekday\":7,\"start\":\"09:00\",\"end\":\"18:00\",\"off\":false}]"))
                .andExpect(status().isBadRequest());
        // eq(id) = 이 값과 같을 때, any() = 무엇이든 (Mockito 인자 조건)
        when(availability.updateHours(eq(id), any())).thenReturn(List.of(new AvailabilityResponse.DayHours("월", "09:00 - 18:00", false, "09:00", "18:00")));
        mvc.perform(put("/api/v1/manager/availability/hours").header("Authorization", "Bearer m").contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"weekday\":0,\"start\":\"09:00\",\"end\":\"18:00\",\"off\":false}]"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].day").value("월"));
        mvc.perform(post("/api/v1/manager/availability/unavailable").header("Authorization", "Bearer m").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-09-18\",\"allDay\":true,\"repeat\":\"매주\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION"));
    }

    // 프로필: menu 는 JSON 트리(JsonNode) 그대로 나간다. 모르는 하위 화면 key 는 404.
    // 정산 목록: before·limit 쿼리가 서비스로 간다. 정산 상세: finalRow 필드가 JSON 에서는 "final" 키이고 "finalRow" 키는 없다
    @Test
    void 프로필_메뉴는_JSON_그대로_정산_상세는_final_키() throws Exception {
        UUID id = ManagerMvc.manager(jwtDecoder, resolver, "m");
        // 메뉴는 문자열 JSON 을 readTree 로 읽어 JsonNode 로 만든다
        when(profiles.managerProfile(id)).thenReturn(new ManagerProfileResponse("김○○ 매니저", List.of(new ManagerProfileResponse.Stat("평점", "4.9")),
                JsonMapper.builder().build().readTree("[{\"key\":\"info\",\"label\":\"매니저 정보\"}]")));
        mvc.perform(get("/api/v1/manager/profile").header("Authorization", "Bearer m"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("김○○ 매니저")).andExpect(jsonPath("$.menu[0].key").value("info"));
        // thenThrow = 이 호출을 받으면 예외를 던진다 → GlobalExceptionHandler 가 404 응답으로 바꾼다
        when(profiles.managerProfileDetail(id, "nope")).thenThrow(ApiException.notFound());
        mvc.perform(get("/api/v1/manager/profile/nope").header("Authorization", "Bearer m")).andExpect(status().isNotFound());

        // 정산 목록: ?before&limit 이 서비스로 전달되고 nextBefore 가 응답에 있다
        when(settlements.screen(eq(id), eq("2026-09-16T00:00:00Z"), eq(2))).thenReturn(new SettlementResponse(new SettlementResponse.Monthly("이번 달 예상 정산", "0원"), List.of(), List.of(), "2026-09-15T05:00:00Z"));
        mvc.perform(get("/api/v1/manager/settlements?before=2026-09-16T00:00:00Z&limit=2").header("Authorization", "Bearer m"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nextBefore").value("2026-09-15T05:00:00Z"));

        // 정산 상세: 금액 줄 목록(breakdown) + 마지막 '최종 정산' 줄(finalRow)
        UUID sid = UUID.randomUUID();
        when(settlements.detail(id, sid)).thenReturn(new SettlementDetailResponse(sid, "9월 14일 · 강남구 기본청소", "10:00 - 13:00 · 김○○ 고객",
                List.of(new SettlementDetailResponse.Row("서비스 금액", "150,000원", "default")), new SettlementDetailResponse.Row("최종 정산", "120,000원", "brand")));
        mvc.perform(get("/api/v1/manager/settlements/" + sid).header("Authorization", "Bearer m"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.final.value").value("120,000원")).andExpect(jsonPath("$.finalRow").doesNotExist());
    }
}
