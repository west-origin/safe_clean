package kr.ansimclean.api;

import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.CurrentUserResolver;
import kr.ansimclean.api.common.security.Roles;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;

// @WebMvcTest 에서는 JwtDecoder(토큰 해석)와 CurrentUserResolver(id → 역할)가 @MockitoBean 가짜라서,
// 테스트가 "이 토큰 = 이 역할의 사용자" 를 직접 정해 줘야 한다. 이름은 ManagerMvc 지만 고객도 만든다.
// 쓰는 곳 예: OfferControllerTest·JobControllerTest.
/** 컨트롤러 슬라이스 테스트용: 토큰 문자열 하나를 매니저/고객으로 만든다 */
public final class ManagerMvc {
    private ManagerMvc() {} // static 메서드만 모은 클래스

    /**
     * token 을 새 사용자 id 의 JWT 로 풀게 하고(jwtDecoder), 그 id 를 가입을 마친(profileComplete=true) role 사용자로 보이게 한다(resolver).
     * 결과는 만든 사용자 id
     */
    public static UUID stub(JwtDecoder jwtDecoder, CurrentUserResolver resolver, String token, String role) {
        UUID id = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue(token).header("alg", "HS256").subject(id.toString()).claim("aud", List.of("authenticated"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        when(jwtDecoder.decode(token)).thenReturn(jwt);
        when(resolver.resolve(id)).thenReturn(Optional.of(new CurrentUser(id, role, true)));
        return id;
    }

    /** token → 가입을 마친 매니저 (ROLE_MANAGER 로 /manager/** 통과) */
    public static UUID manager(JwtDecoder jwtDecoder, CurrentUserResolver resolver, String token) {
        return stub(jwtDecoder, resolver, token, Roles.MANAGER);
    }

    /** token → 가입을 마친 고객 (ROLE_CUSTOMER 로 /customer/** 통과) */
    public static UUID customer(JwtDecoder jwtDecoder, CurrentUserResolver resolver, String token) {
        return stub(jwtDecoder, resolver, token, Roles.CUSTOMER);
    }
}
