package kr.ansimclean.api.common.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;

/** 검증된 JWT + 우리 쪽 CurrentUser. principal 이 CurrentUser 라서 @AuthenticationPrincipal 로 바로 받는다. */
// JwtToCurrentUserConverter 가 만들고, Spring Security 가 요청 동안 SecurityContext(지금 요청의 로그인 정보)에 둔다.
// RequestLogFilter 도 여기서 CurrentUser 를 꺼내 로그의 userId 로 쓴다.
public class CurrentUserAuthentication extends AbstractAuthenticationToken {
    private final CurrentUser user;
    private final Jwt jwt;

    // authorities: ROLE_* 또는 PENDING_* 권한 하나 (SecurityConfig 가 경로마다 검사한다).
    // setAuthenticated(true): 토큰 검증을 이미 마쳤으므로 인증된 것으로 표시한다
    public CurrentUserAuthentication(CurrentUser user, Jwt jwt, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.user = user;
        this.jwt = jwt;
        setAuthenticated(true);
    }

    /** 서명 검증을 마친 원본 JWT */
    @Override
    public Object getCredentials() {
        return jwt;
    }

    /** 컨트롤러의 @AuthenticationPrincipal 에 들어가는 값 */
    @Override
    public CurrentUser getPrincipal() {
        return user;
    }
}
