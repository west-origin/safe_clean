package kr.ansimclean.api.support;

import kr.ansimclean.api.ManagerMvc;
import kr.ansimclean.api.common.config.CorsConfig;
import kr.ansimclean.api.common.error.GlobalExceptionHandler;
import kr.ansimclean.api.common.security.CurrentUserResolver;
import kr.ansimclean.api.common.security.JwtToCurrentUserConverter;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.push.PushController;
import kr.ansimclean.api.push.PushService;
import kr.ansimclean.api.support.dto.FaqResponse;
import kr.ansimclean.api.support.dto.InquiryResponse;
import kr.ansimclean.api.support.dto.NoticeResponse;
import kr.ansimclean.api.support.dto.NoticeSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 지원·푸시 API 의 권한·JSON 모양 (docs/api.md §5.5): FAQ·공지는 인증 없이, 문의·토큰은 로그인 사용자 */
// 컨트롤러 슬라이스 테스트(CODE_GUIDE.md 3-12): 두 컨트롤러만 띄우고 서비스는 @MockitoBean 가짜다.
// 여기서 보는 것은 경로별 권한(401), 요청 본문 검증(400), 응답 JSON 모양이다.
// @Import 로 진짜 SecurityConfig(경로 권한)·JwtToCurrentUserConverter(토큰 → 사용자)·CorsConfig(SecurityConfig 가 쓰는 CORS 설정)·
// GlobalExceptionHandler(오류 JSON)를 넣는다 — 그래서 401·400 응답이 운영과 같은 설정으로 나온다.
// 로그인 사용자는 ManagerMvc 가 "이 토큰 문자열 = 이 역할의 사용자" 로 정해 준다.
@WebMvcTest({ SupportController.class, PushController.class })
@Import({ SecurityConfig.class, JwtToCurrentUserConverter.class, CorsConfig.class, GlobalExceptionHandler.class })
class SupportControllerTest {
    @Autowired
    MockMvc mvc; // 서버를 띄우지 않고 HTTP 요청을 흉내 내 컨트롤러를 부르는 도구 (perform → andExpect 로 응답 확인)
    // @MockitoBean: 스프링 안의 그 객체를 Mockito 가짜로 바꾼다. when(…) 으로 정하지 않은 호출은 null·빈 값을 돌려준다
    @MockitoBean
    SupportService support;
    @MockitoBean
    PushService push;
    @MockitoBean
    JwtDecoder jwtDecoder; // 토큰 해석기 — ManagerMvc 가 가짜 토큰 문자열을 사용자 JWT 로 풀게 정한다
    @MockitoBean
    CurrentUserResolver resolver; // 사용자 id → 역할. JwtToCurrentUserConverter 가 쓴다 (ManagerMvc 가 값을 정한다)

    // 준비: 서비스 가짜가 FAQ 목록·공지 목록·공지 본문을 돌려주게 정한다.
    // 확인: Authorization 헤더 없이 세 GET 이 200 이고(SecurityConfig 의 permitAll), 서비스 값이 응답 JSON 에 그대로 실린다.
    // jsonPath("$[0].question") = 응답 배열 첫 항목의 question 필드, "$.body" = 응답 객체의 body 필드
    @Test
    void FAQ_와_공지는_인증_없이_audience_로_읽는다() throws Exception {
        UUID id = UUID.randomUUID();
        when(support.faq("customer")).thenReturn(List.of(new FaqResponse(id, "질문", "답")));
        when(support.notices("manager")).thenReturn(List.of(new NoticeSummary(id, "공지", "2026.09.10")));
        when(support.notice(id, "customer")).thenReturn(new NoticeResponse(id, "공지", "2026.09.10", "본문"));

        mvc.perform(get("/api/v1/support/faq").param("audience", "customer")).andExpect(status().isOk()).andExpect(jsonPath("$[0].question").value("질문"));
        mvc.perform(get("/api/v1/support/notices").param("audience", "manager")).andExpect(status().isOk()).andExpect(jsonPath("$[0].date").value("2026.09.10"));
        mvc.perform(get("/api/v1/support/notices/" + id).param("audience", "customer")).andExpect(status().isOk()).andExpect(jsonPath("$.body").value("본문"));
    }

    // 준비: 토큰 "c" 를 가입을 마친 고객으로 만든다.
    // 확인: ① 문의 POST 가 사용자 id·Idempotency-Key·본문을 그대로 서비스에 넘긴다 (verify)
    // ② 공백뿐인 본문·2,001자 본문은 400 — 서비스는 가짜라, 이 400 은 InquiryRequest 의 @NotBlank·@Size 검증(@Valid)에서 나온다
    // ③ Authorization 헤더가 없으면 401
    @Test
    void 문의는_로그인_사용자만_Idempotency_Key_를_넘기고_검증은_400() throws Exception {
        UUID customer = ManagerMvc.customer(jwtDecoder, resolver, "c");
        UUID id = UUID.randomUUID();
        // eq(…) = 그 값과 같을 때, any() = 어떤 값이든. Mockito 는 인자 하나라도 any() 같은 매처를 쓰면
        // 나머지 인자도 매처여야 해서 customer·"k1" 을 eq(…) 로 감쌌다
        when(support.submit(eq(customer), eq("k1"), any())).thenReturn(new InquiryResponse(id));

        mvc.perform(post("/api/v1/support/inquiries").header("Authorization", "Bearer c").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"예약이 안 돼요\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()));
        verify(support).submit(customer, "k1", "예약이 안 돼요");
        mvc.perform(post("/api/v1/support/inquiries").header("Authorization", "Bearer c").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"  \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION")).andExpect(jsonPath("$.error.message").value("문의 내용을 입력해주세요"));
        mvc.perform(post("/api/v1/support/inquiries").header("Authorization", "Bearer c").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"" + "가".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/support/inquiries").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"x\"}")).andExpect(status().isUnauthorized());
    }

    // 준비: 토큰 "m" 을 가입을 마친 매니저로 만든다.
    // 확인: PUT(등록)은 사용자 id·platform·token 을, DELETE(해제)는 사용자 id·token 을 서비스에 넘긴다.
    // token 이 빠진 본문은 RegisterTokenRequest 의 @NotBlank 로 400, Authorization 헤더가 없으면 401
    @Test
    void 푸시_토큰은_로그인_사용자가_등록하고_로그아웃_때_지운다() throws Exception {
        UUID manager = ManagerMvc.manager(jwtDecoder, resolver, "m");
        mvc.perform(put("/api/v1/push/token").header("Authorization", "Bearer m").contentType(MediaType.APPLICATION_JSON).content("{\"platform\":\"ios\",\"token\":\"tok\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
        verify(push).register(manager, "ios", "tok");
        mvc.perform(delete("/api/v1/push/token").header("Authorization", "Bearer m").contentType(MediaType.APPLICATION_JSON).content("{\"platform\":\"ios\",\"token\":\"tok\"}"))
                .andExpect(status().isOk());
        verify(push).unregister(manager, "tok"); // 해제 본문에 platform 이 있어도 서비스에는 token 만 넘긴다 (PushController.unregister)
        mvc.perform(put("/api/v1/push/token").header("Authorization", "Bearer m").contentType(MediaType.APPLICATION_JSON).content("{\"platform\":\"ios\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/push/token").contentType(MediaType.APPLICATION_JSON).content("{\"platform\":\"ios\",\"token\":\"tok\"}")).andExpect(status().isUnauthorized());
        // never(): 이 조건의 호출이 한 번도 없었는지 본다. 조건이 token "anon" 이라 바로 위 401 요청(token "tok")은
        // 이 줄의 대상이 아니다 — 그 요청은 401 응답으로 확인한다
        verify(push, never()).register(any(), any(), eq("anon"));
    }
}
