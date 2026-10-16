package kr.ansimclean.api.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;

/** 키가 없으면 실제 결제는 비활성. Noop 성공으로 돈을 받은 것처럼 처리하지 않는다. */
@Component
public record CheckoutProperties(String clientKey, String secretKey) {
    public CheckoutProperties(@Value("${app.payment.client-key:}") String clientKey,
                              @Value("${app.payment.secret-key:}") String secretKey) {
        this.clientKey = clientKey;
        this.secretKey = secretKey;
    }

    public boolean enabled() {
        return (clientKey.startsWith("test_ck_") && secretKey.startsWith("test_sk_"))
                || (clientKey.startsWith("live_ck_") && secretKey.startsWith("live_sk_"));
    }
    public void requireEnabled() {
        if (!enabled()) throw new ApiException(ErrorCode.INVALID_STATE, "결제 서비스를 준비하고 있어요");
    }
    @Override public String toString() { return "CheckoutProperties[enabled=" + enabled() + "]"; }
}
