package kr.ansimclean.api.common.security;

import kr.ansimclean.api.catalog.AddressSearchService;
import kr.ansimclean.api.catalog.CatalogController;
import kr.ansimclean.api.catalog.CatalogService;
import kr.ansimclean.api.catalog.dto.AddressSearchResponse;
import kr.ansimclean.api.catalog.dto.CategoryResponse;
import kr.ansimclean.api.common.config.AppProperties;
import kr.ansimclean.api.common.config.CorsConfig;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 경로별 권한과 오류 응답 모양 ({ error: { code, message } }) — BACKEND_PLAN.md §5.0·§7.2 */
// 컨트롤러 슬라이스 테스트(CODE_GUIDE.md 3-12)인데 컨트롤러는 카탈로그(CatalogController) 하나만 있어서,
// 다른 경로(/customer/home 등)는 보안 검사를 통과하면 404, 막히면 401·403 이 된다 — 그 차이로 권한 규칙을 확인한다.
// @EnableConfigurationProperties(AppProperties.class) + @ActiveProfiles("test"): application-test.yml 의 app.* 값
// (CORS 허용 출처 http://localhost:5175 등)을 AppProperties 로 읽는다.
@WebMvcTest(CatalogController.class)
@Import({ SecurityConfig.class, JwtToCurrentUserConverter.class, CorsConfig.class, GlobalExceptionHandler.class })
@EnableConfigurationProperties(AppProperties.class)
@ActiveProfiles("test")
class SecurityConfigTest {
    @Autowired
    MockMvc mvc; // 실제 포트 없이 HTTP 요청을 흉내 내 보내는 도구

    // @MockitoBean: 스프링 빈 자리에 Mockito 목(가짜 객체)을 넣는다
    @MockitoBean
    CatalogService catalog;

    @MockitoBean
    AddressSearchService addresses; // GET /catalog/address (카카오 호출은 AddressSearchServiceTest 가 본다)

    // 토큰 해석기. 테스트마다 stubJwt 로 '이 문자열이면 이 토큰' 을 정한다 (서명 검증은 SupabaseJwtDecoderConfigTest 가 본다)
    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    CurrentUserResolver resolver; // 기본: 프로필 없음 (Optional.empty)

    // 토큰 없이 카탈로그 GET → 200. 서비스가 notFound 를 던지면 404 와 { error: { code, message } } 모양
    @Test
    void 카탈로그는_인증_없이_열리고_없는_카테고리는_404_JSON() throws Exception {
        when(catalog.category("home")).thenReturn(new CategoryResponse("가정/원룸 청소", "recurring", null));
        when(catalog.category("nope")).thenThrow(ApiException.notFound());

        mvc.perform(get("/api/v1/catalog/categories/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("가정/원룸 청소"))
                .andExpect(jsonPath("$.promoText").doesNotExist());
        mvc.perform(get("/api/v1/catalog/categories/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("찾을 수 없어요"));
    }

    // 주소 검색도 로그인 없이 열리고, 응답은 앱 searchAddress 계약 { query, results: [{ id, main, sub }] } 모양이다
    @Test
    void 주소_검색은_인증_없이_query_와_results_를_준다() throws Exception {
        when(addresses.search("테헤란로 123")).thenReturn(new AddressSearchResponse("테헤란로 123",
                List.of(new AddressSearchResponse.Result("1168010100", "서울특별시 강남구 테헤란로 123", "(역삼동)"))));

        mvc.perform(get("/api/v1/catalog/address").param("q", "테헤란로 123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("테헤란로 123"))
                .andExpect(jsonPath("$.results[0].id").value("1168010100"))
                .andExpect(jsonPath("$.results[0].main").value("서울특별시 강남구 테헤란로 123"))
                .andExpect(jsonPath("$.results[0].sub").value("(역삼동)"));
    }

    // /catalog/reasons 는 kind 쿼리 파라미터가 필수다 — 빠지면 400 VALIDATION
    @Test
    void 필수_파라미터가_없으면_400_VALIDATION() throws Exception {
        mvc.perform(get("/api/v1/catalog/reasons"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION"));
    }

    @Test
    void 토큰이_없으면_401_JSON() throws Exception {
        mvc.perform(get("/api/v1/customer/home"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/json;charset=UTF-8"))
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    /**
     * 목 JwtDecoder 가 이 문자열을 받으면 유효한 토큰(무작위 사용자 id, aud=authenticated, 1분 유효)을 돌려주게 한다.
     * 돌려준 id 로 resolver 의 답(어떤 사용자인지)을 정한다. resolver 를 정하지 않으면 '프로필 없음' 이다
     */
    private UUID stubJwt(String token) {
        UUID id = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue(token).header("alg", "HS256").subject(id.toString()).claim("aud", List.of("authenticated"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        when(jwtDecoder.decode(eq(token))).thenReturn(jwt);
        return id;
    }

    @Test
    void 토큰은_있지만_프로필이_없으면_401_UNAUTHENTICATED() throws Exception {
        stubJwt("t");
        // §7.2: profiles 가 없거나 탈퇴한 계정의 토큰은 401 — 앱은 401 에서만 로그아웃한다
        mvc.perform(get("/api/v1/customer/home").header("Authorization", "Bearer t"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/v1/manager/home").header("Authorization", "Bearer t"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 가입_중_고객은_주소_저장만_되고_홈은_403() throws Exception {
        // CurrentUser 의 세 번째 값 false = 가입을 아직 끝내지 않았다(profileComplete) → 권한이 PENDING_CUSTOMER
        UUID id = stubJwt("p");
        when(resolver.resolve(id)).thenReturn(java.util.Optional.of(new CurrentUser(id, Roles.CUSTOMER, false)));
        // 주소 저장 경로는 통과(컨트롤러가 이 슬라이스에 없어 404 지만 403 이 아니다)
        mvc.perform(put("/api/v1/customer/profile/address").header("Authorization", "Bearer p").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/customer/home").header("Authorization", "Bearer p"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 가입을_끝낸_고객은_customer_경로를_통과한다() throws Exception {
        UUID id = stubJwt("c");
        when(resolver.resolve(id)).thenReturn(java.util.Optional.of(new CurrentUser(id, Roles.CUSTOMER, true)));
        mvc.perform(get("/api/v1/customer/home").header("Authorization", "Bearer c")).andExpect(status().isNotFound()); // 403 아님
        mvc.perform(get("/api/v1/manager/home").header("Authorization", "Bearer c")).andExpect(status().isForbidden());
    }

    // 브라우저가 다른 출처 요청 전에 보내는 OPTIONS 사전 요청(preflight): test 프로필의 허용 출처는 통과하고
    // Access-Control-Allow-Origin 헤더를 받는다. 목록에 없는 출처는 403
    @Test
    void 허용된_origin_의_preflight_만_통과한다() throws Exception {
        mvc.perform(options("/api/v1/catalog/time-slots").header("Origin", "http://localhost:5175").header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5175"));
        mvc.perform(options("/api/v1/catalog/time-slots").header("Origin", "https://evil.example").header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
