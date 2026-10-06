package kr.ansimclean.api.payment;

import java.util.UUID;

/**
 * 결제 대행(PG) 경계 — **구조만** (사용자 결정 §13-5: PG 는 나중에 직접 붙인다).
 * 구현체를 하나 더 만들고 @Primary 를 붙이면 NoopPaymentGateway 대신 쓰인다. 호출 지점은 BookingService 의 `// PAYMENT:` 주석.
 * 지금 구현체는 NoopPaymentGateway(PG 연동 전 흉내)이고, main 코드에서 이 인터페이스를 주입받아 부르는 곳은 아직 없다.
 * 금액(amount)은 모두 원 단위 정수(long)다.
 */
public interface PaymentGateway {
    /** PG 결제창에서 받은 페이로드 → 빌링키 등록 */
    PaymentMethod registerBillingKey(UUID customerId, String providerPayload); // 빌링키 = PG 가 카드 정보를 대신 보관하고 주는 키. 다음 결제는 이 키로 요청한다

    /** 예약 금액 청구 (예약 스냅샷 total_amount) */
    Payment charge(UUID bookingId, long amount, PaymentMethod method);

    /** 부분·전액 환불 */
    void refund(Payment payment, long amount);
}
