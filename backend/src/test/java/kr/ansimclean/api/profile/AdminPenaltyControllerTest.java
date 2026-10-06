package kr.ansimclean.api.profile;

import kr.ansimclean.api.common.config.AppProperties;
import kr.ansimclean.api.common.config.CorsConfig;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.error.GlobalExceptionHandler;
import kr.ansimclean.api.common.security.AdminAuth;
import kr.ansimclean.api.common.security.CurrentUserResolver;
import kr.ansimclean.api.common.security.JwtToCurrentUserConverter;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.profile.dto.PenaltyWaiveResponse;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /admin/penalties/{id}/waive — 운영자 토큰 없이는 면제할 수 없고, 면제 메모가 있어야 한다 (문제 목록 3번) */
// 컨트롤러 슬라이스(CODE_GUIDE.md 3-12): AdminPenaltyController 와 보안·오류 처리만 뜬다 (AdminTokenFilter 도 함께 뜬다).
// ManagerPenaltyService·AdminAuth 는 가짜라 실제 토큰 비교와 점수·정지 계산은 여기서 보지 않는다 —
// '헤더가 없으면 require 가 401 오류를 던진다' 를 테스트가 정하고, 그때 서비스가 불리지 않는지, 통과하면 응답 JSON 모양을 본다.
// 실제 토큰 설정·DB 로 도는 흐름은 PenaltyWaiveIT 가 본다.
@WebMvcTest(AdminPenaltyController.class)
@Import({ SecurityConfig.class, JwtToCurrentUserConverter.class, CorsConfig.class, GlobalExceptionHandler.class })
@EnableConfigurationProperties(AppProperties.class)
@ActiveProfiles("test")
class AdminPenaltyControllerTest {
    @Autowired
    MockMvc mvc;

    @MockitoBean
    ManagerPenaltyService service;

    @MockitoBean
    AdminAuth adminAuth;

    // 로그인 토큰은 쓰지 않지만 보안 설정이 토큰 해석기(JwtDecoder)를, JwtToCurrentUserConverter 가 CurrentUserResolver 를 쓰므로 빈 자리를 가짜로 채운다
    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    CurrentUserResolver resolver;

    // 헤더 없이 → 401 이고 waive 는 불리지 않는다. 헤더 "t"(가짜 require 가 그냥 통과) + 메모 → 200 과 { penaltyId, managerId, points, suspendedUntil }
    @Test
    void 운영자_토큰이_있어야_면제하고_누적_점수와_정지_상태를_돌려준다() throws Exception {
        UUID penalty = UUID.randomUUID();
        UUID manager = UUID.randomUUID();
        doThrow(new ApiException(ErrorCode.UNAUTHENTICATED, "관리자 토큰이 올바르지 않아요")).when(adminAuth).require(isNull());
        mvc.perform(post("/api/v1/admin/penalties/" + penalty + "/waive").contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"진단서 확인\"}"))
                .andExpect(status().isUnauthorized());
        verify(service, never()).waive(any(), any());

        when(service.waive(penalty, "진단서 확인")).thenReturn(new PenaltyWaiveResponse(penalty, manager, "3", null));
        mvc.perform(post("/api/v1/admin/penalties/" + penalty + "/waive").header(AdminAuth.HEADER, "t")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"진단서 확인\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.penaltyId").value(penalty.toString()))
                .andExpect(jsonPath("$.managerId").value(manager.toString()))
                .andExpect(jsonPath("$.points").value("3"))
                .andExpect(jsonPath("$.suspendedUntil").doesNotExist());
    }

    // 메모가 비었으면 400(VALIDATION) — 무엇을 보고 면제했는지 원장에 남아야 한다. 서비스는 불리지 않는다
    @Test
    void 면제_메모가_없으면_400() throws Exception {
        UUID penalty = UUID.randomUUID();
        mvc.perform(post("/api/v1/admin/penalties/" + penalty + "/waive").header(AdminAuth.HEADER, "t")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"  \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION"));
        verify(service, never()).waive(any(), any());
    }
}
