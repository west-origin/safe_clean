package kr.ansimclean.api.payment;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import kr.ansimclean.api.common.error.ApiException;

class PaymentTest {
    final Instant now = Instant.parse("2026-10-17T00:00:00Z");
    Payment payment() { return Payment.pending(UUID.randomUUID(), null, "toss", 85000, now); }
    TossPaymentGateway.Result result(Payment p, String status, long balance) {
        return new TossPaymentGateway.Result("key", p.getId().toString(), status, "KRW", 85000, balance);
    }
    @Test void amount_and_key_cannot_be_replaced() {
        Payment p = payment();
        assertThatThrownBy(() -> p.requestConfirmation("key", 1, now)).isInstanceOf(ApiException.class);
        assertThat(p.getStatus()).isEqualTo("pending");
        p.requestConfirmation("key", 85000, now);
        p.requestConfirmation("key", 85000, now);
        assertThatThrownBy(() -> p.requestConfirmation("other", 85000, now)).isInstanceOf(ApiException.class);
    }
    @Test void paid_partial_and_full_refund_never_regress_on_old_notifications() {
        Payment p = payment(); p.requestConfirmation("key", 85000, now);
        p.apply(result(p, "DONE", 85000), now);
        p.apply(result(p, "PARTIAL_CANCELED", 45000), now);
        p.apply(result(p, "DONE", 85000), now);
        assertThat(p.getStatus()).isEqualTo("partially_refunded");
        assertThat(p.getRefundedAmount()).isEqualTo(40000);
        p.apply(result(p, "CANCELED", 0), now);
        p.apply(result(p, "DONE", 85000), now);
        assertThat(p.getStatus()).isEqualTo("refunded");
        assertThat(p.getRefundedAmount()).isEqualTo(85000);
    }
    @Test void wrong_order_currency_amount_key_and_balance_are_rejected() {
        Payment p = payment(); p.requestConfirmation("key", 85000, now);
        for (var bad : java.util.List.of(
                new TossPaymentGateway.Result("key", "other", "DONE", "KRW", 85000, 85000),
                new TossPaymentGateway.Result("other", p.getId().toString(), "DONE", "KRW", 85000, 85000),
                new TossPaymentGateway.Result("key", p.getId().toString(), "DONE", "USD", 85000, 85000),
                new TossPaymentGateway.Result("key", p.getId().toString(), "DONE", "KRW", 1, 1),
                result(p, "DONE", -1), result(p, "DONE", 85001), result(p, "CANCELED", 1))) {
            assertThatThrownBy(() -> p.apply(bad, now)).isInstanceOf(ApiException.class);
        }
        assertThat(p.getStatus()).isEqualTo("authorized");
    }
    @Test void authentication_and_virtual_account_are_not_payment_success() {
        Payment p = payment();
        p.apply(result(p, "IN_PROGRESS", 85000), now);
        p.apply(result(p, "WAITING_FOR_DEPOSIT", 85000), now);
        assertThat(p.getStatus()).isEqualTo("pending");
        p.apply(result(p, "EXPIRED", 85000), now);
        assertThat(p.getStatus()).isEqualTo("failed");
    }
    @Test void webhook_work_is_limited_per_order() {
        Payment p = payment();
        assertThat(p.acceptWebhook(now)).isTrue();
        assertThat(p.acceptWebhook(now.plusSeconds(29))).isFalse();
        assertThat(p.acceptWebhook(now.plusSeconds(30))).isTrue();
    }
}
