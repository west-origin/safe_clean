package kr.ansimclean.api.common.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 어드민 API 슬라이스 테스트용 토큰 (rbac.md §2.1 — /admin/** 는 admin 역할 + MFA aal2).
 * stub 을 부르면 가짜 JwtDecoder·CurrentUserResolver 가 아래 세 토큰을 알아본다. 요청에는 header("Authorization", bearer(…)) 로 붙인다.
 * <ul>
 *   <li>ADMIN_MFA — 어드민 + aal2 (통과)</li>
 *   <li>ADMIN_NO_MFA — 어드민이지만 aal1 (403)</li>
 *   <li>MANAGER_MFA — 매니저 + aal2 (403: 역할이 다르다)</li>
 * </ul>
 */
public final class AdminTokens {
    public static final String ADMIN_MFA = "admin-aal2";
    public static final String ADMIN_NO_MFA = "admin-aal1";
    public static final String MANAGER_MFA = "manager-aal2";

    private AdminTokens() {}

    public static String bearer(String token) {
        return "Bearer " + token;
    }

    /** 세 토큰을 가짜 해석기·사용자 조회에 등록한다 */
    public static void stub(JwtDecoder decoder, CurrentUserResolver resolver) {
        stubOne(decoder, resolver, ADMIN_MFA, Roles.ADMIN, "aal2");
        stubOne(decoder, resolver, ADMIN_NO_MFA, Roles.ADMIN, "aal1");
        stubOne(decoder, resolver, MANAGER_MFA, Roles.MANAGER, "aal2");
    }

    private static void stubOne(JwtDecoder decoder, CurrentUserResolver resolver, String token, String role, String aal) {
        UUID id = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue(token).header("alg", "HS256").subject(id.toString()).claim("aud", List.of("authenticated")).claim("aal", aal)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        when(decoder.decode(eq(token))).thenReturn(jwt);
        when(resolver.resolve(id)).thenReturn(Optional.of(new CurrentUser(id, role, true, Roles.MANAGER.equals(role) ? "active" : null, false)));
    }
}
