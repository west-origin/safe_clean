package kr.ansimclean.api.customer;

import kr.ansimclean.api.ManagerMvc;
import kr.ansimclean.api.common.config.CorsConfig;
import kr.ansimclean.api.common.error.GlobalExceptionHandler;
import kr.ansimclean.api.common.security.CurrentUserResolver;
import kr.ansimclean.api.common.security.JwtToCurrentUserConverter;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.customer.dto.HomeResponse;
import kr.ansimclean.api.customer.dto.RepairResponse;
import kr.ansimclean.api.customer.dto.SettingsResponse;
import kr.ansimclean.api.live.DeviceController;
import kr.ansimclean.api.live.LiveSessionService;
import kr.ansimclean.api.live.dto.DeviceResponse;
import kr.ansimclean.api.live.dto.DeviceStatusRow;
import kr.ansimclean.api.payment.PaymentMethodController;
import kr.ansimclean.api.payment.PaymentService;
import kr.ansimclean.api.payment.dto.PaymentMethodResponse;
import kr.ansimclean.api.profile.dto.SettingsProfile;
import kr.ansimclean.api.support.TermsController;
import kr.ansimclean.api.support.TermsService;
import kr.ansimclean.api.support.dto.ManagerTermsResponse;
import kr.ansimclean.api.support.dto.TermResponse;
import kr.ansimclean.api.support.dto.TermSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 슬라이스만 없던 읽기 API 들 — JSON 모양·역할 권한 (docs/api.md): /customer/home·repair·settings, /customer/payment-methods, /manager/device, /terms */
// 컨트롤러 슬라이스(CODE_GUIDE.md 3-12). 화면별 읽기 컨트롤러 여럿을 한 슬라이스에 묶었다.
// @Import 로 실제 보안 설정(SecurityConfig 등)과 오류 JSON 변환(GlobalExceptionHandler)을 넣어 경로별 역할 검사(401·403)까지 같이 본다.
// 서비스는 가짜라 서비스 규칙은 보지 않는다 — 단, static 인 TermsService.requireAudience 는 실제 코드가 돈다(약관 테스트).
@WebMvcTest({ CustomerScreenController.class, PaymentMethodController.class, DeviceController.class, TermsController.class })
@Import({ SecurityConfig.class, JwtToCurrentUserConverter.class, CorsConfig.class, GlobalExceptionHandler.class })
class CustomerScreenControllerTest {
    // MockMvc = 실제 네트워크 없이 HTTP 요청을 흉내 내 컨트롤러에 보내는 도구
    @Autowired
    MockMvc mvc;
    @MockitoBean
    CustomerScreenService screens;
    @MockitoBean
    PaymentService payments;
    @MockitoBean
    LiveSessionService live;
    @MockitoBean
    TermsService terms;
    // JWT 해석기와 사용자 조회를 가짜로 둔다 — ManagerMvc 가 토큰 문자열("c", "m")을 고객·매니저 로그인으로 꾸민다
    @MockitoBean
    JwtDecoder jwtDecoder;
    @MockitoBean
    CurrentUserResolver resolver;

    final JsonMapper json = JsonMapper.builder().build(); // 가짜 응답에 넣을 JSON 조각(슬라이드·메뉴)을 문자열에서 만든다

    // 준비: "Bearer c" = 고객, "Bearer m" = 매니저. 고객으로 세 탭 API 를 불러 JSON 칸을 확인하고
    // → 매니저 토큰은 403(FORBIDDEN), 토큰이 없으면 401 인지 본다
    @Test
    void 고객_화면_값은_고객만_보고_모양은_mock_과_같다() throws Exception {
        UUID customer = ManagerMvc.customer(jwtDecoder, resolver, "c");
        ManagerMvc.manager(jwtDecoder, resolver, "m");
        when(screens.home(customer)).thenReturn(new HomeResponse(json.readTree("[{\"id\":\"s1\"}]"),
                new kr.ansimclean.api.booking.dto.ReservationResponse(UUID.randomUUID(), "upcoming", "home", "가정/원룸 청소", "scheduled", "2026-09-24", "11:00", false, false, true,
                        java.util.List.of(new kr.ansimclean.api.booking.dto.ReservationResponse.Row("일정", "2026년 9월 24일 (목) 오전 11시"))), 24));
        when(screens.repair()).thenReturn(new RepairResponse(json.readTree("{\"title\":\"수리\"}")));
        when(screens.settings(customer)).thenReturn(new SettingsResponse(json.readTree("{\"title\":\"설정\"}"),
                new SettingsProfile("김하늘", "010-1234-5678", null), json.readTree("[]"), Map.of("push", true, "marketing", false)));

        mvc.perform(get("/api/v1/customer/home").header("Authorization", "Bearer c")).andExpect(status().isOk()).andExpect(jsonPath("$.slides[0].id").value("s1"))
                .andExpect(jsonPath("$.upcoming.title").value("가정/원룸 청소")) // 홈이 다가오는 방문을 보여 준다 (사용성 검토)
                .andExpect(jsonPath("$.changeDeadlineHours").value(24)); // 잠긴 카드 안내의 기한 시간 (문제 목록 25번)
        mvc.perform(get("/api/v1/customer/repair").header("Authorization", "Bearer c")).andExpect(status().isOk()).andExpect(jsonPath("$.hero.title").value("수리"));
        mvc.perform(get("/api/v1/customer/settings").header("Authorization", "Bearer c")).andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.name").value("김하늘")).andExpect(jsonPath("$.notifications.marketing").value(false));
        mvc.perform(get("/api/v1/customer/home").header("Authorization", "Bearer m")).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(get("/api/v1/customer/home")).andExpect(status().isUnauthorized());
    }

    // 결제수단 목록이 배열 JSON 이고 id·isDefault 칸 이름이 맞는지 본다 (소유자 확인은 서비스 몫이라 여기서는 가짜로 둔다)
    @Test
    void 결제수단은_고객_소유_목록만() throws Exception {
        UUID customer = ManagerMvc.customer(jwtDecoder, resolver, "c");
        UUID id = UUID.randomUUID();
        when(payments.methods(customer)).thenReturn(List.of(new PaymentMethodResponse(id, "신한카드 (1234)", true)));
        mvc.perform(get("/api/v1/customer/payment-methods").header("Authorization", "Bearer c")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString())).andExpect(jsonPath("$[0].isDefault").value(true));
    }

    // 촬영 장비 화면(GET /manager/device, /status): 매니저만 열 수 있고, 응답에는 가린 키(keyMasked)만 있고 전체 키(key) 칸은 없어야 한다
    @Test
    void 바디캠_화면은_매니저만_키는_마스킹된_값만() throws Exception {
        UUID manager = ManagerMvc.manager(jwtDecoder, resolver, "m");
        ManagerMvc.customer(jwtDecoder, resolver, "c");
        when(live.device(manager)).thenReturn(new DeviceResponse("안심캠 #AC-00231", "연결됨", true, List.of(new DeviceStatusRow("카메라", "정상", "ok")),
                new DeviceResponse.Stream("rtmp://lk.example/x", "****-**-1234"), "방금 전"));
        when(live.deviceStatus(manager)).thenReturn(List.of(new DeviceStatusRow("카메라", "정상", "ok")));

        mvc.perform(get("/api/v1/manager/device").header("Authorization", "Bearer m")).andExpect(status().isOk())
                .andExpect(jsonPath("$.stream.keyMasked").value("****-**-1234")).andExpect(jsonPath("$.stream.key").doesNotExist());
        mvc.perform(get("/api/v1/manager/device/status").header("Authorization", "Bearer m")).andExpect(status().isOk()).andExpect(jsonPath("$[0].label").value("카메라"));
        mvc.perform(get("/api/v1/manager/device").header("Authorization", "Bearer c")).andExpect(status().isForbidden());
    }

    // 약관(/terms)은 SecurityConfig 가 로그인 없이 통과시킨다 — 그래서 토큰 없이 부른다.
    // audience=customer 면 배열, manager 면 { terms, note } 객체. admin 처럼 모르는 값은 TermsService.requireAudience 가 400 으로 막는다
    // (static 메서드라 가짜 서비스로 바뀌지 않고 실제 코드가 돈다)
    @Test
    void 약관은_인증_없이_audience_별_모양이고_모르는_audience_는_400() throws Exception {
        when(terms.customerTerms()).thenReturn(List.of(new TermSummary("service", "서비스 이용약관", true)));
        when(terms.managerTerms()).thenReturn(new ManagerTermsResponse(List.of(new TermSummary("recording", "촬영 동의", true)), "안내"));
        when(terms.term("service", "customer")).thenReturn(new TermResponse("service", "서비스 이용약관", "본문"));

        mvc.perform(get("/api/v1/terms").param("audience", "customer")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value("service"));
        mvc.perform(get("/api/v1/terms").param("audience", "manager")).andExpect(status().isOk())
                .andExpect(jsonPath("$.terms[0].id").value("recording")).andExpect(jsonPath("$.note").value("안내"));
        mvc.perform(get("/api/v1/terms/service").param("audience", "customer")).andExpect(status().isOk()).andExpect(jsonPath("$.body").value("본문"));
        mvc.perform(get("/api/v1/terms").param("audience", "admin")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION"));
    }
}
