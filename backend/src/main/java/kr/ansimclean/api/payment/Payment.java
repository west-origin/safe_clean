package kr.ansimclean.api.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

// 만드는 곳: PaymentGateway.charge 구현(지금은 NoopPaymentGateway). charge 를 부르는 main 코드는 아직 없다
// (부를 자리는 BookingService 의 `// PAYMENT:` 주석).
// 고객 계정 파기(PaymentAccountPurge) 때도 이 행은 예약 기록이라 지우지 않고 결제 수단 참조(payment_method_id)만 비운다.
/** payments — 결제 1건. PG 연동 전에는 NoopPaymentGateway 가 pending 행만 만든다 */
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

    // DB check 제약이 pending·authorized·paid·failed·refunded 만 허용한다. 지금 코드는 pending 만 만든다
    @Column(columnDefinition = "text", nullable = false)
    private String status;

    // 실패했을 때 PG 가 알려 준 이유 (지금은 채우는 코드가 없다)
    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

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
}
