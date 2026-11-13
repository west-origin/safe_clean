package kr.ansimclean.api.payment;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * 예약 취소의 전체 잔액 환불 한 건. 재시도 내내 같은 id를 PG 멱등키로 쓴다.
 * 시리즈 결제(V074)는 취소된 회차마다 한 행 — booking_id = 그 회차, amount = 그 회차 금액(토스 부분 취소 cancelAmount).
 */
@Entity
@Table(name = "payment_refunds")
public class PaymentRefund {
    @Id private UUID id;
    @Column(name = "payment_id", nullable = false) private UUID paymentId;
    @Column(name = "booking_id") private UUID bookingId; // 회차 몫 환불이면 그 회차, 전체 잔액 환불이면 null
    @Column(nullable = false) private long amount;
    @Column(columnDefinition = "text", nullable = false) private String status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected PaymentRefund() {}
    static PaymentRefund pending(Payment p, Instant now) {
        var refund = new PaymentRefund();
        refund.id = UUID.randomUUID(); refund.paymentId = p.getId();
        refund.amount = p.getAmount() - p.getRefundedAmount(); refund.status = "pending";
        refund.createdAt = now; refund.updatedAt = now;
        return refund;
    }
    /** 시리즈 결제에서 취소된 회차 하나의 몫 (CheckoutService.work) */
    static PaymentRefund pendingVisit(Payment p, UUID bookingId, long amount, Instant now) {
        var refund = pending(p, now);
        refund.bookingId = bookingId;
        refund.amount = amount;
        return refund;
    }
    void done(Instant now) { status = "done"; updatedAt = now; }
    public UUID getId() { return id; }
    public UUID getBookingId() { return bookingId; }
    public long getAmount() { return amount; }
    public Instant getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }
}
