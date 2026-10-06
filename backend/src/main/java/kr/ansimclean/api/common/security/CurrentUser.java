package kr.ansimclean.api.common.security;

import java.util.UUID;

/**
 * 요청을 보낸 사용자. 컨트롤러에서 @AuthenticationPrincipal CurrentUser 로 받는다.
 * role 은 토큰이 아니라 profiles.role 에서 온다 (B2 에서 CurrentUserResolver 가 채운다). profiles.role 이 not null 이라 비지 않는다.
 * 프로필이 없거나 탈퇴했으면 CurrentUser 가 만들어지지 않고 401 이다. 가입 중인지는 profileComplete 로 본다.
 * id = Supabase 사용자 id (JWT 의 sub = profiles.id). profileComplete = 가입을 끝냈는지 (profiles.signup_completed_at 이 있는지).
 * 만드는 곳: ProfileService.currentUser (CurrentUserCache 를 거친다).
 */
public record CurrentUser(UUID id, String role, boolean profileComplete) {
    /** 고객 계정인가 */
    public boolean isCustomer() {
        return Roles.CUSTOMER.equals(role);
    }

    /** 매니저 계정인가 */
    public boolean isManager() {
        return Roles.MANAGER.equals(role);
    }
}
