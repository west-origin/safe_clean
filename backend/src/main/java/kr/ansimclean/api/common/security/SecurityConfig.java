package kr.ansimclean.api.common.security;

import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.error.ErrorResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * 경로별 권한 (BACKEND_PLAN.md §7.2). 두 번째 겹(소유권 검사)은 각 Service 가 한다.
 * 토큰 인증이라 CSRF·세션 없음. 401/403 도 { error } JSON 으로 돌려준다.
 * 설정 클래스(@Configuration): @Bean 메서드가 돌려준 객체를 스프링이 빈으로 등록한다. @EnableWebSecurity: Spring Security 를 켠다.
 * 함께 보는 곳: 토큰 서명 검증은 SupabaseJwtDecoderConfig, 토큰 → 사용자는 JwtToCurrentUserConverter,
 * /admin/** 의 운영자 토큰은 AdminTokenFilter·AdminAuth.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    public static final String API = "/api/v1"; // 모든 API 경로의 앞부분. 컨트롤러 주소(@RequestMapping 등)와 AdminTokenFilter 가 이 상수를 붙인다
    private static final org.slf4j.Logger HTTP_LOG = org.slf4j.LoggerFactory.getLogger("http"); // RequestLogFilter 와 같은 "http" 로거

    /**
     * 경로별 권한 규칙. 위에서부터 처음 맞는 규칙 하나만 적용되므로 순서가 중요하다
     * (예: 가입 중에도 되는 PUT 두 개가 /customer/** 규칙보다 먼저 와야 한다).
     * permitAll = 로그인 없이 통과, hasAuthority = 로그인했지만 그 권한이 없으면 403(토큰이 없으면 401),
     * authenticated = 로그인만 되어 있으면(ROLE_*·PENDING_*) 통과 — 그래서 가입 중인 사용자도 anyRequest 경로는 쓸 수 있다.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtToCurrentUserConverter converter) throws Exception {
        JsonMapper json = JsonMapper.builder().build();
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults()) // CorsConfig 의 corsConfigurationSource 빈을 쓴다
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 서버에 로그인 세션을 만들지 않는다 — 요청마다 토큰으로 확인
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll() // 서버 상태 확인
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // 브라우저의 CORS 사전 요청(preflight)은 토큰 없이 온다
                        .requestMatchers(HttpMethod.POST, API + "/payments/webhooks/toss").permitAll() // 본문을 신뢰하지 않고 PG API로 재조회
                        .requestMatchers(API + "/auth/phone/**", API + "/auth/password/**", API + "/auth/login", API + "/auth/refresh").permitAll()   // 비밀번호 재설정은 로그인하지 못한 사용자가 쓴다
                        .requestMatchers(HttpMethod.POST, API + "/auth/signup").permitAll()   // 아직 토큰이 없는 신규 사용자
                        .requestMatchers(API + "/terms/**", API + "/catalog/**").permitAll() // 약관·카탈로그(서비스 안내)는 로그인 없이
                        .requestMatchers(HttpMethod.GET, API + "/support/faq", API + "/support/notices/**", API + "/support/contact").permitAll() // 고객센터 FAQ·공지·연락처 읽기만 공개
                        .requestMatchers(API + "/live/webhooks/**").permitAll()               // LiveKit 서명으로 검증
                        .requestMatchers(API + "/admin/**").permitAll()                       // X-Admin-Token 으로 검증 (common/security/AdminAuth, §13-20)
                        // 가입 도중(PENDING_CUSTOMER)에도 되는 가입 화면 저장
                        .requestMatchers(HttpMethod.PUT, API + "/customer/profile/address", API + "/customer/profile/housing").hasAnyAuthority(Roles.ROLE_CUSTOMER, "PENDING_CUSTOMER")
                        .requestMatchers(API + "/customer/**").hasAuthority(Roles.ROLE_CUSTOMER) // 가입을 끝낸 고객만 (매니저·가입 중이면 403)
                        .requestMatchers(API + "/manager/**").hasAuthority(Roles.ROLE_MANAGER)
                        .anyRequest().authenticated())                                        // /auth/signup/complete, /auth/me, /auth/logout, /auth/account, /live/**, /support/inquiries, /push/**
                // Authorization: Bearer <JWT> 를 JwtDecoder 빈(SupabaseJwtDecoderConfig)으로 검증하고, converter 로 CurrentUserAuthentication 을 만든다
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(entryPoint(json))
                        .accessDeniedHandler(deniedHandler(json)))
                // 위(토큰 검증 실패)와 여기(토큰 없이 막힌 요청 등) 모두 같은 { error } JSON 으로 답하도록 같은 처리기를 건다
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint(json))
                        .accessDeniedHandler(deniedHandler(json)));
        return http.build();
    }

    /** 401: 로그인이 필요하다 (토큰 없음·틀림·만료·끊긴 세션·탈퇴한 계정) */
    private static AuthenticationEntryPoint entryPoint(JsonMapper json) {
        return (request, response, e) -> write(json, request, response, ErrorCode.UNAUTHENTICATED);
    }

    /** 403: 로그인은 됐지만 이 경로의 권한이 없다 (예: 고객 토큰으로 /manager/**) */
    private static AccessDeniedHandler deniedHandler(JsonMapper json) {
        return (request, response, e) -> write(json, request, response, ErrorCode.FORBIDDEN);
    }

    /** 보안 체인이 거부한 요청은 RequestLogFilter(체인 뒤)까지 오지 않는다 — 같은 모양(method path status ms userId)으로 여기서 남긴다 */
    private static void write(JsonMapper json, jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, ErrorCode code) throws IOException {
        HTTP_LOG.info("{} {} {} 0ms -", request.getMethod(), request.getRequestURI(), code.status().value());
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json.writeValueAsString(ErrorResponse.of(code)));
    }
}
