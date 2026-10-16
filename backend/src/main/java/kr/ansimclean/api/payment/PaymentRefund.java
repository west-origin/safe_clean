package kr.ansimclean.api.payment;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** 예약 취소의 전체 잔액 환불 한 건. 재시도 내내 같은 id를 PG 멱등키로 쓴다. */
@Entity
@Table(name = "payment_refunds")
public class PaymentRefund {
    @Id private UUID id;
    @Column(name = "payment_id", nullable = false) private UUID paymentId;
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
    void done(Instant now) { status = "done"; updatedAt = now; }
    public UUID getId() { return id; }
    public String getStatus() { return status; }
}
