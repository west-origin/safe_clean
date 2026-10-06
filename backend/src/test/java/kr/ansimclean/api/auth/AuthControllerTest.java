package kr.ansimclean.api.auth;

import kr.ansimclean.api.auth.dto.AuthResponse;
import kr.ansimclean.api.auth.dto.OtpResponse;
import kr.ansimclean.api.common.config.AppProperties;
import kr.ansimclean.api.common.config.CorsConfig;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.error.GlobalExceptionHandler;
import kr.ansimclean.api.common.security.CurrentUserResolver;
import kr.ansimclean.api.common.security.JwtToCurrentUserConverter;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.profile.dto.UserResponse;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 컨트롤러 슬라이스 테스트 (CODE_GUIDE.md 3-12): AuthController 와 웹 계층(JSON 변환·입력 검증)만 띄운다.
// @Import: 슬라이스에 자동으로 들어오지 않는 보안 설정·토큰 변환기·CORS·오류 응답 처리기, 그리고 AuthController 생성자가 받는 ClientIp 를 직접 넣는다.
// @EnableConfigurationProperties(AppProperties): CorsConfig 등이 쓰는 app.* 설정값. @ActiveProfiles("test"): application-test.yml 을 읽는다.
// AuthService 는 가짜라서, 여기서는 "요청 → 서비스 호출 인자" 와 "서비스 결과·오류 → 응답 JSON·상태 코드" 만 본다
/** 인증 API 의 입력 검증·권한·JSON 모양 (docs/api.md §5.1) */
@WebMvcTest(AuthController.class)
@Import({ SecurityConfig.class, JwtToCurrentUserConverter.class, CorsConfig.class, GlobalExceptionHandler.class, kr.ansimclean.api.common.web.ClientIp.class })
@EnableConfigurationProperties(AppProperties.class)
@ActiveProfiles("test")
class AuthControllerTest {
    // MockMvc: 서버를 실제로 띄우지 않고 HTTP 요청을 흉내 내 보내고 응답을 검사하는 도구
    @Autowired
    MockMvc mvc;

    // @MockitoBean: 스프링 안의 그 빈을 Mockito 가짜로 바꿔 끼운다
    @MockitoBean
    AuthService service;

    // 토큰 해석기 가짜 — 테스트가 "이 문자열 = 이 JWT" 를 정한다 (서명·만료 검사 없음)
    @MockitoBean
    JwtDecoder jwtDecoder;

    // 사용자 id → 역할·가입 완료 여부, 끊긴 세션인지 — 가짜
    @MockitoBean
    CurrentUserResolver resolver;

    // 준비: session_id=sid-1 이 든 토큰 "t" 를 고객으로 풀게 한다 → 로그아웃이 서비스에 (id, "sid-1", "t") 를 넘기는지 확인
    // → 그 세션을 끊긴 것으로 바꾸면(isSessionRevoked) 같은 토큰의 /auth/me 가 401 인지 확인 (JwtToCurrentUserConverter 가 막는다)
    @Test
    void 로그아웃하면_그_세션_id_를_넘기고_끊긴_세션의_토큰은_401() throws Exception { // 감사 M-03
        java.util.UUID id = java.util.UUID.randomUUID();
        org.springframework.security.oauth2.jwt.Jwt jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("t").header("alg", "HS256").subject(id.toString())
                .claim("aud", java.util.List.of("authenticated")).claim("session_id", "sid-1")
                .issuedAt(java.time.Instant.now()).expiresAt(java.time.Instant.now().plusSeconds(60)).build();
        when(jwtDecoder.decode("t")).thenReturn(jwt);
        when(resolver.resolve(id)).thenReturn(java.util.Optional.of(new kr.ansimclean.api.common.security.CurrentUser(id, "customer", true)));
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer t")).andExpect(status().isOk());
        org.mockito.Mockito.verify(service).logout(id, "sid-1", "t");

        when(resolver.isSessionRevoked("sid-1")).thenReturn(true);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/auth/me").header("Authorization", "Bearer t"))
                .andExpect(status().isUnauthorized());
    }

    // role 은 customer|manager 만 (OtpRequest 의 @Pattern). 서비스 세 번째 인자 any() 는 요청한 쪽 IP
    @Test
    void 인증번호_요청은_토큰_없이_되고_role_이_이상하면_400() throws Exception {
        when(service.requestOtp(eq("12345678"), eq("customer"), any())).thenReturn(new OtpResponse(true, 300, 60));
        mvc.perform(post("/api/v1/auth/phone/otp").contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"12345678\",\"role\":\"customer\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(true))
                .andExpect(jsonPath("$.expiresIn").value(300))
                .andExpect(jsonPath("$.resendAfter").value(60));
        mvc.perform(post("/api/v1/auth/phone/otp").contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"12345678\",\"role\":\"admin\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION"));
    }

    // 서비스까지 가지 않고 요청 본문 검증(VerifyRequest 의 @Pattern)에서 막혀 그 문구가 그대로 나간다
    @Test
    void 인증번호는_6자리_숫자여야_한다() throws Exception {
        mvc.perform(post("/api/v1/auth/phone/verify").contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"12345678\",\"code\":\"12\",\"role\":\"customer\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("인증번호 6자리를 입력해주세요"));
    }

    // 서비스가 돌려준 AuthResponse 가 accessToken·user.role·user.profileComplete·user.signupStep 이름으로 나가는지 (앱 mock 키와 같아야 한다)
    @Test
    void 가입은_토큰_없이_되고_응답에_세션과_user_가_있다() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.signup(any(), any())).thenReturn(new AuthResponse("a", "r", 3600, new UserResponse(id, "customer", "김하늘", false, "address")));
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verificationToken\":\"tok\",\"name\":\"김하늘\",\"nickname\":\"하늘\",\"password\":\"password1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("a"))
                .andExpect(jsonPath("$.user.role").value("customer"))
                .andExpect(jsonPath("$.user.profileComplete").value(false))
                .andExpect(jsonPath("$.user.signupStep").value("address"));
    }

    // 서비스가 던진 ApiException 을 GlobalExceptionHandler 가 ErrorCode 의 상태(401)·코드·한국어 문구로 바꾸는지
    @Test
    void 로그인_실패는_401_LOGIN_FAILED_JSON() throws Exception {
        when(service.login(any(), any())).thenThrow(new ApiException(ErrorCode.LOGIN_FAILED));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"12345678\",\"password\":\"x\",\"role\":\"customer\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("LOGIN_FAILED"))
                .andExpect(jsonPath("$.error.message").value("휴대폰 번호 또는 비밀번호를 확인해주세요"));
    }

    @Test
    void me_와_signup_complete_는_토큰이_필요하다() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/signup/complete").contentType(MediaType.APPLICATION_JSON).content("{\"agreedTermIds\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    // 차례로: 있는 번호 → 200 / 서비스가 PHONE_NOT_FOUND → 404 / 재설정 성공 → 새 세션 / verificationToken 이 빈 문자열 → 입력 검증 400.
    // 이름의 '짧은 비밀번호' 검사(PASSWORD_TOO_SHORT)는 서비스가 하므로 AuthServiceTest 에서 본다
    @Test
    void 비밀번호_재설정은_토큰_없이_되고_없는_번호는_404_짧은_비밀번호는_400() throws Exception {
        when(service.requestPasswordOtp(eq("12345678"), eq("customer"), any())).thenReturn(new OtpResponse(true, 300, 60));
        mvc.perform(post("/api/v1/auth/password/otp").contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"12345678\",\"role\":\"customer\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(true));

        when(service.requestPasswordOtp(eq("87654321"), eq("customer"), any())).thenThrow(new ApiException(ErrorCode.PHONE_NOT_FOUND));
        mvc.perform(post("/api/v1/auth/password/otp").contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"87654321\",\"role\":\"customer\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PHONE_NOT_FOUND"));

        when(service.resetPassword(eq("tok"), eq("newpassword1"), any()))
                .thenReturn(new AuthResponse("a", "r", 3600, new UserResponse(UUID.randomUUID(), "customer", "김하늘", true, null)));
        mvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON).content("{\"verificationToken\":\"tok\",\"password\":\"newpassword1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("a"));

        mvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON).content("{\"verificationToken\":\"\",\"password\":\"newpassword1\"}"))
                .andExpect(status().isBadRequest());
    }
}
