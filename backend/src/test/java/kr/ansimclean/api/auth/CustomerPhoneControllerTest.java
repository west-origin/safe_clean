package kr.ansimclean.api.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.ansimclean.api.common.config.AppProperties;
import kr.ansimclean.api.common.config.CorsConfig;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.error.GlobalExceptionHandler;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.CurrentUserResolver;
import kr.ansimclean.api.common.security.JwtToCurrentUserConverter;
import kr.ansimclean.api.common.security.Roles;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.common.web.ClientIp;
import kr.ansimclean.api.profile.FirstCleaningService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 고객 휴대폰 인증 API·Send SMS Hook 의 권한·입력 검증·JSON 모양 (backend/docs/api.md "고객 휴대폰 인증") */
@WebMvcTest({ CustomerPhoneController.class, SmsHookController.class })
@Import({ SecurityConfig.class, JwtToCurrentUserConverter.class, CorsConfig.class, GlobalExceptionHandler.class, ClientIp.class })
@EnableConfigurationProperties(AppProperties.class)
@ActiveProfiles("test")
class CustomerPhoneControllerTest {
    @Autowired
    MockMvc mvc;

    @MockitoBean
    CustomerPhoneService service;

    @MockitoBean
    SmsHookService hook;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    CurrentUserResolver resolver;

    UUID login(String token, String role) {
        UUID id = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue(token).header("alg", "HS256").subject(id.toString()).claim("aud", List.of("authenticated"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        when(jwtDecoder.decode(token)).thenReturn(jwt);
        when(resolver.resolve(id)).thenReturn(Optional.of(new CurrentUser(id, role, true)));
        return id;
    }

    @Test
    void 인증번호_요청은_사용자_JWT_원문을_서비스에_넘기고_만료_재발송_초를_돌려준다() throws Exception {
        UUID id = login("cust-jwt", Roles.CUSTOMER);
        when(service.request(eq(id), eq("cust-jwt"), eq("00000001"), any())).thenReturn(new CustomerPhoneService.OtpSent(180, 60, false));
        mvc.perform(post("/api/v1/customer/identity/phone/otp").header("Authorization", "Bearer cust-jwt")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"00000001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(180))
                .andExpect(jsonPath("$.resendAfter").value(60))
                .andExpect(jsonPath("$.verified").value(false));
    }

    @Test
    void 매니저_토큰과_무토큰은_막힌다() throws Exception {
        login("mgr", Roles.MANAGER);
        mvc.perform(post("/api/v1/customer/identity/phone/otp").header("Authorization", "Bearer mgr")
                .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"00000001\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/customer/identity/phone/otp")
                .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"00000001\"}")).andExpect(status().isUnauthorized());
        verify(service, never()).request(any(), anyString(), anyString(), any());
    }

    @Test
    void 확인은_숫자_6자리만_받고_자격을_돌려준다() throws Exception {
        UUID id = login("c", Roles.CUSTOMER);
        mvc.perform(post("/api/v1/customer/identity/phone/verify").header("Authorization", "Bearer c")
                .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"00000001\",\"code\":\"12345\"}")).andExpect(status().isBadRequest());
        when(service.verify(eq(id), eq("00000001"), eq("123456"), any()))
                .thenReturn(new FirstCleaningService.Eligibility(true, true, "eligible", false));
        mvc.perform(post("/api/v1/customer/identity/phone/verify").header("Authorization", "Bearer c")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"00000001\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.eligible").value(true))
                .andExpect(jsonPath("$.reason").value("eligible"))
                .andExpect(jsonPath("$.completedOneTime").value(false));
        when(service.verify(eq(id), eq("00000001"), eq("000000"), any())).thenThrow(new ApiException(ErrorCode.OTP_INVALID));
        mvc.perform(post("/api/v1/customer/identity/phone/verify").header("Authorization", "Bearer c")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"00000001\",\"code\":\"000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("OTP_INVALID"));
    }

    @Test
    void Hook_은_토큰_없이_오고_꺼져_있으면_404_거절은_Supabase_오류_형식() throws Exception {
        when(hook.enabled()).thenReturn(false);
        mvc.perform(post("/api/v1/hooks/supabase/send-sms").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
        when(hook.enabled()).thenReturn(true);
        when(hook.handle("id", "1", "v1,x", "{}")).thenReturn(new SmsHookService.Result(403, "no"));
        mvc.perform(post("/api/v1/hooks/supabase/send-sms").header("webhook-id", "id").header("webhook-timestamp", "1").header("webhook-signature", "v1,x")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.http_code").value(403))
                .andExpect(jsonPath("$.error.message").value("no"));
    }
}
