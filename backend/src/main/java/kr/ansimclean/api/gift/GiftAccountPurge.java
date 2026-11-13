package kr.ansimclean.api.gift;

import kr.ansimclean.api.auth.AccountPurge;
import kr.ansimclean.api.common.security.Roles;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** 탈퇴 + 5년 파기: 주고받은 선물의 메시지(자유 입력)만 지운다. 선물·금액권 행은 금액 이력이라 남긴다 */
@Component
public class GiftAccountPurge implements AccountPurge {
    private final GiftStore store;

    public GiftAccountPurge(GiftStore store) {
        this.store = store;
    }

    @Override
    public void purge(UUID userId, String role) {
        if (Roles.CUSTOMER.equals(role)) store.scrubMessages(userId);
    }
}
