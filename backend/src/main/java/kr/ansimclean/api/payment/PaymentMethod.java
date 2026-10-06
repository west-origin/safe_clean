package kr.ansimclean.api.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

// 고객 한 명이 여러 개 가질 수 있다. 읽는 곳: PaymentService.methods (GET /customer/payment-methods — 고객 앱 설정 › 결제 수단
// 화면이 부르지만, 지금 화면은 받은 목록과 상관없이 '연동 후 제공' 안내만 그린다). 지우는 곳: PaymentAccountPurge (탈퇴 + 5년 파기).
// 만드는 create() 를 부르는 main 코드는 아직 없다 (PG 를 붙이면 registerBillingKey 구현이 부를 자리).
/** payment_methods — PG 빌링키 (카드번호는 절대 저장하지 않는다). PG 연동 전에는 행이 생기지 않는다 (§6.4 구조만) */
@Entity
@Table(name = "payment_methods")
public class PaymentMethod {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "customer_id", columnDefinition = "uuid", nullable = false)
    private UUID customerId;

    // 결제 대행사 이름 (PG 를 정한 뒤 채운다)
    @Column(columnDefinition = "text", nullable = false)
    private String provider;

    // PG 가 준 빌링키 — 카드 정보를 PG 가 대신 보관하고 주는 키. 이 표에 카드번호는 두지 않는다 (위 설명)
    @Column(name = "billing_key", columnDefinition = "text", nullable = false)
    private String billingKey;

    // 화면에 보이는 이름 (예: "신한카드 1234")
    @Column(columnDefinition = "text", nullable = false)
    private String label;

    // 기본 결제 수단인가
    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // JPA 가 DB 행을 읽어 객체를 만들 때 쓰는 빈 생성자
    protected PaymentMethod() {}

    /** 새 결제 수단 객체 (id 는 여기서 UUID 생성, 저장은 부르는 쪽이 한다) */
    public static PaymentMethod create(UUID customerId, String provider, String billingKey, String label, boolean isDefault, Instant now) {
        PaymentMethod m = new PaymentMethod();
        m.id = UUID.randomUUID();
        m.customerId = customerId;
        m.provider = provider;
        m.billingKey = billingKey;
        m.label = label;
        m.isDefault = isDefault;
        m.createdAt = now;
        return m;
    }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public String getProvider() { return provider; }
    public String getBillingKey() { return billingKey; }
    public String getLabel() { return label; }
    public boolean isDefault() { return isDefault; }
}
