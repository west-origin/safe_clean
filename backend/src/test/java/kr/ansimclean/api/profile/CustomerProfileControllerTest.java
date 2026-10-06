package kr.ansimclean.api.profile;

import kr.ansimclean.api.common.config.AppProperties;
import kr.ansimclean.api.common.config.CorsConfig;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.GlobalExceptionHandler;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.CurrentUserResolver;
import kr.ansimclean.api.common.security.JwtToCurrentUserConverter;
import kr.ansimclean.api.common.security.Roles;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.profile.dto.AddressResponse;
import kr.ansimclean.api.profile.dto.NotificationResponse;
import kr.ansimclean.api.profile.dto.ProfileFormResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 프로필·주소·알림 API 의 검증·JSON 모양 (docs/api.md §5.2) */
// CustomerProfileController 슬라이스 테스트: 컨트롤러와 보안 설정만 띄우고 ProfileService 는 가짜로 둔다.
// 확인하는 것: 응답 JSON 필드 이름, 요청 검증 실패(400)·없음(404) 오류 모양, 매니저 토큰이 고객 경로에서 403 인지.
// Import 로 실제 보안(SecurityConfig·JwtToCurrentUserConverter)·CORS·오류 처리 설정을 함께 올려 403·400 응답 모양이 실제와 같다.
// EnableConfigurationProperties(AppProperties) = CorsConfig 등이 읽는 app.* 설정값.
// ActiveProfiles("test") = src/main/resources/application-test.yml 설정을 더해 띄운다
@WebMvcTest(CustomerProfileController.class)
@Import({ SecurityConfig.class, JwtToCurrentUserConverter.class, CorsConfig.class, GlobalExceptionHandler.class })
@EnableConfigurationProperties(AppProperties.class)
@ActiveProfiles("test")
class CustomerProfileControllerTest {
    // MockMvc = 실제 서버를 띄우지 않고 HTTP 요청을 흉내 내 컨트롤러에 보내는 도구. perform(요청).andExpect(기대) 로 쓴다
    @Autowired
    MockMvc mvc;

    // MockitoBean = 스프링에 등록될 진짜 빈 대신 Mockito 가짜 객체를 넣는다
    @MockitoBean
    ProfileService service;

    // 토큰 해독기도 가짜 — 실제 서명 검사 없이, 정해 둔 문자열 토큰을 정해 둔 JWT 로 바꿔 준다
    @MockitoBean
    JwtDecoder jwtDecoder;

    // 사용자 id → 역할 조회도 가짜. isSessionRevoked 는 따로 정하지 않아 기본값 false(끊기지 않은 세션)다
    @MockitoBean
    CurrentUserResolver resolver;

    /**
     * 테스트용 고객 로그인 준비: 문자열 token 을 보내면 새 고객 id 의 JWT 로 해독되고,
     * 그 id 가 가입을 끝낸 고객(Roles.CUSTOMER, profileComplete=true)으로 조회되게 한다. 만든 id 를 돌려준다
     */
    UUID customer(String token) {
        UUID id = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue(token).header("alg", "HS256").subject(id.toString()).claim("aud", List.of("authenticated"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        when(jwtDecoder.decode(token)).thenReturn(jwt);
        when(resolver.resolve(id)).thenReturn(Optional.of(new CurrentUser(id, Roles.CUSTOMER, true)));
        return id;
    }

    // GET /profile 의 phone, PATCH /profile 의 nickname 필드 이름을 확인하고,
    // 공백 이름은 UpdateProfileRequest 의 NotBlank 검사에 걸려 400 과 그 문구가 error.message 로 나가는지 본다.
    // jsonPath("$.phone") = 응답 JSON 의 최상위 phone 값
    @Test
    void 내_정보는_name_nickname_phone_이고_빈_이름은_400() throws Exception {
        UUID id = customer("c");
        when(service.profileForm(id)).thenReturn(new ProfileFormResponse("김하늘", "하늘", "12345678"));
        when(service.updateProfile(id, "김하늘", "구름")).thenReturn(new ProfileFormResponse("김하늘", "구름", "12345678"));
        mvc.perform(get("/api/v1/customer/profile").header("Authorization", "Bearer c"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.phone").value("12345678"));
        mvc.perform(patch("/api/v1/customer/profile").header("Authorization", "Bearer c").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"김하늘\",\"nickname\":\"구름\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nickname").value("구름"));
        mvc.perform(patch("/api/v1/customer/profile").header("Authorization", "Bearer c").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.message").value("이름을 입력해주세요"));
    }

    // POST /addresses 는 추가된 주소 하나(id·label …)를 객체로 돌려준다 — 이름의 '배열 한 줄 모양' 은
    // 목록(GET /addresses) 배열의 한 항목과 같은 모양이라는 뜻이다 (docs/api.md 의 '1건').
    // 기본 주소가 없을 때 서비스가 ApiException.notFound() 를 던지면 404 + error.code=NOT_FOUND 로 바뀌는지 본다.
    // eq(id), any() = Mockito 인자 조건 — id 는 정확히 같고 두 번째 인자(요청 본문)는 무엇이든
    @Test
    void 주소_추가는_배열_한_줄_모양이고_기본_주소가_없으면_404() throws Exception {
        UUID id = customer("c");
        UUID addr = UUID.randomUUID();
        when(service.addAddress(eq(id), any())).thenReturn(new AddressResponse(addr, "회사", "서울특별시 강남구 테헤란로 123", "3층", false));
        when(service.defaultAddress(id)).thenThrow(ApiException.notFound());
        mvc.perform(post("/api/v1/customer/addresses").header("Authorization", "Bearer c").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"회사\",\"roadAddress\":\"서울특별시 강남구 테헤란로 123\",\"detailAddress\":\"3층\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(addr.toString())).andExpect(jsonPath("$.label").value("회사"));
        mvc.perform(get("/api/v1/customer/addresses/default").header("Authorization", "Bearer c"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    // PUT /addresses/{id}/default 는 바뀐 주소 목록(배열)을 돌려주고, 각 항목에 isDefault 필드가 있어야 한다.
    // jsonPath("$[0].isDefault") = 배열 첫 항목의 isDefault 값
    @Test
    void 기본_주소_바꾸기는_목록을_돌려주고_isDefault_를_싣는다() throws Exception { // 직접 사용 점검 U15
        UUID id = customer("c");
        UUID addr = UUID.randomUUID();
        when(service.makeDefaultAddress(id, addr)).thenReturn(List.of(new AddressResponse(addr, "회사", "서울특별시 강남구 테헤란로 123", "3층", true)));
        mvc.perform(put("/api/v1/customer/addresses/" + addr + "/default").header("Authorization", "Bearer c"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(addr.toString())).andExpect(jsonPath("$[0].isDefault").value(true));
    }

    // { enabled: true } 는 저장된 { key, enabled } 를 돌려주고, 빈 본문 {} 은 NotificationRequest 의 NotNull 검사로 400
    @Test
    void 알림_토글은_enabled_가_필수() throws Exception {
        UUID id = customer("c");
        when(service.updateNotification(id, "marketing", true)).thenReturn(new NotificationResponse("marketing", true));
        mvc.perform(put("/api/v1/customer/notifications/marketing").header("Authorization", "Bearer c").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.key").value("marketing")).andExpect(jsonPath("$.enabled").value(true));
        mvc.perform(put("/api/v1/customer/notifications/marketing").header("Authorization", "Bearer c").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    // customer() 대신 역할이 MANAGER 인 사용자를 직접 준비한다. 로그인은 됐지만 /customer/** 는
    // SecurityConfig 가 ROLE_CUSTOMER 만 허용하므로 403 이어야 한다 (컨트롤러까지 가지 않는다)
    @Test
    void 매니저_토큰으로는_고객_경로가_403() throws Exception {
        UUID id = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("m").header("alg", "HS256").subject(id.toString()).claim("aud", List.of("authenticated"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        when(jwtDecoder.decode("m")).thenReturn(jwt);
        when(resolver.resolve(id)).thenReturn(Optional.of(new CurrentUser(id, Roles.MANAGER, true)));
        mvc.perform(get("/api/v1/customer/addresses").header("Authorization", "Bearer m")).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/customer/addresses/" + UUID.randomUUID() + "/default").header("Authorization", "Bearer m")).andExpect(status().isForbidden());
    }
}
