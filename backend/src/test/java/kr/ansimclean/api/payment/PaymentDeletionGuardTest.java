package kr.ansimclean.api.payment;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class PaymentDeletionGuardTest {
    @Test void unresolved_customer_charge_blocks_deletion_but_manager_is_not_a_payer() {
        var service = mock(CheckoutService.class); UUID id = UUID.randomUUID();
        when(service.hasUnresolvedPayment(id)).thenReturn(true);
        var guard = new PaymentDeletionGuard(service);
        assertThat(guard.blockReason(id, "customer")).contains("결제 또는 환불");
        assertThat(guard.blockReason(id, "manager")).isNull();
        when(service.hasUnresolvedPayment(id)).thenReturn(false);
        assertThat(guard.blockReason(id, "customer")).isNull();
    }
}
