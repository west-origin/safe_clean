package kr.ansimclean.api.friend;

import kr.ansimclean.api.auth.AccountPurge;
import kr.ansimclean.api.common.security.Roles;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 탈퇴 + 5년 파기: 고객의 친구 관계·요청 이력·차단·초대 코드를 지운다 (auth.DeletedAccountPurger 가 계정마다 같은 트랜잭션에서 부른다).
 * 탈퇴 직후에는 지우지 않는다 — 목록·검색이 deleted_at 으로 거르므로 이미 보이지 않는다
 */
@Component
public class FriendAccountPurge implements AccountPurge {
    private final FriendStore store;

    public FriendAccountPurge(FriendStore store) {
        this.store = store;
    }

    @Override
    public void purge(UUID userId, String role) {
        if (!Roles.CUSTOMER.equals(role)) return;
        store.purge(userId);
    }
}
