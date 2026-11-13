package kr.ansimclean.api.offer;

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
import kr.ansimclean.api.offer.dto.AdminCancelRequestItem;
import kr.ansimclean.api.offer.dto.AssignResponse;
import kr.ansimclean.api.offer.dto.AssignableManager;
import kr.ansimclean.api.offer.dto.CancelDecisionResponse;
import kr.ansimclean.api.offer.dto.ReassignmentItem;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 어드민 방문 취소 승인·관리자 배정 API (docs/api.md '매니저 방문 취소 승인·재배정') — 컨트롤러 슬라이스(CODE_GUIDE.md 3-12).
 * ReassignService 는 가짜, 경로 권한(SecurityConfig: /admin/** = admin + MFA)은 진짜. 권한이 없으면 서비스가 불리지 않는지와 JSON 모양을 본다.
 * 서비스 안의 두 번째 겹(AccessPolicy)은 ReassignServiceTest, 실제 DB 흐름은 ReassignIT 가 본다.
 */
@WebMvcTest(AdminReassignController.class)
@Import({ SecurityConfig.class, JwtToCurrentUserConverter.class, CorsConfig.class, GlobalExceptionHandler.class, AccessPolicy.class })
@EnableConfigurationProperties(AppProperties.class)
@ActiveProfiles("test")
class AdminReassignControllerTest {
    @Autowired
    MockMvc mvc;

    @MockitoBean
    ReassignService service;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    CurrentUserResolver resolver;

    static final UUID REQUEST = UUID.randomUUID();
    static final UUID BOOKING = UUID.randomUUID();
    static final UUID MANAGER = UUID.randomUUID();

    @Test
    void 어드민_MFA_가_아니면_취소_요청_목록도_승인도_못_한다() throws Exception {
        AdminTokens.stub(jwtDecoder, resolver);
        String approve = "/api/v1/admin/cancel-requests/" + REQUEST + "/approve";
        mvc.perform(post(approve)).andExpect(status().isUnauthorized());
        mvc.perform(post(approve).header("Authorization", AdminTokens.bearer(AdminTokens.ADMIN_NO_MFA))).andExpect(status().isForbidden());
        mvc.perform(post(approve).header("Authorization", AdminTokens.bearer(AdminTokens.MANAGER_MFA))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/cancel-requests").header("Authorization", AdminTokens.bearer(AdminTokens.MANAGER_MFA))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/reassignments").header("Authorization", AdminTokens.bearer(AdminTokens.ADMIN_NO_MFA))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/bookings/" + BOOKING + "/assign").header("Authorization", AdminTokens.bearer(AdminTokens.MANAGER_MFA))
                .contentType(MediaType.APPLICATION_JSON).content("{\"managerId\":\"" + MANAGER + "\"}")).andExpect(status().isForbidden());
        verify(service, never()).approve(any(), any());
        verify(service, never()).assign(any(), any(), any());
    }

    @Test
    void 취소_요청_목록과_승인_거절_응답_모양() throws Exception {
        AdminTokens.stub(jwtDecoder, resolver);
        String admin = AdminTokens.bearer(AdminTokens.ADMIN_MFA);
        when(service.cancelRequests(any())).thenReturn(List.of(new AdminCancelRequestItem(REQUEST, BOOKING, "김매니저", "질병·부상", null,
                "2026-11-13T06:00:00Z", "11월 13일 오후 3:00", "2026년 11월 20일 오전 10:00", "서울특별시 강남구 테헤란로 1", "이고객")));
        mvc.perform(get("/api/v1/admin/cancel-requests").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(REQUEST.toString()))
                .andExpect(jsonPath("$[0].managerName").value("김매니저"))
                .andExpect(jsonPath("$[0].reason").value("질병·부상"))
                .andExpect(jsonPath("$[0].visitLabel").value("2026년 11월 20일 오전 10:00"));

        when(service.approve(any(), eq(REQUEST))).thenReturn(new CancelDecisionResponse(REQUEST, BOOKING, "approved", "calling", "2026-11-14T06:00:00Z"));
        mvc.perform(post("/api/v1/admin/cancel-requests/" + REQUEST + "/approve").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.next").value("calling"))
                .andExpect(jsonPath("$.reassignUntil").value("2026-11-14T06:00:00Z"));

        // 거절은 본문 없이도 된다(메모 선택). 이미 처리된 요청이면 409 + 서버 문구
        when(service.reject(any(), eq(REQUEST), isNull())).thenReturn(new CancelDecisionResponse(REQUEST, BOOKING, "rejected", null, null));
        mvc.perform(post("/api/v1/admin/cancel-requests/" + REQUEST + "/reject").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("rejected"));
        UUID done = UUID.randomUUID();
        when(service.reject(any(), eq(done), eq("통화함"))).thenThrow(new ApiException(ErrorCode.INVALID_STATE, "이미 처리된 취소 요청이에요"));
        mvc.perform(post("/api/v1/admin/cancel-requests/" + done + "/reject").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"통화함\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.message").value("이미 처리된 취소 요청이에요"));
    }

    @Test
    void 관리자_배정_대기_목록과_매니저_고르기와_직접_배정_응답_모양() throws Exception {
        AdminTokens.stub(jwtDecoder, resolver);
        String admin = AdminTokens.bearer(AdminTokens.ADMIN_MFA);
        when(service.reassignments(any())).thenReturn(List.of(new ReassignmentItem(BOOKING, "no_accept", "2026-11-14T06:00:00Z", "2026-11-20T01:00:00Z",
                "2026년 11월 20일 오전 10:00", List.of("서울특별시 강남구 테헤란로 1", "101동 101호"), new ReassignmentItem.Customer("이고객", "010-1234-5678"),
                new ReassignmentItem.Manager(MANAGER, "김매니저"), "2026-11-13T05:00:00Z", "2026-11-13T06:00:00Z", "질병·부상")));
        mvc.perform(get("/api/v1/admin/reassignments").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bookingId").value(BOOKING.toString()))
                .andExpect(jsonPath("$[0].reason").value("no_accept"))
                .andExpect(jsonPath("$[0].customer.phone").value("010-1234-5678"))
                .andExpect(jsonPath("$[0].address[1]").value("101동 101호"))
                .andExpect(jsonPath("$[0].cancelledManager.name").value("김매니저"))
                .andExpect(jsonPath("$[0].requestedAt").value("2026-11-13T05:00:00Z"))
                .andExpect(jsonPath("$[0].approvedAt").value("2026-11-13T06:00:00Z"));

        UUID other = UUID.randomUUID();
        when(service.assignableManagers(any(), eq(BOOKING))).thenReturn(List.of(new AssignableManager(other, "박매니저", "010-2222-3333", true)));
        mvc.perform(get("/api/v1/admin/bookings/" + BOOKING + "/assignable-managers").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("박매니저")).andExpect(jsonPath("$[0].available").value(true));

        when(service.assign(any(), eq(BOOKING), eq(other))).thenReturn(new AssignResponse(BOOKING, other));
        mvc.perform(post("/api/v1/admin/bookings/" + BOOKING + "/assign").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"managerId\":\"" + other + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.managerId").value(other.toString()));
    }
}
