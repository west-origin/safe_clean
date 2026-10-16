package kr.ansimclean.api.payment;
import java.util.UUID;
import kr.ansimclean.api.auth.AccountDeletionGuard;
import kr.ansimclean.api.common.security.Roles;
import org.springframework.stereotype.Component;

/** 탈퇴와 새 승인 요청은 프로필 잠금으로 직렬화한다. 결과 불명 청구·환불이 남으면 탈퇴를 보류한다. */
@Component
public class PaymentDeletionGuard implements AccountDeletionGuard {
    private final CheckoutService service;
    public PaymentDeletionGuard(CheckoutService service) { this.service = service; }
    @Override public String blockReason(UUID userId, String role) {
        return Roles.CUSTOMER.equals(role) && service.hasUnresolvedPayment(userId)
                ? "결제 또는 환불 결과를 확인 중이에요. 처리가 끝난 뒤 탈퇴해주세요" : null;
    }
}
