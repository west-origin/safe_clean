package kr.ansimclean.api.common.security;

import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 중앙 권한 판단 (rbac.md §1-2·§1-3·§2.1): 어드민 작업은 admin ∧ 가입 완료 ∧ MFA 만, 나머지는 기본 거부 */
class AccessPolicyTest {
    AccessPolicy policy = new AccessPolicy();
    AccessPolicy.Resource any = AccessPolicy.Resource.of("booking", UUID.randomUUID());

    static CurrentUser user(String role, boolean complete, boolean mfa) {
        return new CurrentUser(UUID.randomUUID(), role, complete, null, mfa);
    }

    @ParameterizedTest
    @EnumSource(AccessPolicy.Action.class)
    void 어드민_MFA_세션만_어드민_작업을_한다(AccessPolicy.Action action) {
        assertThatCode(() -> policy.require(user(Roles.ADMIN, true, true), action, any)).doesNotThrowAnyException();
        // MFA 없는 어드민 · 가입 미완료 어드민 · MFA 를 한 다른 역할은 모두 403
        for (CurrentUser u : new CurrentUser[] { user(Roles.ADMIN, true, false), user(Roles.ADMIN, false, true),
                user(Roles.REGIONAL_MANAGER, true, true), user(Roles.MANAGER, true, true), user(Roles.CUSTOMER, true, true) }) {
            assertThatThrownBy(() -> policy.require(u, action, any)).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.FORBIDDEN);
        }
    }

    @Test
    void 로그인이_없으면_401() {
        assertThatThrownBy(() -> policy.require(null, AccessPolicy.Action.PENALTY_WAIVE, any)).extracting("code").isEqualTo(ErrorCode.UNAUTHENTICATED);
    }
}
