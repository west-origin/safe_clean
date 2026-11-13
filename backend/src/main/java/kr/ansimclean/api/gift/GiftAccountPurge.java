package kr.ansimclean.api.gift;

import kr.ansimclean.api.auth.AccountPurge;
import kr.ansimclean.api.common.security.Roles;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** 탈퇴 + 5년 파기: 주고받은 선물의 메시지(자유 입력)와 남은 계좌 환불 계좌 정보를 지운다. 선물·금액권·환불 행은 금액 이력이라 남긴다 */
@Component
public class GiftAccountPurge implements AccountPurge {
    private final GiftStore store;
    private final VoucherStore vouchers;

    public GiftAccountPurge(GiftStore store, VoucherStore vouchers) {
        this.store = store;
        this.vouchers = vouchers;
    }

    @Override
    public void purge(UUID userId, String role) {
        if (!Roles.CUSTOMER.equals(role)) return;
        store.scrubMessages(userId);
        vouchers.scrubAccounts(userId); // 처리되지 않은 계좌 환불의 암호문·예금주

    }
}
