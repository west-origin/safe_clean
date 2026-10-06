package kr.ansimclean.api.offer;

import kr.ansimclean.api.common.config.AppProperties;
import kr.ansimclean.api.common.config.CorsConfig;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.error.GlobalExceptionHandler;
import kr.ansimclean.api.common.security.AdminAuth;
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

/** POST /admin/bookings/{id}/redispatch — 운영자 토큰 없이는 다시 매칭할 수 없다 (자체 점검 #5) */
// 컨트롤러 슬라이스(CODE_GUIDE.md 3-12): AdminOfferController 와 보안·오류 처리만 뜬다 (AdminTokenFilter 같은 필터도 함께 뜬다).
// OfferService·AdminAuth 는 가짜라 실제 토큰 비교는 하지 않는다 — '헤더가 없으면 require 가 401 오류를 던진다' 를
// 테스트가 직접 정하고, 그때 서비스가 불리지 않는지, 통과하면 응답 JSON 이 { bookingId, offered } 인지 본다.
// 헤더 없는 요청은 필터가 먼저 그 가짜 require 를 불러 401 로 끝내므로, 컨트롤러 안의 두 번째 검사까지는 가지 않는다.
// 실제 토큰 설정으로 도는 흐름은 MatchingIT 의 운영자 재매칭 테스트가 본다.
@WebMvcTest(AdminOfferController.class)
@Import({ SecurityConfig.class, JwtToCurrentUserConverter.class, CorsConfig.class, GlobalExceptionHandler.class })
@EnableConfigurationProperties(AppProperties.class)
@ActiveProfiles("test")
class AdminOfferControllerTest {
    @Autowired
    MockMvc mvc;

    @MockitoBean
    OfferService service;

    @MockitoBean
    AdminAuth adminAuth;

    // 이 테스트는 로그인 토큰을 쓰지 않는다. 보안 설정이 토큰 해석기(JwtDecoder)를, JwtToCurrentUserConverter 가
    // CurrentUserResolver 를 쓰므로 빈 자리를 가짜로 채운다
    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    CurrentUserResolver resolver;

    @Test
    void 운영자_토큰이_있어야_다시_매칭하고_보낸_오퍼_수를_돌려준다() throws Exception {
        UUID booking = UUID.randomUUID();
        // doThrow(…).when(가짜).메서드(…) = 반환값이 없는(void) 메서드가 예외를 던지게 정하는 모양. isNull() = 인자가 null 일 때만
        doThrow(new ApiException(ErrorCode.UNAUTHENTICATED, "관리자 토큰이 올바르지 않아요")).when(adminAuth).require(isNull());
        mvc.perform(post("/api/v1/admin/bookings/" + booking + "/redispatch")).andExpect(status().isUnauthorized());
        verify(service, never()).redispatchByAdmin(booking); // never() = 한 번도 불리지 않았다 — 인증 실패면 재매칭까지 가지 않는다

        // 헤더 "t" 에는 아무것도 정하지 않았다 — 가짜 require 는 그냥 지나가므로 통과한 것으로 본다
        when(service.redispatchByAdmin(booking)).thenReturn(new RedispatchResponse(booking, 2));
        mvc.perform(post("/api/v1/admin/bookings/" + booking + "/redispatch").header(AdminAuth.HEADER, "t"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bookingId").value(booking.toString())).andExpect(jsonPath("$.offered").value(2));
    }
}
