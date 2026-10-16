package kr.ansimclean.api.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;

import java.time.Instant;
import java.util.UUID;

// 만드는 곳: CheckoutService.prepare. NoopPaymentGateway의 예전 billing 골격도 이 엔티티를 사용한다.
// 고객 계정 파기(PaymentAccountPurge) 때도 이 행은 예약 기록이라 지우지 않고 결제 수단 참조(payment_method_id)만 비운다.
/** payments — 방문 회차 한 건의 PG 주문. 실패가 확정된 뒤에만 새 주문으로 재시도한다. */
@Entity
@Table(name = "payments")
public class Payment {
    @Id // 기본 키. DB 가 아니라 pending() 에서 UUID 를 만들어 넣는다
    @Column(columnDefinition = "uuid")
    private UUID id;

    // 어느 예약의 결제인가 (bookings.id)
    @Column(name = "booking_id", columnDefinition = "uuid", nullable = false)
    private UUID bookingId;

    // 쓴 결제 수단(payment_methods.id). 수단 없이 만든 행이거나 고객 계정 파기 뒤에는 null
    @Column(name = "payment_method_id", columnDefinition = "uuid")
    private UUID paymentMethodId;

    // 결제 대행사 이름. NoopPaymentGateway 는 "none" 을 넣는다
    @Column(columnDefinition = "text", nullable = false)
    private String provider;

    // PG 가 준 거래 id (DB 에서 유일). PG 연동 전에는 채우는 코드가 없어 늘 null
    @Column(name = "provider_tx_id", columnDefinition = "text")
    private String providerTxId;

    // 금액 (원 단위 정수)
    @Column(nullable = false)
    private long amount;

    // authorized = 승인 요청을 서버에 저장한 상태. PG 승인 확인 뒤에만 paid가 된다.
    @Column(columnDefinition = "text", nullable = false)
    private String status;

    // 실패했을 때 PG 가 알려 준 이유 (지금은 채우는 코드가 없다)
    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(name = "refunded_amount", nullable = false)
    private long refundedAmount;
    @Column(name = "next_check_at")
    private Instant nextCheckAt;
    @Column(name = "last_webhook_at")
    private Instant lastWebhookAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // JPA 가 DB 행을 읽어 객체를 만들 때 쓰는 빈 생성자. 코드에서 새로 만들 때는 pending() 을 쓴다
    protected Payment() {}

    /** 결제 대기 행을 새로 만든다 (아직 DB 저장 전 — 부르는 쪽이 save 한다). 만든 시각·바뀐 시각 모두 now */
    public static Payment pending(UUID bookingId, UUID paymentMethodId, String provider, long amount, Instant now) {
        Payment p = new Payment();
        p.id = UUID.randomUUID();
        p.bookingId = bookingId;
        p.paymentMethodId = paymentMethodId;
        p.provider = provider;
        p.amount = amount;
        p.status = "pending";
        p.createdAt = now;
        p.updatedAt = now;
        return p;
    }

    // 읽기 전용 getter. 상태를 바꾸는 메서드는 아직 없다
    public UUID getId() { return id; }
    public UUID getBookingId() { return bookingId; }
    public String getProvider() { return provider; }
    public long getAmount() { return amount; }
    public String getStatus() { return status; }
    public String getProviderTxId() { return providerTxId; }
    public long getRefundedAmount() { return refundedAmount; }

    /** 승인 요청을 영속화한다. 다른 키로 같은 주문을 다시 승인할 수 없다. */
    public void requestConfirmation(String key, long requestedAmount, Instant now) {
        if (requestedAmount != amount) throw ApiException.validation("결제 금액이 일치하지 않아요");
        if (providerTxId != null && !providerTxId.equals(key)) throw new ApiException(ErrorCode.INVALID_STATE);
        if (!"pending".equals(status) && !"authorized".equals(status)) return;
        providerTxId = key; status = "authorized"; updatedAt = now; nextCheckAt = now;
    }

    public void defer(Instant now) { nextCheckAt = now.plusSeconds(300); }
    public boolean acceptWebhook(Instant now) {
        if (lastWebhookAt != null && lastWebhookAt.plusSeconds(30).isAfter(now)) return false;
        lastWebhookAt = now;
        return true;
    }

    /** PG 조회 결과만 적용. 오래된 응답으로 환불을 되돌리지 않는다. */
    public void apply(TossPaymentGateway.Result result, Instant now) {
        if (result == null || !id.toString().equals(result.orderId()) || result.totalAmount() != amount
                || !"KRW".equals(result.currency()) || result.paymentKey() == null || result.paymentKey().isBlank() || result.status() == null
                || (providerTxId != null && !providerTxId.equals(result.paymentKey()))
                || result.balanceAmount() < 0 || result.balanceAmount() > amount) {
            throw new ApiException(ErrorCode.UPSTREAM);
        }
        String next = switch (result.status()) {
            case "DONE" -> "paid";
            case "PARTIAL_CANCELED" -> "partially_refunded";
            case "CANCELED" -> "refunded";
            case "ABORTED", "EXPIRED" -> "failed";
            default -> null;
        };
        if (next == null) return;
        long refunded = ("partially_refunded".equals(next) || "refunded".equals(next)) ? amount - result.balanceAmount() : 0;
        if (("refunded".equals(next) && refunded != amount)
                || ("partially_refunded".equals(next) && (refunded == 0 || refunded == amount)))
            throw new ApiException(ErrorCode.UPSTREAM);
        if (refunded < refundedAmount || ("failed".equals(next) && !"pending".equals(status) && !"authorized".equals(status))) return;
        // failed 상태가 확정된 주문은 다시 활성화하지 않는다.
        if ("failed".equals(status)) return;
        providerTxId = result.paymentKey(); status = next; refundedAmount = refunded; updatedAt = now;
        nextCheckAt = "failed".equals(next) || "refunded".equals(next) ? null : now.plusSeconds(86400);
    }
}
