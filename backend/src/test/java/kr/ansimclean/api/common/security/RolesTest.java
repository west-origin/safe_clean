package kr.ansimclean.api.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 역할 4개의 권한 이름·매니저 계정 판단·로그인 앱 (rbac.md §2) */
class RolesTest {
    @Test
    void 역할마다_스프링_권한_이름이_있다() {
        assertThat(Roles.authorityOf(Roles.CUSTOMER)).isEqualTo(Roles.ROLE_CUSTOMER);
        assertThat(Roles.authorityOf(Roles.MANAGER)).isEqualTo(Roles.ROLE_MANAGER);
        assertThat(Roles.authorityOf(Roles.REGIONAL_MANAGER)).isEqualTo(Roles.ROLE_REGIONAL_MANAGER);
        assertThat(Roles.authorityOf(Roles.ADMIN)).isEqualTo(Roles.ROLE_ADMIN);
    }

    @Test
    void 매니저_계정은_매니저와_지역_매니저뿐이다() {
        assertThat(Roles.isManagerRole(Roles.MANAGER)).isTrue();
        assertThat(Roles.isManagerRole(Roles.REGIONAL_MANAGER)).isTrue();
        assertThat(Roles.isManagerRole(Roles.ADMIN)).isFalse();
        assertThat(Roles.isManagerRole(Roles.CUSTOMER)).isFalse();
        assertThat(Roles.isManagerRole(null)).isFalse();
    }

    @Test
    void 지역_매니저와_어드민은_매니저_앱으로_로그인한다() {
        assertThat(Roles.appOf(Roles.CUSTOMER)).isEqualTo("customer");
        assertThat(Roles.appOf(Roles.MANAGER)).isEqualTo("manager");
        assertThat(Roles.appOf(Roles.REGIONAL_MANAGER)).isEqualTo("manager");
        assertThat(Roles.appOf(Roles.ADMIN)).isEqualTo("manager");
    }

    @Test
    void CurrentUser_isManager_는_지역_매니저도_참이다() {
        assertThat(new CurrentUser(null, Roles.REGIONAL_MANAGER, true).isManager()).isTrue();
        assertThat(new CurrentUser(null, Roles.ADMIN, true).isManager()).isFalse();
    }
}
