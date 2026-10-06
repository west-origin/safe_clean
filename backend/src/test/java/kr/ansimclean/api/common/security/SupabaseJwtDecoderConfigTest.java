package kr.ansimclean.api.common.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import kr.ansimclean.api.common.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** JWT 검증의 첫 관문(§7.2): 서명·issuer·aud·만료. 슬라이스·IT 는 디코더를 대역으로 바꾸므로 여기서만 실제 디코더가 돈다 */
// 스프링 없이 설정 클래스를 직접 new 해서 jwtDecoder 를 만든다. 토큰은 Nimbus 라이브러리로 테스트 안에서 직접 서명해 만든다.
class SupabaseJwtDecoderConfigTest {
    static final String SECRET = "test-secret-test-secret-test-secret-test-secret-32bytes"; // 테스트용 HS256 비밀키 (가짜 값)
    static final String URL = "https://proj.supabase.local"; // 가짜 Supabase 주소. 올바른 issuer 는 이 뒤에 /auth/v1
    static final AppProperties.Supabase SUPABASE = new AppProperties.Supabase(URL, "service-role", "secret", SECRET); // secret 모드

    JwtDecoder decoder = new SupabaseJwtDecoderConfig().jwtDecoder(new AppProperties(null, SUPABASE, null, null, null));

    /**
     * 시험용 JWT 를 만든다: secret 으로 HS256 서명, issuer·aud 는 null 이면 넣지 않는다.
     * expiresInSeconds 가 음수면 이미 만료된 토큰이다
     */
    static String token(String secret, String issuer, List<String> aud, long expiresInSeconds) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder().subject("11111111-1111-1111-1111-111111111111")
                .issueTime(new Date()).expirationTime(new Date(System.currentTimeMillis() + expiresInSeconds * 1000));
        if (issuer != null) claims.issuer(issuer);
        if (aud != null) claims.audience(aud);
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
        jwt.sign(new MACSigner(secret.getBytes()));
        return jwt.serialize();
    }

    // 같은 비밀키·issuer(URL + /auth/v1)·aud(authenticated)·1시간 유효 → 검증을 통과하고 sub(사용자 id)를 읽는다
    @Test
    void 올바른_토큰은_subject_를_준다() throws Exception {
        assertThat(decoder.decode(token(SECRET, URL + "/auth/v1", List.of("authenticated"), 3600)).getSubject())
                .isEqualTo("11111111-1111-1111-1111-111111111111");
    }

    // 한 줄씩: 다른 키로 서명 / 다른 issuer / issuer 없음 / aud 가 anon / aud 없음 / 1시간 전에 만료 — 모두 JwtException
    @Test
    void 서명_issuer_aud_만료가_틀리면_전부_거부한다() throws Exception {
        String wrongKey = "other-secret-other-secret-other-secret-other-secret-32b";
        assertThatThrownBy(() -> decoder.decode(token(wrongKey, URL + "/auth/v1", List.of("authenticated"), 3600))).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(token(SECRET, "https://evil.example/auth/v1", List.of("authenticated"), 3600))).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(token(SECRET, null, List.of("authenticated"), 3600))).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(token(SECRET, URL + "/auth/v1", List.of("anon"), 3600))).isInstanceOf(JwtException.class); // anon 키 토큰
        assertThatThrownBy(() -> decoder.decode(token(SECRET, URL + "/auth/v1", null, 3600))).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(token(SECRET, URL + "/auth/v1", List.of("authenticated"), -3600))).isInstanceOf(JwtException.class);
    }

    @Test
    void jwks_모드는_ES256_과_RS256_을_받는다() {
        // Supabase 기본 서명키는 ES256 — 빌더 기본값(RS256 만)으로 두면 모든 토큰이 401 이었다(검수 1차). 허용 알고리즘 목록으로 회귀를 막는다
        AppProperties.Supabase jwks = new AppProperties.Supabase(URL, "service-role", "jwks", null);
        JwtDecoder d = new SupabaseJwtDecoderConfig().jwtDecoder(new AppProperties(null, jwks, null, null, null));
        // 허용 알고리즘 목록은 공개 메서드로 꺼낼 수 없어 ReflectionTestUtils(비공개 필드·메서드를 이름으로 읽는 테스트 도구)로
        // 디코더 → 처리기(jwtProcessor) → 키 선택기 → 허용 목록 순서로 들어간다. 라이브러리 내부 이름이 바뀌면 이 테스트가 깨진다
        Object processor = org.springframework.test.util.ReflectionTestUtils.getField(d, "jwtProcessor");
        com.nimbusds.jose.proc.JWSKeySelector<?> selector = (com.nimbusds.jose.proc.JWSKeySelector<?>) org.springframework.test.util.ReflectionTestUtils.invokeMethod(processor, "getJWSKeySelector");
        Object algorithms = org.springframework.test.util.ReflectionTestUtils.getField(selector, "jwsAlgs"); // JWSVerificationKeySelector 의 허용 목록
        assertThat(algorithms.toString()).contains("ES256").contains("RS256");
    }
}
