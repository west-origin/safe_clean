package kr.ansimclean.api.payment;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class PaymentWorkHandlerTest {
    @Test void approval_already_done_after_timeout_is_queried_without_charging_again() {
        var service = mock(CheckoutService.class); var gateway = mock(TossPaymentGateway.class);
        UUID id = UUID.randomUUID();
        when(service.work(id)).thenReturn(new CheckoutService.Work(id, "key", 85000, true, null));
        var paid = new TossPaymentGateway.Result("key", id.toString(), "DONE", "KRW", 85000, 85000);
        when(gateway.lookup(id.toString())).thenReturn(paid);
        new PaymentWorkHandler(service, gateway).handle("PaymentWorkRequested", "{\"paymentId\":\"" + id + "\"}");
        verify(service).apply(id, paid); verify(gateway, never()).confirm(any(), any(), anyLong());
    }
    @Test void cancellation_during_approval_triggers_refund_with_durable_id() {
        var service = mock(CheckoutService.class); var gateway = mock(TossPaymentGateway.class);
        UUID id = UUID.randomUUID(), refund = UUID.randomUUID();
        when(service.work(id)).thenReturn(new CheckoutService.Work(id, "key", 85000, true, null), new CheckoutService.Work(id, "key", 85000, false, refund));
        var paid = new TossPaymentGateway.Result("key", id.toString(), "DONE", "KRW", 85000, 85000);
        var cancelled = new TossPaymentGateway.Result("key", id.toString(), "CANCELED", "KRW", 85000, 0);
        when(gateway.confirm(id.toString(), "key", 85000)).thenReturn(paid);
        when(gateway.cancel(id.toString(), "key", refund)).thenReturn(cancelled);
        new PaymentWorkHandler(service, gateway).handle("PaymentWorkRequested", "{\"paymentId\":\"" + id + "\"}");
        verify(service).apply(id, paid); verify(service).apply(id, cancelled);
    }
    @Test void pg_failure_does_not_invent_a_failed_or_successful_result() {
        var service = mock(CheckoutService.class); var gateway = mock(TossPaymentGateway.class);
        UUID id = UUID.randomUUID();
        when(service.work(id)).thenReturn(new CheckoutService.Work(id, "key", 85000, true, null));
        when(gateway.confirm(id.toString(), "key", 85000)).thenThrow(new kr.ansimclean.api.common.error.ApiException(kr.ansimclean.api.common.error.ErrorCode.UPSTREAM));
        assertThatThrownBy(() -> new PaymentWorkHandler(service, gateway).handle("PaymentWorkRequested", "{\"paymentId\":\"" + id + "\"}")).isInstanceOf(RuntimeException.class);
        verify(service, never()).apply(any(), any());
    }
}
