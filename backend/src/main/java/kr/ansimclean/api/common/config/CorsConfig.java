package kr.ansimclean.api.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

// CORS: 브라우저(웹뷰 포함)는 다른 출처(origin)의 서버를 부를 때 서버가 허락한 출처인지 먼저 확인하고, 아니면 응답을 막는다.
// 이 파일이 그 허락 규칙을 정한다. SecurityConfig 의 .cors(Customizer.withDefaults()) 가 아래 빈을 찾아 쓴다.
// 허용 목록은 AppProperties.Cors (환경변수 ALLOWED_ORIGINS). 운영 값 검사는 ProductionGuard 가 한다.
/** 허용 origin 은 ALLOWED_ORIGINS 만 (Capacitor: capacitor://localhost, https://localhost). 비어 있으면 어떤 origin 도 허용하지 않는다. */
@Configuration
public class CorsConfig {
    /** 모든 경로에 같은 CORS 규칙을 건다 */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // 앱이 붙이는 헤더: 로그인 토큰, JSON 본문 형식, 멱등 키. 목록에 없는 헤더(X-Admin-Token 등)를 붙인 브라우저 요청은
        // 사전 확인(preflight, OPTIONS) 단계에서 막힌다
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        config.setMaxAge(3600L); // 브라우저가 사전 확인 결과를 1시간(3600초) 기억해 같은 요청마다 OPTIONS 를 다시 보내지 않는다
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
