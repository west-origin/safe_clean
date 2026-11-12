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
import kr.ansimclean.api.offer.dto.RedispatchResponse;
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

/** POST /admin/bookings/{id}/redispatch — 어드민 + MFA 가 아니면 다시 매칭할 수 없다 (자체 점검 #5, rbac.md §2.1) */
// 컨트롤러 슬라이스(CODE_GUIDE.md 3-12): AdminOfferController 와 보안·오류 처리만 뜬다. OfferService 는 가짜, 권한 판단은 진짜.
// 권한이 없을 때 서비스가 불리지 않는지, 통과하면 응답 JSON 이 { bookingId, offered } 인지 본다.
// 실제 DB 로 도는 흐름은 MatchingIT 의 어드민 재매칭 테스트가 본다.
@WebMvcTest(AdminOfferController.class)
@Import({ SecurityConfig.class, JwtToCurrentUserConverter.class, CorsConfig.class, GlobalExceptionHandler.class, AccessPolicy.class })
@EnableConfigurationProperties(AppProperties.class)
@ActiveProfiles("test")
class AdminOfferControllerTest {
    @Autowired
    MockMvc mvc;

    @MockitoBean
    OfferService service;

    // 토큰 해석기·사용자 조회는 가짜 — AdminTokens.stub 이 어드민·매니저 토큰을 정한다
    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    CurrentUserResolver resolver;

    @Test
    void 어드민_MFA_세션이어야_다시_매칭하고_보낸_오퍼_수를_돌려준다() throws Exception {
        AdminTokens.stub(jwtDecoder, resolver);
        UUID booking = UUID.randomUUID();
        String url = "/api/v1/admin/bookings/" + booking + "/redispatch";
        mvc.perform(post(url)).andExpect(status().isUnauthorized());
        mvc.perform(post(url).header("Authorization", AdminTokens.bearer(AdminTokens.ADMIN_NO_MFA))).andExpect(status().isForbidden());
        mvc.perform(post(url).header("Authorization", AdminTokens.bearer(AdminTokens.MANAGER_MFA))).andExpect(status().isForbidden());
        verify(service, never()).redispatchByAdmin(booking); // never() = 한 번도 불리지 않았다 — 권한이 없으면 재매칭까지 가지 않는다

        when(service.redispatchByAdmin(booking)).thenReturn(new RedispatchResponse(booking, 2));
        mvc.perform(post(url).header("Authorization", AdminTokens.bearer(AdminTokens.ADMIN_MFA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bookingId").value(booking.toString())).andExpect(jsonPath("$.offered").value(2));
    }
}
