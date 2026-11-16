package kr.ansimclean.api.auth;

import kr.ansimclean.api.common.sms.SmsSender;
import kr.ansimclean.api.common.supabase.SupabaseAuthClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 통합 (Docker 필요): 인증번호 → 검증 → 가입 → 주소 → 주거 → 약관 완료 → me → 로그인 → 탈퇴 를 실제 Postgres 위에서.
 * Supabase Auth·auth.users 조회·문자 발송·JWT 검증만 대역이다 (각각 단위 테스트가 따로 있다).
 * 준비 방식은 CODE_GUIDE.md 3-12. 컨테이너는 이 클래스의 테스트가 같이 쓰고, 스키마·seed 는 기동 때 Flyway 가 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class AuthFlowIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    static final UUID USER = UUID.randomUUID(); // Supabase 가 만들어 줬다고 칠 사용자 id
    static final String PHONE_LOCAL = "12345678"; // 앱이 보내는 모양 (010 뒤 8자리)
    static final String PHONE = "+8210" + PHONE_LOCAL; // 서버가 저장·발송에 쓰는 국제 형식

    @Autowired
    MockMvc mvc;

    // @MockitoBean: 서버 안의 그 빈을 Mockito 가짜로 바꿔 끼운다 — 바깥 서비스(Supabase·문자)에 실제로 나가지 않는다
    @MockitoBean
    SupabaseAuthClient supabase;

    @MockitoBean
    SupabaseUserLookup userLookup;

    @MockitoBean
    SmsSender sms;

    @MockitoBean
    JwtDecoder jwtDecoder;

    final JsonMapper json = JsonMapper.builder().build(); // 응답 JSON 문자열을 읽는 Jackson 도구

    // 고객은 카카오·구글로만 가입한다(2026-11-09 소셜 인증 개편). 휴대폰 인증번호·비밀번호 가입·로그인·비밀번호 탈퇴는 고객에게 403.
    // 소셜 첫 로그인 때 서버가 만드는 '가입 1단계' 프로필(ProfileService.currentUser → provisionSocial, auth.users 가 필요해 여기선
    // createForSignup 으로 같은 상태를 만든다) 다음부터 주소 → 주거 → 약관 → 홈 이 열리는지 본다
    @Test
    void 고객_가입부터_탈퇴까지() throws Exception {
        Jwt jwt = Jwt.withTokenValue("access").header("alg", "HS256").subject(USER.toString()).claim("aud", List.of("authenticated"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
        when(jwtDecoder.decode("access")).thenReturn(jwt);

        // 1~3. 휴대폰 가입 경로는 고객에게 닫혀 있다 (문자도 보내지 않는다)
        mvc.perform(post("/api/v1/auth/phone/otp").contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"" + PHONE_LOCAL + "\",\"role\":\"customer\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        verify(sms, org.mockito.Mockito.never()).send(anyString(), anyString());
        profiles.createForSignup(USER, "customer", "안심클린 고객", null, null);

        // 4. 가입 중: 홈은 403, 주소·주거 저장은 된다
        //    가입 중 권한(PENDING_CUSTOMER)은 SecurityConfig 에서 /customer/** 중 이 두 PUT 만 통과한다 (/auth/me 는 로그인만 되면 된다)
        mvc.perform(get("/api/v1/customer/home").header("Authorization", "Bearer access")).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/customer/profile/address").header("Authorization", "Bearer access").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roadAddress\":\"부산광역시 중앙대로 2127-23\",\"detailAddress\":\"금정아파트 802동 203호\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.label").value("집"));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer access"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.signupStep").value("housing"));
        mvc.perform(put("/api/v1/customer/profile/housing").header("Authorization", "Bearer access").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"housingType\":\"apartment\",\"size\":\"32\",\"sizeUnknown\":false,\"rooms\":\"3\",\"bathrooms\":\"2\",\"pet\":\"no\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer access"))
                .andExpect(jsonPath("$.signupStep").value("complete"));

        // 5. 필수 약관이 빠지면 400, 다 있으면 완료
        mvc.perform(post("/api/v1/auth/signup/complete").header("Authorization", "Bearer access").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreedTermIds\":[\"service\",\"privacy\"]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("위치기반")));
        mvc.perform(post("/api/v1/auth/signup/complete").header("Authorization", "Bearer access").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreedTermIds\":[\"service\",\"privacy\",\"location\",\"marketing\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.profileComplete").value(true)).andExpect(jsonPath("$.user.signupStep").doesNotExist());

        // 6. 완료 후엔 customer 경로 통과 (B3 에서 홈 컨트롤러가 생겨 200)
        mvc.perform(get("/api/v1/customer/home").header("Authorization", "Bearer access")).andExpect(status().isOk());

        // 7. 고객은 비밀번호 로그인·비밀번호 탈퇴가 없다 — 403 (탈퇴는 SNS 재인증 뒤 DELETE /auth/account/social)
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"" + PHONE_LOCAL + "\",\"password\":\"password1\",\"role\":\"customer\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(delete("/api/v1/auth/account").header("Authorization", "Bearer access").contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"password1\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.message").value("SNS 재인증 후 탈퇴해주세요"));
    }

    // 매니저 흐름: 인증번호 → 확인 → 가입(매니저는 주소·주거 단계가 없어 바로 signupStep=complete) → 약관 완료 → /manager/home 통과.
    // 이 테스트는 근무시간 행·manager_profiles 행을 직접 조회하지 않고, 가입 완료 뒤 manager 경로가 열리는지만 본다
    @Test
    void 매니저_가입_완료는_기본_근무시간과_미승인_프로필을_만든다() throws Exception {
        UUID manager = UUID.randomUUID();
        String local = "99998888";
        String phone = "+8210" + local;
        when(userLookup.findIdByPhone(phone)).thenReturn(Optional.empty());
        when(supabase.createUser(eq(phone), anyString())).thenReturn(manager);
        when(supabase.passwordGrant(eq(phone), anyString())).thenReturn(new SupabaseAuthClient.Session("m-access", "r", 3600, manager));
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        doNothing().when(sms).send(any(), any());
        Jwt jwt = Jwt.withTokenValue("m-access").header("alg", "HS256").subject(manager.toString()).claim("aud", List.of("authenticated"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
        when(jwtDecoder.decode("m-access")).thenReturn(jwt);

        mvc.perform(post("/api/v1/auth/phone/otp").contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"" + local + "\",\"role\":\"manager\"}")).andExpect(status().isOk());
        verify(sms).send(eq(phone), text.capture());
        Matcher m = Pattern.compile("인증번호 (\\d{6})").matcher(text.getValue());
        assertThat(m.find()).isTrue();
        JsonNode verified = json.readTree(mvc.perform(post("/api/v1/auth/phone/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + local + "\",\"code\":\"" + m.group(1) + "\",\"role\":\"manager\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verificationToken\":\"" + verified.get("verificationToken").asString() + "\",\"name\":\"김매니저\",\"password\":\"password1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("manager")).andExpect(jsonPath("$.user.signupStep").value("complete"));
        mvc.perform(post("/api/v1/auth/signup/complete").header("Authorization", "Bearer m-access").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreedTermIds\":[\"service\",\"privacy\",\"location\",\"recording\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.profileComplete").value(true));
        // 가입을 끝낸 매니저는 manager 경로를 통과한다 (B4 에서 홈 컨트롤러가 생겨 200 — 미승인이어도 홈은 열린다)
        mvc.perform(get("/api/v1/manager/home").header("Authorization", "Bearer m-access")).andExpect(status().isOk());
    }

    @Autowired
    kr.ansimclean.api.profile.ProfileService profiles;

    // 어드민 매니저 앱 로그인 (rbac.md §15-20): 비밀번호 로그인 → aal1 토큰으로는 관리자 API 403 → 6자리 확인 → aal2 토큰으로 통과
    // (없는 작업 id 라 통과하면 404). Supabase 의 MFA 호출은 가짜
    @Test
    void 어드민은_비밀번호_로그인_뒤_6자리를_거쳐야_관리자_API_가_열린다() throws Exception {
        UUID admin = UUID.randomUUID();
        String phone = "+821087654321";
        profiles.createForSignup(admin, "admin", "운영자", null, phone);
        profiles.completeSignup(admin);
        when(supabase.passwordGrant(eq(phone), anyString())).thenReturn(new SupabaseAuthClient.Session("admin-aal1", "r1", 3600, admin));
        for (String[] t : new String[][] { { "admin-aal1", "aal1" }, { "admin-aal2", "aal2" } }) {
            when(jwtDecoder.decode(t[0])).thenReturn(Jwt.withTokenValue(t[0]).header("alg", "HS256").subject(admin.toString()).claim("aud", List.of("authenticated"))
                    .claim("aal", t[1]).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build());
        }
        when(supabase.verifiedTotpFactorId("admin-aal1")).thenReturn("f1");
        when(supabase.verifyTotp("admin-aal1", "f1", "123456")).thenReturn(new SupabaseAuthClient.Session("admin-aal2", "r2", 3600, admin));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"87654321\",\"password\":\"password1\",\"role\":\"manager\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").value("admin-aal1")).andExpect(jsonPath("$.user.role").value("admin"));
        String adminApi = "/api/v1/admin/jobs/" + UUID.randomUUID() + "/complete";
        mvc.perform(post(adminApi).header("Authorization", "Bearer admin-aal1")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/manager/home").header("Authorization", "Bearer admin-aal1")).andExpect(status().isForbidden()); // 어드민은 매니저 업무를 하지 않는다

        mvc.perform(post("/api/v1/auth/mfa/verify").header("Authorization", "Bearer admin-aal1").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").value("admin-aal2")).andExpect(jsonPath("$.user.role").value("admin"));
        mvc.perform(post(adminApi).header("Authorization", "Bearer admin-aal2")).andExpect(status().isNotFound());
    }
}
