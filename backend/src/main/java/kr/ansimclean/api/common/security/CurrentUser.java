package kr.ansimclean.api.common.security;

import java.util.UUID;

/**
 * 요청을 보낸 사용자 = 권한 판단의 행위자(Actor, rbac.md §1). 컨트롤러에서 @AuthenticationPrincipal CurrentUser 로 받는다.
 * 역할·계정 상태는 토큰이 아니라 **요청마다 DB 에서** 읽는다(캐시하지 않는다 — 정지·역할 변경이 다음 요청부터 반영, rbac.md §1-4).
 * 프로필이 없거나 탈퇴했으면 CurrentUser 가 만들어지지 않고 401 이다.
 * <ul>
 *   <li>id = Supabase 사용자 id (JWT 의 sub = profiles.id)</li>
 *   <li>role = profiles.role (Roles 상수 4개)</li>
 *   <li>profileComplete = 가입을 끝냈는지 (profiles.signup_completed_at 이 있는지)</li>
 *   <li>managerStatus = 매니저·지역 매니저의 계정 상태(manager_profiles.status — ManagerProfile 상수). 그 밖의 역할이나 행이 아직 없으면 null</li>
 *   <li>mfa = 이 토큰이 MFA(인증앱 6자리)를 통과한 세션인가 (JWT aal = aal2). 토큰에서 오는 유일한 값 — 세션의 성질이라 DB 에 없다</li>
 * </ul>
 * 만드는 곳: ProfileService.currentUser(DB 값) → JwtToCurrentUserConverter 가 withMfa 로 토큰의 aal 을 더한다.
 */
public record CurrentUser(UUID id, String role, boolean profileComplete, String managerStatus, boolean mfa) {
    /** 테스트·매니저 상태가 필요 없는 곳: 매니저 상태 없음, MFA 아님 */
    public CurrentUser(UUID id, String role, boolean profileComplete) {
        this(id, role, profileComplete, null, false);
    }

    /** 같은 사용자에 토큰의 MFA 여부만 바꾼 값 */
    public CurrentUser withMfa(boolean mfa) {
        return new CurrentUser(id, role, profileComplete, managerStatus, mfa);
    }

    /** 고객 계정인가 */
    public boolean isCustomer() {
        return Roles.CUSTOMER.equals(role);
    }

    /** 매니저 업무를 하는 계정인가 (매니저·지역 매니저) */
    public boolean isManager() {
        return Roles.isManagerRole(role);
    }

    /** 어드민인가 (MFA 여부는 따로 mfa 로 본다) */
    public boolean isAdmin() {
        return Roles.ADMIN.equals(role);
    }
}
