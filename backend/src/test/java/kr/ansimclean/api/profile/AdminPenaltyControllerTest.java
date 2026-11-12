package kr.ansimclean.api.profile;

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

/** POST /admin/penalties/{id}/waive — 어드민 + MFA 가 아니면 면제할 수 없고, 면제 메모가 있어야 한다 (문제 목록 3번, rbac.md §2.1) */
// 컨트롤러 슬라이스(CODE_GUIDE.md 3-12): AdminPenaltyController 와 보안·오류 처리만 뜬다. ManagerPenaltyService 는 가짜라
// 점수·정지 계산은 여기서 보지 않는다 — 권한이 없을 때 서비스가 불리지 않는지, 통과하면 응답 JSON 모양을 본다.
// 실제 DB 로 도는 흐름은 PenaltyWaiveIT 가 본다.
@WebMvcTest(AdminPenaltyController.class)
@Import({ SecurityConfig.class, JwtToCurrentUserConverter.class, CorsConfig.class, GlobalExceptionHandler.class, AccessPolicy.class })
@EnableConfigurationProperties(AppProperties.class)
@ActiveProfiles("test")
class AdminPenaltyControllerTest {
    @Autowired
    MockMvc mvc;

    @MockitoBean
    ManagerPenaltyService service;

    // 토큰 해석기·사용자 조회는 가짜 — AdminTokens.stub 이 어드민·매니저 토큰을 정한다
    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    CurrentUserResolver resolver;

    // 로그인 없음 401, MFA 없는 어드민 403 이고 waive 는 불리지 않는다. 어드민 + MFA + 메모 → 200 과 { penaltyId, managerId, points, suspendedUntil }
    @Test
    void 어드민_MFA_세션이어야_면제하고_누적_점수와_정지_상태를_돌려준다() throws Exception {
        AdminTokens.stub(jwtDecoder, resolver);
        UUID penalty = UUID.randomUUID();
        UUID manager = UUID.randomUUID();
        mvc.perform(post("/api/v1/admin/penalties/" + penalty + "/waive").contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"진단서 확인\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/penalties/" + penalty + "/waive").header("Authorization", AdminTokens.bearer(AdminTokens.ADMIN_NO_MFA))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"진단서 확인\"}"))
                .andExpect(status().isForbidden());
        verify(service, never()).waive(any(), any());

        when(service.waive(penalty, "진단서 확인")).thenReturn(new PenaltyWaiveResponse(penalty, manager, "3", null));
        mvc.perform(post("/api/v1/admin/penalties/" + penalty + "/waive").header("Authorization", AdminTokens.bearer(AdminTokens.ADMIN_MFA))
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
        AdminTokens.stub(jwtDecoder, resolver);
        UUID penalty = UUID.randomUUID();
        mvc.perform(post("/api/v1/admin/penalties/" + penalty + "/waive").header("Authorization", AdminTokens.bearer(AdminTokens.ADMIN_MFA))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"  \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION"));
        verify(service, never()).waive(any(), any());
    }
}
