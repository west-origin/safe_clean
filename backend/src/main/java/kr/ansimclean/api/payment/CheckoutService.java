package kr.ansimclean.api.payment;

import java.util.UUID;
import kr.ansimclean.api.booking.Booking;
import kr.ansimclean.api.booking.BookingService;
import kr.ansimclean.api.booking.BookingStatus;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.events.PaymentWorkRequested;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.common.web.Idempotent;
import kr.ansimclean.api.payment.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** DB 트랜잭션만 담당. 잠금 순서는 항상 예약 → 결제 → 환불이다. */
@Service
@Transactional
public class CheckoutService {
    private final PaymentRepository payments;
    private final PaymentRefundRepository refunds;
    private final BookingService bookings;
    private final CheckoutProperties properties;
    private final Idempotent idempotent;
    private final OutboxService outbox;
    private final AppClock clock;
    private final kr.ansimclean.api.profile.ProfileService profiles;

    public CheckoutService(PaymentRepository payments, PaymentRefundRepository refunds, BookingService bookings,
                           CheckoutProperties properties, Idempotent idempotent, OutboxService outbox, AppClock clock,
                           kr.ansimclean.api.profile.ProfileService profiles) {
        this.payments = payments; this.refunds = refunds; this.bookings = bookings; this.properties = properties;
        this.idempotent = idempotent; this.outbox = outbox; this.clock = clock;
        this.profiles = profiles;
    }

    public CheckoutResponse prepare(UUID userId, UUID bookingId, String key) {
        properties.requireEnabled(); requireKey(key);
        return idempotent.run(key, userId, "checkout:" + bookingId, CheckoutResponse.class, () -> {
            profiles.holdLive(userId);
            Booking b = ownBooking(userId, bookingId);
            Payment p = payments.findFirstByBookingIdAndProviderAndStatusNotOrderByCreatedAtDesc(bookingId, "toss", "failed").orElse(null);
            if (p == null || "failed".equals(p.getStatus())) {
                if (b.getStatus() == BookingStatus.CANCELLED || b.getTotalAmount() <= 0) throw new ApiException(ErrorCode.INVALID_STATE, "이 예약은 결제할 수 없어요");
                p = payments.save(Payment.pending(bookingId, null, "toss", b.getTotalAmount(), clock.now()));
            }
            return response(p, b);
        });
    }

    public CheckoutResponse get(UUID userId, UUID id) {
        Booking b = ownBooking(userId, bookingIdOf(id));
        return response(payments.lockById(id).orElseThrow(ApiException::notFound), b);
    }

    public CheckoutResponse confirm(UUID userId, UUID id, String key, ConfirmPaymentRequest req) {
        properties.requireEnabled(); requireKey(key);
        return idempotent.run(key, userId, java.util.List.of("confirm", id, req), CheckoutResponse.class, () -> {
            profiles.holdLive(userId);
            Booking b = ownBooking(userId, bookingIdOf(id));
            Payment p = payments.lockById(id).orElseThrow(ApiException::notFound);
            if (b.getStatus() == BookingStatus.CANCELLED && "pending".equals(p.getStatus())) throw new ApiException(ErrorCode.INVALID_STATE);
            boolean enqueue = "pending".equals(p.getStatus());
            p.requestConfirmation(req.paymentKey(), req.amount(), clock.now());
            if (enqueue) { b.recordPayment(p.getStatus()); outbox.publish(new PaymentWorkRequested(id)); }
            return response(p, b);
        });
    }

    // 웹훅은 조회를 깨우는 신호일 뿐이다. 본문의 상태·paymentKey·금액을 신뢰하지 않는다.
    public void webhook(String orderId) {
        if (!properties.enabled() || orderId == null) return;
        UUID id;
        try { id = UUID.fromString(orderId); } catch (IllegalArgumentException e) { return; }
        Payment p = payments.lockById(id).orElse(null);
        if (p != null && "toss".equals(p.getProvider()) && p.acceptWebhook(clock.now())) outbox.publish(new PaymentWorkRequested(id));
    }

    public java.util.List<UUID> due() { return properties.enabled() ? payments.findDueIds(clock.now()) : java.util.List.of(); }
    @Transactional(readOnly = true)
    public boolean hasUnresolvedPayment(UUID userId) { return payments.hasUnresolvedPayment(userId); }
    public void enqueue(UUID id) {
        Payment p = payments.lockById(id).orElseThrow(ApiException::notFound);
        p.defer(clock.now()); outbox.publish(new PaymentWorkRequested(id));
    }

    public record Work(UUID id, String paymentKey, long amount, boolean confirm, UUID refundId) {}

    public Work work(UUID id) {
        Booking b = bookings.lockForUpdate(bookingIdOf(id));
        Payment p = payments.lockById(id).orElseThrow(ApiException::notFound);
        p.defer(clock.now());
        UUID refundId = null;
        if (b.getStatus() == BookingStatus.CANCELLED && ("paid".equals(p.getStatus()) || "partially_refunded".equals(p.getStatus()))) {
            PaymentRefund refund = refunds.findByPaymentId(id).orElseGet(() -> refunds.save(PaymentRefund.pending(p, clock.now())));
            refundId = refund.getId();
        }
        return new Work(id, p.getProviderTxId(), p.getAmount(), "authorized".equals(p.getStatus()) && b.getStatus() != BookingStatus.CANCELLED, refundId);
    }

    public void apply(UUID id, TossPaymentGateway.Result result) {
        Booking b = bookings.lockForUpdate(bookingIdOf(id));
        Payment p = payments.lockById(id).orElseThrow(ApiException::notFound);
        // 실패 확정 뒤 새 결제 시도가 생길 수 있다. 옛 실패 알림이 새 결제의 요약을 unpaid로 되돌리면 안 된다.
        if ("failed".equals(p.getStatus())) return;
        p.apply(result, clock.now()); b.recordPayment(p.getStatus());
        if ("refunded".equals(p.getStatus())) refunds.findByPaymentId(id).ifPresent(r -> r.done(clock.now()));
    }

    private Booking ownBooking(UUID userId, UUID bookingId) {
        Booking b = bookings.lockForUpdate(bookingId);
        if (!userId.equals(b.getCustomerId())) throw ApiException.notFound();
        return b;
    }
    private UUID bookingIdOf(UUID id) {
        return payments.findCheckoutBookingId(id).orElseThrow(ApiException::notFound);
    }
    private void requireKey(String key) {
        if (key == null || key.isBlank()) throw ApiException.validation("Idempotency-Key 가 필요해요");
    }
    private CheckoutResponse response(Payment p, Booking b) {
        return new CheckoutResponse(p.getId(), p.getBookingId(), p.getId().toString(), p.getAmount(), "KRW",
                p.getStatus(), p.getRefundedAmount(), properties.clientKey(), "안심클린 방문 서비스",
                b.getStatus() != BookingStatus.CANCELLED && "pending".equals(p.getStatus()),
                refunds.findByPaymentId(p.getId()).map(PaymentRefund::getStatus).orElse(null));
    }
}
