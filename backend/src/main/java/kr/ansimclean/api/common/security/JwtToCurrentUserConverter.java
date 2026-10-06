package kr.ansimclean.api.common.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Supabase JWT → CurrentUserAuthentication. 권한은 profiles.role 에서만 온다 (토큰 claim 은 믿지 않는다).
 * 프로필이 없거나 탈퇴했으면 401 (§7.2 — 앱이 로그아웃할 수 있게), 가입 중(signup_completed_at 없음)이면 PENDING_*, 끝났으면 ROLE_*.
 * SecurityConfig 가 oauth2ResourceServer 의 jwtAuthenticationConverter 로 등록한다 — 서명·만료·issuer·audience 검사
 * (SupabaseJwtDecoderConfig)를 통과한 토큰만 여기로 온다.
 */
@Component
public class JwtToCurrentUserConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final CurrentUserResolver resolver;

    public JwtToCurrentUserConverter(CurrentUserResolver resolver) {
        this.resolver = resolver;
    }

    /** 검증된 토큰 → 인증 객체. InvalidBearerTokenException 을 던지면 SecurityConfig 의 entryPoint 가 401 { error } 로 답한다 */
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID id = UUID.fromString(jwt.getSubject()); // sub = Supabase 사용자 id (= profiles.id)
        // 서명·만료가 맞아도 로그아웃·비밀번호 재설정으로 끊은 세션이면 401 (감사 M-03). Supabase 사용자 토큰에는 항상 session_id 가 있다
        if (resolver.isSessionRevoked(jwt.getClaimAsString("session_id"))) throw new InvalidBearerTokenException("끊긴 세션");
        CurrentUser user = resolver.resolve(id).orElseThrow(() -> new InvalidBearerTokenException("프로필이 없거나 탈퇴한 계정"));
        // 가입을 끝냈으면 ROLE_CUSTOMER/ROLE_MANAGER, 아직이면 PENDING_*.
        // PENDING 은 SecurityConfig 에서 /customer/**·/manager/** 를 통과하지 못한다 (고객 가입 저장 PUT 두 개만 예외)
        var authorities = List.of(new SimpleGrantedAuthority(user.profileComplete() ? Roles.authorityOf(user.role()) : Roles.pendingAuthorityOf(user.role())));
        return new CurrentUserAuthentication(user, jwt, authorities);
    }
}
