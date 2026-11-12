package kr.ansimclean.api.common.security;

/** profiles.role 값과 Spring Security 권한 이름 (역할 4개 — backend/docs/rbac.md §2) */
// 쓰는 곳: SecurityConfig(경로 권한), JwtToCurrentUserConverter(권한 만들기), CurrentUser,
// 그 밖에 역할을 비교하는 서비스들(예: AuthService·ProfileService)
public final class Roles {
    // profiles.role 컬럼 값 (DB check 제약: customer | manager | regional_manager | admin, V065)
    public static final String CUSTOMER = "customer";
    public static final String MANAGER = "manager";
    public static final String REGIONAL_MANAGER = "regional_manager"; // 지역 매니저: 담당 권역 운영 + 일반 매니저 업무 전부
    public static final String ADMIN = "admin";                       // 어드민: DB 에서만 지정한다(앱에 임명 기능 없음)
    // Spring Security 권한 이름. "ROLE_" 접두사는 스프링의 역할 표기 관례
    public static final String ROLE_CUSTOMER = "ROLE_CUSTOMER";
    public static final String ROLE_MANAGER = "ROLE_MANAGER";
    public static final String ROLE_REGIONAL_MANAGER = "ROLE_REGIONAL_MANAGER";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";
    /** 퇴사(withdrawn)한 매니저·지역 매니저의 권한 — 정산 조회만 (rbac.md §3). ROLE_ 접두사를 붙이지 않아 어떤 역할 경로도 통과하지 않는다 */
    public static final String WITHDRAWN_MANAGER = "WITHDRAWN_MANAGER";
    /** 이 토큰이 MFA(aal2) 를 통과했다 — /admin/** 에 ROLE_ADMIN 과 함께 필요하다 (rbac.md §2.1) */
    public static final String MFA_AAL2 = "MFA_AAL2";
    /** manager_profiles.status 의 퇴사 값 (ManagerProfile.WITHDRAWN 과 같은 글자 — common 이 profile 패키지를 참조하지 않으려고 따로 둔다) */
    public static final String STATUS_WITHDRAWN = "withdrawn";

    private Roles() {} // 상수·정적 메서드만 있는 클래스라 객체를 만들지 않는다

    /** 가입을 끝낸 사용자: ROLE_CUSTOMER / ROLE_MANAGER / ROLE_REGIONAL_MANAGER / ROLE_ADMIN */
    // role 은 profiles.role 값 (null 이면 NullPointerException — profiles.role 은 not null 이다)
    public static String authorityOf(String role) {
        return "ROLE_" + role.toUpperCase();
    }

    /**
     * 가입 도중(profileComplete=false): PENDING_CUSTOMER / PENDING_MANAGER — /customer/**·/manager/** 는 막힌다(고객 가입 저장 PUT 두 개만 예외).
     * 로그인만 보는 경로(SecurityConfig 의 anyRequest, 예: /auth/me·/push/**)는 가입 중에도 통과한다.
     */
    public static String pendingAuthorityOf(String role) {
        return "PENDING_" + role.toUpperCase();
    }

    /**
     * 매니저 업무를 하는 역할인가: manager 또는 regional_manager (지역 매니저는 일반 매니저 업무를 전부 한다, rbac.md §2).
     * manager_profiles 행·근무시간·패널티·정산이 있는 계정이다. 탈퇴·파기·알림 설정처럼 '매니저 계정' 을 가르는 곳은 이것으로 비교한다.
     */
    public static boolean isManagerRole(String role) {
        return MANAGER.equals(role) || REGIONAL_MANAGER.equals(role);
    }

    /**
     * 이 역할의 계정이 로그인하는 앱: "customer"(고객 앱) / "manager"(매니저 앱). 로그인·비밀번호 재설정 요청의 role 과 비교한다.
     * 어드민도 매니저 앱으로 로그인한다(rbac.md §15-20) — 비밀번호 로그인은 aal1 토큰이고, 인증앱 6자리(/auth/mfa/verify)를 통과해야
     * 관리자 API 가 열린다.
     */
    public static String appOf(String role) {
        return isManagerRole(role) || ADMIN.equals(role) ? MANAGER : role;
    }
}
