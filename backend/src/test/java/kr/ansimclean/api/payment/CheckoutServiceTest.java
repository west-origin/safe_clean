package kr.ansimclean.api.payment;

import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
import kr.ansimclean.api.booking.*;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.events.PaymentWorkRequested;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.common.web.Idempotent;
import kr.ansimclean.api.payment.dto.*;

class CheckoutServiceTest {
    final PaymentRepository payments = mock(PaymentRepository.class);
    final PaymentRefundRepository refunds = mock(PaymentRefundRepository.class);
    final BookingService bookings = mock(BookingService.class);
    final Idempotent idempotent = mock(Idempotent.class);
    final OutboxService outbox = mock(OutboxService.class);
    final kr.ansimclean.api.profile.ProfileService profiles = mock(kr.ansimclean.api.profile.ProfileService.class);
    final Instant now = Instant.parse("2026-09-15T05:00:00Z");
    final UUID user = UUID.randomUUID();
    final Booking booking = TestBookings.requested(user, LocalDate.of(2026, 9, 22), LocalTime.NOON);
    final CheckoutService service = new CheckoutService(payments, refunds, bookings,
            new CheckoutProperties("test_ck_stub", "test_sk_stub"), idempotent, outbox, new AppClock(Clock.fixed(now, ZoneOffset.UTC)), profiles);

    @BeforeEach void setup() {
        when(bookings.lockForUpdate(booking.getId())).thenReturn(booking);
        when(idempotent.run(anyString(), any(), any(), eq(CheckoutResponse.class), any())).thenAnswer(i -> ((Supplier<?>) i.getArgument(4)).get());
        when(payments.save(any())).thenAnswer(i -> i.getArgument(0));
    }
    Payment p() {
        Payment p = Payment.pending(booking.getId(), null, "toss", 85000, now);
        when(payments.findCheckoutBookingId(p.getId())).thenReturn(Optional.of(booking.getId()));
        when(payments.lockById(p.getId())).thenReturn(Optional.of(p));
        return p;
    }
    @Test void uses_stored_price_and_returns_existing_order_across_new_idempotency_keys() {
        var p = p();
        when(payments.findFirstByBookingIdAndProviderAndStatusNotOrderByCreatedAtDesc(booking.getId(), "toss", "failed")).thenReturn(Optional.of(p));
        var result = service.prepare(user, booking.getId(), "first");
        assertThat(result.amount()).isEqualTo(85000);
        assertThat(service.prepare(user, booking.getId(), "other").id()).isEqualTo(result.id());
        verify(payments, never()).save(any());
    }
    @Test void hides_other_customers_payments() {
        var p = p();
        assertThatThrownBy(() -> service.get(UUID.randomUUID(), p.getId())).isInstanceOf(ApiException.class).extracting("code").isEqualTo(kr.ansimclean.api.common.error.ErrorCode.NOT_FOUND);
    }
    @Test void confirmation_is_queued_once_and_amount_tampering_does_not_enqueue() {
        var p = p();
        assertThatThrownBy(() -> service.confirm(user, p.getId(), "bad", new ConfirmPaymentRequest("key", 1))).isInstanceOf(ApiException.class);
        service.confirm(user, p.getId(), "first", new ConfirmPaymentRequest("key", 85000));
        service.confirm(user, p.getId(), "second", new ConfirmPaymentRequest("key", 85000));
        verify(outbox, times(1)).publish(new PaymentWorkRequested(p.getId()));
        assertThat(booking.getPaymentStatus()).isEqualTo("authorized");
    }
    @Test void cancellation_during_approval_becomes_a_durable_refund_with_stable_id() {
        var p = p(); p.requestConfirmation("key", 85000, now);
        booking.cancel("customer", now, 24);
        service.apply(p.getId(), new TossPaymentGateway.Result("key", p.getId().toString(), "DONE", "KRW", 85000, 85000));
        var refund = PaymentRefund.pending(p, now);
        when(refunds.findByPaymentId(p.getId())).thenReturn(Optional.empty(), Optional.of(refund));
        when(refunds.save(any())).thenReturn(refund);
        assertThat(service.work(p.getId()).refundId()).isEqualTo(refund.getId());
        assertThat(service.work(p.getId()).refundId()).isEqualTo(refund.getId());
        verify(refunds, times(1)).save(any());
    }
    @Test void unknown_or_invalid_webhook_does_not_queue_work() {
        service.webhook("bad-id"); service.webhook(null); service.webhook(UUID.randomUUID().toString());
        verifyNoInteractions(outbox);
    }
    @Test void old_failed_attempt_does_not_reset_a_later_successful_payment() {
        var p = p();
        var expired = new TossPaymentGateway.Result("key", p.getId().toString(), "EXPIRED", "KRW", 85000, 85000);
        p.apply(expired, now);
        booking.recordPayment("paid"); // 이후 새 주문으로 결제를 완료한 예약의 요약
        service.apply(p.getId(), expired);
        assertThat(booking.getPaymentStatus()).isEqualTo("paid");
    }
    @Test void no_keys_never_creates_a_fake_payment() {
        var disabled = new CheckoutService(payments, refunds, bookings, new CheckoutProperties("", ""), idempotent, outbox, AppClock.system(), profiles);
        assertThatThrownBy(() -> disabled.prepare(user, booking.getId(), "key")).isInstanceOf(ApiException.class);
        verifyNoInteractions(payments);
    }
}
