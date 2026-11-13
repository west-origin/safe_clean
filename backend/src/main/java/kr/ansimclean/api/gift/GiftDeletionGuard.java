package kr.ansimclean.api.gift;

import kr.ansimclean.api.auth.AccountDeletionGuard;
import kr.ansimclean.api.common.security.Roles;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** 처리 중인 선물(보낸·받은)이나 잔액 있는 금액권이 있으면 탈퇴를 막는다 — 금액권이 주인 없이 사라지지 않게 (문구는 코드에서 정함) */
@Component
public class GiftDeletionGuard implements AccountDeletionGuard {
    private final GiftService service;

    public GiftDeletionGuard(GiftService service) {
        this.service = service;
    }

    @Override
    public String blockReason(UUID userId, String role) {
        return Roles.CUSTOMER.equals(role) && service.hasOpenGiftOrBalance(userId)
                ? "처리 중인 선물이나 남은 금액권이 있어요. 선물을 정리하고 금액권을 사용한 뒤 탈퇴해주세요" : null;
    }
}
