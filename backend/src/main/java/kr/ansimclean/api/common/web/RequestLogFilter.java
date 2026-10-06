package kr.ansimclean.api.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.ansimclean.api.common.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import org.slf4j.MDC;

import java.io.IOException;
import java.security.SecureRandom;

/**
 * 요청 로그는 method path status ms userId 만 (CLAUDE.md §11-11). 쿼리스트링·본문·헤더는 남기지 않는다. 보안 체인이 거부한 401·403 은 SecurityConfig 가 같은 모양으로 남긴다.
 * 요청마다 짧은 id 를 만들어 MDC(rid)·응답 헤더 X-Request-Id·500(INTERNAL) 응답 본문(requestId)에 같이 둔다 — 사용자가 캡처한 오류 화면과 서버 로그를 짝지을 수 있게 (검증 검토)
 * 필터: 요청이 컨트롤러에 닿기 전에 거쳐 가는 관문 (보안 체인이 거부한 요청은 여기까지 오지 않는다). OncePerRequestFilter 는 한 요청에 한 번만 돌게 보장한다.
 * {@code @Component} 로 등록돼 스프링이 필터로 끼워 준다. 500 응답의 requestId 는 GlobalExceptionHandler 가 currentId() 로 읽는다.
 */
@Component
public class RequestLogFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger("http"); // 로거 이름 "http" — SecurityConfig 의 거부 로그도 같은 이름을 쓴다
    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "rid";
    public static final String ATTR = RequestLogFilter.class.getName() + ".id"; // 요청 객체에 id 를 붙여 둘 때의 속성 이름

    /**
     * 요청 하나를 감싼다: id 를 만들어 요청 속성·응답 헤더·MDC 에 두고 → 나머지 처리(chain)를 돌린 뒤 → 끝나면 한 줄 로그.
     * MDC: 이 스레드에서 찍는 로그에 rid 를 함께 싣는 저장소 (운영의 JSON 로그에 실린다. local 의 기본 텍스트 형식에는 안 보인다).
     * 스레드는 다음 요청에 다시 쓰이므로 finally 에서 지운다
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        long started = System.nanoTime();
        String id = newId();
        request.setAttribute(ATTR, id);
        response.setHeader(HEADER, id);
        MDC.put(MDC_KEY, id);
        try {
            chain.doFilter(request, response);
        } finally {
            // 예외가 나도 로그는 남긴다. nanoTime 차이 ÷ 1,000,000 = 밀리초
            long ms = (System.nanoTime() - started) / 1_000_000;
            // 로그인한 요청이면 사용자 id, 아니면 '-'. 전화번호·토큰 같은 개인정보는 남기지 않는다
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String userId = auth != null && auth.getPrincipal() instanceof CurrentUser u ? u.id().toString() : "-";
            // getRequestURI() 는 경로만 준다 (쿼리스트링 제외)
            log.info("{} {} {} {}ms {} rid={}", request.getMethod(), request.getRequestURI(), response.getStatus(), ms, userId, id);
            MDC.remove(MDC_KEY);
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom(); // 예측하기 어려운 난수 생성기
    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"; // 헷갈리는 글자(0/o, 1/l/i) 제외 — 사용자가 읽어 줄 수 있게

    /** 8자리 — 문의에 적을 만큼 짧고, 하루치 로그 안에서 겹칠 일은 없다 */
    static String newId() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return sb.toString();
    }

    /** 현재 요청의 id (필터 밖에서 — 오류 핸들러) */
    public static String currentId(HttpServletRequest request) {
        Object v = request == null ? null : request.getAttribute(ATTR);
        return v == null ? null : v.toString();
    }
}
