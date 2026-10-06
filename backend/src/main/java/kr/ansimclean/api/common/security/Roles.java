package kr.ansimclean.api.common.security;

/** profiles.role 값과 Spring Security 권한 이름 */
// 쓰는 곳: SecurityConfig(경로 권한), JwtToCurrentUserConverter(권한 만들기), CurrentUser,
// 그 밖에 역할을 비교하는 서비스들(예: AuthService·ProfileService)
public final class Roles {
    public static final String CUSTOMER = "customer"; // profiles.role 컬럼 값 (DB check 제약: customer | manager)
    public static final String MANAGER = "manager";
    public static final String ROLE_CUSTOMER = "ROLE_CUSTOMER"; // Spring Security 권한 이름. "ROLE_" 접두사는 스프링의 역할 표기 관례
    public static final String ROLE_MANAGER = "ROLE_MANAGER";

    private Roles() {} // 상수·정적 메서드만 있는 클래스라 객체를 만들지 않는다

    /** 가입을 끝낸 사용자: ROLE_CUSTOMER / ROLE_MANAGER */
    // role 은 "customer" 또는 "manager" (null 이면 NullPointerException — profiles.role 은 not null 이다)
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
}
