package kr.ansimclean.api.common.error;

import kr.ansimclean.api.common.config.CorsConfig;
import kr.ansimclean.api.common.security.CurrentUserResolver;
import kr.ansimclean.api.common.security.JwtToCurrentUserConverter;
import kr.ansimclean.api.common.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import kr.ansimclean.api.common.web.RequestLogFilter;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 오류 응답은 전부 { error: { code, message } } (docs/api.md §5.0). 스택트레이스·SQL·클래스 이름이 응답에 나가면 안 된다 (§11-3) */
// 컨트롤러 슬라이스 테스트(CODE_GUIDE.md 3-12). @Import 로 보안 설정·CORS·예외 처리기·요청 로그 필터와
// 아래 시험용 컨트롤러를 직접 지정해 넣는다 — 실제 요청과 같은 필터·처리기를 거친 응답을 보기 위해서다.
@WebMvcTest(controllers = GlobalExceptionHandlerTest.ProbeController.class)
@Import({ SecurityConfig.class, JwtToCurrentUserConverter.class, CorsConfig.class, GlobalExceptionHandler.class, RequestLogFilter.class, GlobalExceptionHandlerTest.ProbeController.class })
class GlobalExceptionHandlerTest {
    /** 이 테스트에서만 쓰는 시험용 컨트롤러: 일부러 예외를 던지거나(boom·conflict) 받은 JSON 을 그대로 돌려준다(echo) */
    @RestController
    @RequestMapping(SecurityConfig.API + "/catalog/probe") // /catalog/** 는 permitAll — 인증 없이 예외 경로만 본다
    static class ProbeController {
        @GetMapping("/boom")
        public String boom() { throw new IllegalStateException("secret internal detail: select * from profiles"); }

        @GetMapping("/conflict")
        public String conflict() { throw new DataIntegrityViolationException("ERROR: duplicate key value violates unique constraint \"bookings_manager_no_overlap\""); }

        // 서비스가 직접 던지는 INTERNAL (예: 가격표·app_settings 값이 빠진 설정 누락)
        @GetMapping("/internal")
        public String internal() { throw new ApiException(ErrorCode.INTERNAL, ErrorCode.INTERNAL.defaultMessage()); }

        @PostMapping("/echo")
        public Map<String, String> echo(@RequestBody Map<String, String> body) { return body; }
    }

    @Autowired
    MockMvc mvc; // 실제 포트 없이 HTTP 요청을 흉내 내 보내는 도구
    // @MockitoBean: 스프링 빈 자리에 Mockito 목을 넣는다. SecurityConfig 는 JwtDecoder 빈이, JwtToCurrentUserConverter 는
    // CurrentUserResolver 빈이 있어야 만들어지므로 채워 둔다 (이 테스트의 요청은 토큰 없이 보내 둘 다 쓰이지 않는다)
    @MockitoBean
    JwtDecoder jwtDecoder;
    @MockitoBean
    CurrentUserResolver resolver;

    // 예외 메시지의 SQL·예외 클래스 이름·스택트레이스 줄이 본문에 없고, 문의용 requestId 가 본문과 X-Request-Id 헤더에 있다
    // (헤더 값은 8글자 — RequestLogFilter 는 헷갈리는 i·l·o 를 뺀 소문자와 2~9 를 쓰고, 정규식은 그보다 넓게 본다)
    @Test
    void 예상_못_한_예외는_500_INTERNAL_이고_내부_내용을_숨긴다() throws Exception {
        mvc.perform(get("/api/v1/catalog/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL"))
                .andExpect(content().string(not(containsString("select"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("at kr.ansimclean"))))
                .andExpect(jsonPath("$.error.requestId").isString()) // 문의 번호 — 응답 헤더·로그 rid 와 같다 (검증 검토)
                .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.matchesPattern("[a-z2-9]{8}")));
    }

    // 서비스가 직접 던진 ApiException(INTERNAL) 도 예상 못 한 예외와 같은 모양이다: 본문 requestId = X-Request-Id 헤더 값,
    // message 는 '잠시 후 다시 시도해주세요 (문의 번호 <같은 값>)' — 사용자가 알려 준 번호로 로그를 찾는다 (문제 목록 30번)
    @Test
    void 서비스가_던진_INTERNAL_도_문의_번호와_requestId_를_붙인다() throws Exception {
        var res = mvc.perform(get("/api/v1/catalog/probe/internal"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL"))
                .andExpect(jsonPath("$.error.requestId").isString())
                .andReturn().getResponse();
        String rid = res.getHeader("X-Request-Id");
        String body = res.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(rid).matches("[a-z2-9]{8}");
        assertThat(com.jayway.jsonpath.JsonPath.<String>read(body, "$.error.requestId")).isEqualTo(rid);
        assertThat(com.jayway.jsonpath.JsonPath.<String>read(body, "$.error.message")).isEqualTo(ErrorCode.INTERNAL.defaultMessage() + " (문의 번호 " + rid + ")");
    }

    // DB 제약 위반(DataIntegrityViolationException)의 메시지에 든 SQL 오류 문장·제약 이름이 응답에 나가지 않는다
    @Test
    void DB_제약_위반은_409_CONFLICT_이고_SQL_을_숨긴다() throws Exception {
        mvc.perform(get("/api/v1/catalog/probe/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"))
                .andExpect(content().string(not(containsString("duplicate key"))))
                .andExpect(content().string(not(containsString("bookings_manager_no_overlap"))));
    }

    // 스프링이 컨트롤러 전에 던지는 요청 오류들도 같은 { error } 모양인지 본다:
    // GET 전용 경로에 POST → 405, JSON 을 받는 경로에 text/plain → 415, 깨진 JSON 본문 → 400 VALIDATION
    @Test
    void 잘못된_메서드는_405_잘못된_본문_형식은_415_깨진_JSON_은_400() throws Exception {
        mvc.perform(post("/api/v1/catalog/probe/boom").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
        mvc.perform(post("/api/v1/catalog/probe/echo").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));
        mvc.perform(post("/api/v1/catalog/probe/echo").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION"))
                .andExpect(content().string(not(containsString("Exception"))));
    }
}
