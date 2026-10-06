package kr.ansimclean.api.common.security;

import kr.ansimclean.api.common.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Supabase 가 발급한 JWT 검증 (BACKEND_PLAN.md §7.2).
 * jwks 모드: 프로젝트의 JWKS URL(비대칭 키). secret 모드: 구형 프로젝트의 HS256 비밀키.
 * 두 모드 모두 issuer(<url>/auth/v1) 와 audience(authenticated) 를 검사한다.
 * 여기서 만든 JwtDecoder 빈을 SecurityConfig 의 oauth2ResourceServer().jwt() 가 Authorization 헤더 토큰 검증에 쓴다.
 * 모드는 app.supabase.jwt-mode (환경변수 SUPABASE_JWT_MODE, 기본 jwks). test 프로필은 secret.
 */
@Configuration
public class SupabaseJwtDecoderConfig {
    static final String AUDIENCE = "authenticated"; // 토큰의 aud 에 이 값이 있어야 통과한다

    /** 설정 모드에 맞는 디코더를 만들고 issuer·audience 검사를 붙인다 */
    @Bean
    public JwtDecoder jwtDecoder(AppProperties props) {
        AppProperties.Supabase supabase = props.supabase();
        // secret 모드: 비밀키 문자열을 HMAC-SHA256 키로 써서 서명을 확인한다. 그 밖(jwks): JWKS 주소에서 공개키를 받아 확인한다
        NimbusJwtDecoder decoder = "secret".equalsIgnoreCase(supabase.jwtMode())
                ? NimbusJwtDecoder.withSecretKey(new SecretKeySpec(supabase.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256")).macAlgorithm(MacAlgorithm.HS256).build()
                // Supabase 의 기본 서명키는 ES256 (§7.2). Nimbus 빌더의 기본값은 RS256 뿐이라 명시하지 않으면 모든 토큰이 401 이 된다
                : NimbusJwtDecoder.withJwkSetUri(supabase.jwksUri()).jwsAlgorithms(algs -> { algs.add(SignatureAlgorithm.ES256); algs.add(SignatureAlgorithm.RS256); }).build();
        decoder.setJwtValidator(validator(supabase.issuer()));
        return decoder;
    }

    /** 스프링 기본 검사(예: 만료 exp·nbf 시각, iss 가 issuer 와 같은지) + aud 목록에 authenticated 가 들어 있는지. 하나라도 틀리면 401 */
    static OAuth2TokenValidator<Jwt> validator(String issuer) {
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(AUDIENCE));
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), audience);
    }
}
