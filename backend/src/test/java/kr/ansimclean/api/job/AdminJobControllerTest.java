package kr.ansimclean.api.job;

import kr.ansimclean.api.common.config.AppProperties;
import kr.ansimclean.api.common.config.CorsConfig;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.error.GlobalExceptionHandler;
import kr.ansimclean.api.common.security.AccessPolicy;
import kr.ansimclean.api.common.security.AdminTokens;
import kr.ansimclean.api.common.security.CurrentUserResolver;
import kr.ansimclean.api.common.security.JwtToCurrentUserConverter;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.job.dto.JobIdResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /admin/jobs/{id}/complete — 어드민 + MFA 가 아니면 닫을 수 없다 (자체 점검 #17, rbac.md §2.1) */
// 컨트롤러 슬라이스(CODE_GUIDE.md 3-12). JobService 는 가짜, 권한 판단(SecurityConfig + AccessPolicy)은 진짜다.
// 막아야 하는 문제: 매니저가 '청소 완료' 를 누르지 않고 떠난 작업을 닫는 어드민 API 가 어드민이 아닌 사람에게 열리는 것.
// @EnableConfigurationProperties(AppProperties) = 설정값 묶음(app.*)을 슬라이스에서도 만들게 한다. @ActiveProfiles("test") = 테스트 설정 파일을 쓴다.
@WebMvcTest(AdminJobController.class)
@Import({ SecurityConfig.class, JwtToCurrentUserConverter.class, CorsConfig.class, GlobalExceptionHandler.class, AccessPolicy.class })
@EnableConfigurationProperties(AppProperties.class)
@ActiveProfiles("test")
class AdminJobControllerTest {
    @Autowired
    MockMvc mvc;

    @MockitoBean
    JobService service;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    CurrentUserResolver resolver;

    // 확인: 로그인 없음 401, MFA 없는 어드민·매니저 403 이고 그동안 completeByAdmin 은 한 번도 불리지 않는다 → 어드민 + MFA 면 200 과 { jobId }
    @Test
    void 어드민_MFA_세션이어야_작업을_닫고_jobId_를_돌려준다() throws Exception {
        AdminTokens.stub(jwtDecoder, resolver);
        UUID job = UUID.randomUUID();
        String url = "/api/v1/admin/jobs/" + job + "/complete";
        mvc.perform(post(url)).andExpect(status().isUnauthorized());
        mvc.perform(post(url).header("Authorization", AdminTokens.bearer(AdminTokens.ADMIN_NO_MFA))).andExpect(status().isForbidden());
        mvc.perform(post(url).header("Authorization", AdminTokens.bearer(AdminTokens.MANAGER_MFA))).andExpect(status().isForbidden());
        verify(service, never()).completeByAdmin(job); // verify(…, never()) = 이 메서드가 한 번도 불리지 않았는지 확인

        when(service.completeByAdmin(job)).thenReturn(new JobIdResponse(job));
        mvc.perform(post(url).header("Authorization", AdminTokens.bearer(AdminTokens.ADMIN_MFA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.jobId").value(job.toString()));
    }
}
