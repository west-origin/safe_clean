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
    final GiftPaymentHooks gifts = mock(GiftPaymentHooks.class);
    final Instant now = Instant.parse("2026-09-15T05:00:00Z");
    final UUID user = UUID.randomUUID();
    final Booking booking = TestBookings.requested(user, LocalDate.of(2026, 9, 22), LocalTime.NOON);
    final CheckoutService service = new CheckoutService(payments, refunds, bookings,
            new CheckoutProperties("test_ck_stub", "test_sk_stub"), idempotent, outbox, new AppClock(Clock.fixed(now, ZoneOffset.UTC)), profiles, gifts);

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
        var disabled = new CheckoutService(payments, refunds, bookings, new CheckoutProperties("", ""), idempotent, outbox, AppClock.system(), profiles, gifts);
        assertThatThrownBy(() -> disabled.prepare(user, booking.getId(), "key")).isInstanceOf(ApiException.class);
        verifyNoInteractions(payments);
    }

    // ── 금액권 선물 결제 (V070) — 선물이 예약 자리에 선다. 잠금 순서 선물 → 결제, 결과는 GiftPaymentHooks 로 같은 트랜잭션에서 ──
    Payment giftPayment(UUID giftId) {
        Payment p = Payment.pendingGift(giftId, 69000, now);
        when(payments.findCheckoutGiftId(p.getId())).thenReturn(Optional.of(giftId));
        when(payments.lockById(p.getId())).thenReturn(Optional.of(p));
        when(gifts.lockForPayment(giftId)).thenReturn(Optional.of(user));
        when(gifts.payable(giftId)).thenReturn(true);
        return p;
    }
    @Test void gift_order_uses_server_amount_and_names_the_gift() {
        UUID giftId = UUID.randomUUID();
        when(gifts.payable(giftId)).thenReturn(true);
        var res = service.prepareGift(giftId, 69000);
        assertThat(res.giftId()).isEqualTo(giftId);
        assertThat(res.bookingId()).isNull();
        assertThat(res.amount()).isEqualTo(69000);
        assertThat(res.orderName()).isEqualTo("안심클린 금액권 선물");
        assertThat(res.payable()).isTrue();
        verify(payments).save(org.mockito.ArgumentMatchers.argThat(p -> giftId.equals(p.getGiftId()) && p.getBookingId() == null));
        var disabled = new CheckoutService(payments, refunds, bookings, new CheckoutProperties("", ""), idempotent, outbox, AppClock.system(), profiles, gifts);
        assertThatThrownBy(() -> disabled.prepareGift(giftId, 69000)).isInstanceOf(ApiException.class);
    }
    @Test void gift_confirm_locks_gift_first_hides_from_others_and_refuses_closed_gifts() {
        UUID giftId = UUID.randomUUID();
        var p = giftPayment(giftId);
        assertThatThrownBy(() -> service.confirm(UUID.randomUUID(), p.getId(), "x", new ConfirmPaymentRequest("key", 69000)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(kr.ansimclean.api.common.error.ErrorCode.NOT_FOUND);
        service.confirm(user, p.getId(), "first", new ConfirmPaymentRequest("key", 69000));
        service.confirm(user, p.getId(), "second", new ConfirmPaymentRequest("key", 69000));
        var order = org.mockito.Mockito.inOrder(gifts, payments);
        order.verify(gifts).lockForPayment(giftId);
        order.verify(payments).lockById(p.getId());
        verify(gifts, times(1)).paymentChanged(giftId, "authorized");
        verify(outbox, times(1)).publish(new PaymentWorkRequested(p.getId()));
        verify(bookings, never()).lockForUpdate(any());

        UUID closed = UUID.randomUUID();
        var q = giftPayment(closed);
        when(gifts.payable(closed)).thenReturn(false); // 보낸 사람이 결제 전에 취소한 선물
        assertThatThrownBy(() -> service.confirm(user, q.getId(), "k", new ConfirmPaymentRequest("key2", 69000)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(kr.ansimclean.api.common.error.ErrorCode.INVALID_STATE);
    }
    @Test void gift_paid_result_is_handed_to_the_gift_in_the_same_transaction() {
        UUID giftId = UUID.randomUUID();
        var p = giftPayment(giftId);
        p.requestConfirmation("key", 69000, now);
        assertThat(service.work(p.getId()).confirm()).isTrue();
        service.apply(p.getId(), new TossPaymentGateway.Result("key", p.getId().toString(), "DONE", "KRW", 69000, 69000));
        assertThat(p.getStatus()).isEqualTo("paid");
        var order = org.mockito.Mockito.inOrder(gifts, payments);
        order.verify(gifts).lockForPayment(giftId);
        order.verify(payments).lockById(p.getId());
        verify(gifts).paymentChanged(giftId, "paid");
        verify(bookings, never()).lockForUpdate(any());
    }
    @Test void gift_payment_failure_is_reported_to_the_gift() {
        UUID giftId = UUID.randomUUID();
        var p = giftPayment(giftId);
        p.requestConfirmation("key", 69000, now);
        service.apply(p.getId(), new TossPaymentGateway.Result("key", p.getId().toString(), "ABORTED", "KRW", 69000, 69000));
        verify(gifts).paymentChanged(giftId, "failed");
    }

    // ── 금액권 사용 (3회차): 카드 결제 금액 = 예약 금액 − 금액권, 다 덮으면 카드 결제 없음, 결제 대기 주문은 닫는다 ──
    @Test void card_amount_is_reduced_by_the_voucher_and_full_cover_needs_no_card() {
        when(gifts.voucherCredit(booking.getId())).thenReturn(30000L);
        when(payments.findFirstByBookingIdAndProviderAndStatusNotOrderByCreatedAtDesc(booking.getId(), "toss", "failed")).thenReturn(Optional.empty());
        assertThat(service.prepare(user, booking.getId(), "k").amount()).isEqualTo(55000);

        // 옛 금액의 대기 주문은 닫고 새 금액으로
        var old = Payment.pending(booking.getId(), null, "toss", 85000, now);
        when(payments.findFirstByBookingIdAndProviderAndStatusNotOrderByCreatedAtDesc(booking.getId(), "toss", "failed")).thenReturn(Optional.of(old));
        assertThat(service.prepare(user, booking.getId(), "k2").amount()).isEqualTo(55000);
        assertThat(old.getStatus()).isEqualTo("failed");

        when(gifts.voucherCredit(booking.getId())).thenReturn(85000L);
        when(payments.findFirstByBookingIdAndProviderAndStatusNotOrderByCreatedAtDesc(booking.getId(), "toss", "failed")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.prepare(user, booking.getId(), "k3")).isInstanceOf(ApiException.class).hasMessageContaining("금액권으로 결제가 끝났어요");
    }
    @Test void closing_pending_order_only_before_approval() {
        assertThat(service.closePendingBookingPayment(booking.getId())).isEqualTo("none"); // 결제 없음
        var p = p();
        when(payments.findFirstByBookingIdAndProviderAndStatusNotOrderByCreatedAtDesc(booking.getId(), "toss", "failed")).thenReturn(Optional.of(p));
        assertThat(service.closePendingBookingPayment(booking.getId())).isEqualTo("none");
        assertThat(p.getStatus()).isEqualTo("failed");
        var q = Payment.pending(booking.getId(), null, "toss", 85000, now);
        q.requestConfirmation("key", 85000, now);
        when(payments.findFirstByBookingIdAndProviderAndStatusNotOrderByCreatedAtDesc(booking.getId(), "toss", "failed")).thenReturn(Optional.of(q));
        when(payments.lockById(q.getId())).thenReturn(Optional.of(q));
        assertThat(service.closePendingBookingPayment(booking.getId())).isEqualTo("authorized");
        assertThat(q.getStatus()).isEqualTo("authorized");
    }
    @Test void gift_card_refund_creates_one_refund_and_queues_work() {
        UUID giftId = UUID.randomUUID();
        var p = giftPayment(giftId);
        p.requestConfirmation("key", 69000, now);
        p.apply(new TossPaymentGateway.Result("key", p.getId().toString(), "DONE", "KRW", 69000, 69000), now);
        when(payments.findFirstByGiftIdOrderByCreatedAtDesc(giftId)).thenReturn(Optional.of(p));
        when(refunds.findByPaymentId(p.getId())).thenReturn(Optional.empty());
        when(refunds.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThat(service.requestGiftRefund(giftId)).isTrue();
        verify(refunds, times(1)).save(any());
        verify(outbox).publish(new PaymentWorkRequested(p.getId()));
        UUID unpaid = UUID.randomUUID();
        var q = giftPayment(unpaid);
        when(payments.findFirstByGiftIdOrderByCreatedAtDesc(unpaid)).thenReturn(Optional.of(q));
        assertThat(service.requestGiftRefund(unpaid)).isFalse();
    }
}
