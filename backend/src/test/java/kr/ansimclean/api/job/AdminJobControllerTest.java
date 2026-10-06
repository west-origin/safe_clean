package kr.ansimclean.api.job;

import kr.ansimclean.api.common.config.AppProperties;
import kr.ansimclean.api.common.config.CorsConfig;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.error.GlobalExceptionHandler;
import kr.ansimclean.api.common.security.AdminAuth;
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

/** POST /admin/jobs/{id}/complete — 운영자 토큰 없이는 닫을 수 없다 (자체 점검 #17) */
// 컨트롤러 슬라이스(CODE_GUIDE.md 3-12). JobService·AdminAuth 는 가짜라 실제 토큰 비교(AdminAuth.require 안쪽)는 여기서 보지 않는다.
// 막아야 하는 문제: 매니저가 '청소 완료' 를 누르지 않고 떠난 작업을 닫는 운영자 API 가 토큰 없이 열리는 것.
// @EnableConfigurationProperties(AppProperties) = 설정값 묶음(app.*)을 슬라이스에서도 만들게 한다. @ActiveProfiles("test") = 테스트 설정 파일을 쓴다.
@WebMvcTest(AdminJobController.class)
@Import({ SecurityConfig.class, JwtToCurrentUserConverter.class, CorsConfig.class, GlobalExceptionHandler.class })
@EnableConfigurationProperties(AppProperties.class)
@ActiveProfiles("test")
class AdminJobControllerTest {
    @Autowired
    MockMvc mvc;

    @MockitoBean
    JobService service;

    @MockitoBean
    AdminAuth adminAuth;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    CurrentUserResolver resolver;

    // 준비: 가짜 AdminAuth 는 헤더가 없을 때(require(null))만 401 오류를 던지고, 다른 값은 그냥 통과시킨다(가짜 객체의 기본 동작).
    // require 는 AdminTokenFilter(컨트롤러 앞)와 컨트롤러가 둘 다 부른다.
    // 확인: 헤더 없이 부르면 401 이고 completeByAdmin 이 한 번도 불리지 않는다 → 헤더를 넣으면 200 과 { jobId }
    @Test
    void 운영자_토큰이_있어야_작업을_닫고_jobId_를_돌려준다() throws Exception {
        UUID job = UUID.randomUUID();
        // doThrow(…).when(객체).메서드(…) = 반환값이 없는(void) 메서드가 불리면 예외를 던지게 꾸미는 Mockito 문법
        doThrow(new ApiException(ErrorCode.UNAUTHENTICATED, "관리자 토큰이 올바르지 않아요")).when(adminAuth).require(isNull());
        mvc.perform(post("/api/v1/admin/jobs/" + job + "/complete")).andExpect(status().isUnauthorized());
        verify(service, never()).completeByAdmin(job); // verify(…, never()) = 이 메서드가 한 번도 불리지 않았는지 확인

        when(service.completeByAdmin(job)).thenReturn(new JobIdResponse(job));
        mvc.perform(post("/api/v1/admin/jobs/" + job + "/complete").header(AdminAuth.HEADER, "t"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.jobId").value(job.toString()));
    }
}
