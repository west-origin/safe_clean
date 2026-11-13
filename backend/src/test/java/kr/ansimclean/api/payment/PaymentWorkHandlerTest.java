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
        when(gateway.cancel(id.toString(), "key", refund, null)).thenReturn(cancelled);
        new PaymentWorkHandler(service, gateway).handle("PaymentWorkRequested", "{\"paymentId\":\"" + id + "\"}");
        verify(service).apply(id, paid); verify(service).apply(id, cancelled);
    }
    @Test void series_payment_cancels_each_cancelled_visit_share_in_turn() { // 요금표 전환 V074: 회차 몫 부분 취소를 차례로
        var service = mock(CheckoutService.class); var gateway = mock(TossPaymentGateway.class);
        UUID id = UUID.randomUUID(), r1 = UUID.randomUUID(), r2 = UUID.randomUUID();
        var paid = new TossPaymentGateway.Result("key", id.toString(), "DONE", "KRW", 329000, 329000);
        var after1 = new TossPaymentGateway.Result("key", id.toString(), "PARTIAL_CANCELED", "KRW", 329000, 246750);
        var after2 = new TossPaymentGateway.Result("key", id.toString(), "PARTIAL_CANCELED", "KRW", 329000, 164500);
        when(service.work(id)).thenReturn(new CheckoutService.Work(id, "key", 329000, false, null),
                new CheckoutService.Work(id, "key", 329000, false, r1, 82250L), new CheckoutService.Work(id, "key", 329000, false, r2, 82250L),
                new CheckoutService.Work(id, "key", 329000, false, null));
        when(gateway.lookup(id.toString())).thenReturn(paid);
        when(gateway.cancel(id.toString(), "key", r1, 82250L)).thenReturn(after1);
        when(gateway.cancel(id.toString(), "key", r2, 82250L)).thenReturn(after2);
        new PaymentWorkHandler(service, gateway).handle("PaymentWorkRequested", "{\"paymentId\":\"" + id + "\"}");
        var order = inOrder(service, gateway);
        order.verify(service).apply(id, paid);
        order.verify(gateway).cancel(id.toString(), "key", r1, 82250L);
        order.verify(service).apply(id, after1);
        order.verify(gateway).cancel(id.toString(), "key", r2, 82250L);
        order.verify(service).apply(id, after2);
    }
    @Test void the_same_pending_refund_is_not_requested_twice_in_one_run() {
        var service = mock(CheckoutService.class); var gateway = mock(TossPaymentGateway.class);
        UUID id = UUID.randomUUID(), r1 = UUID.randomUUID();
        var paid = new TossPaymentGateway.Result("key", id.toString(), "DONE", "KRW", 329000, 329000);
        when(service.work(id)).thenReturn(new CheckoutService.Work(id, "key", 329000, false, null), new CheckoutService.Work(id, "key", 329000, false, r1, 82250L));
        when(gateway.lookup(id.toString())).thenReturn(paid);
        when(gateway.cancel(id.toString(), "key", r1, 82250L)).thenReturn(paid); // 아직 반영 안 된 결과
        new PaymentWorkHandler(service, gateway).handle("PaymentWorkRequested", "{\"paymentId\":\"" + id + "\"}");
        verify(gateway, times(1)).cancel(id.toString(), "key", r1, 82250L);
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
