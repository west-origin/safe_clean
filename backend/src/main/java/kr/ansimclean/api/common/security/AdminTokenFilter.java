package kr.ansimclean.api.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.error.ErrorResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * /api/v1/admin/** 는 컨트롤러에 닿기 전에 여기서 X-Admin-Token 을 검사한다 (보안 검토 2026-09-22).
 * SecurityConfig 가 /admin/** 를 permitAll 로 두므로, 컨트롤러마다 AdminAuth.require 를 직접 부르는 방식은 하나만 빼먹어도 열렸다.
 * 컨트롤러의 require 호출은 그대로 둔다(이중 방어). 실패는 SecurityConfig 의 401 과 같은 { error } JSON.
 * OncePerRequestFilter: 한 요청에 한 번만 도는 서블릿 필터. @Component 라 스프링 부트가 필터로 자동 등록한다.
 */
@Component
public class AdminTokenFilter extends OncePerRequestFilter {
    static final String PREFIX = SecurityConfig.API + "/admin/"; // 이 앞부분으로 시작하는 요청만 검사한다
    private final ObjectProvider<AdminAuth> auth; // 슬라이스 테스트(@WebMvcTest)에는 AdminAuth 빈이 없을 수 있다 — 없으면 닫힌 것으로 본다
    private final JsonMapper json = JsonMapper.builder().build(); // 401 응답 본문을 JSON 으로 쓴다

    public AdminTokenFilter(ObjectProvider<AdminAuth> auth) {
        this.auth = auth;
    }

    /** true 를 돌려주면 이 필터를 건너뛴다 — 관리자 경로가 아니면 검사하지 않는다 */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PREFIX);
    }

    /** 토큰이 맞으면 다음 필터(→ 컨트롤러)로 넘기고, 틀리면 여기서 401 JSON 을 쓰고 끝낸다 (컨트롤러에 닿지 않는다) */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        try {
            AdminAuth a = auth.getIfAvailable();
            if (a == null) throw new ApiException(ErrorCode.UNAUTHENTICATED, "관리자 토큰이 올바르지 않아요");
            a.require(request.getHeader(AdminAuth.HEADER));
        } catch (ApiException e) {
            // 응답을 직접 쓴다 — 필터는 컨트롤러 밖이라 GlobalExceptionHandler(@RestControllerAdvice)가 받아 주지 않는다
            response.setStatus(ErrorCode.UNAUTHENTICATED.status().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(json.writeValueAsString(ErrorResponse.of(ErrorCode.UNAUTHENTICATED, e.getMessage())));
            return; // chain.doFilter 를 부르지 않으면 요청은 여기서 끝난다
        }
        chain.doFilter(request, response);
    }
}
