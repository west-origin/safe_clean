package kr.ansimclean.api.payment;

import java.util.Optional;
import java.util.UUID;

/**
 * 금액권 선물 결제(payments.gift_id)에서 결제 쪽이 선물 쪽에 묻고 알리는 곳. 구현은 gift.GiftPaymentBridge 하나.
 * payment 가 gift 패키지를 직접 알지 않게 인터페이스로 둔다 (AccountPurge 와 같은 방식 — 패키지 순환 없음).
 * 잠금 순서: 선물 → 금액권 → 결제. CheckoutService 는 결제 행을 잠그기 전에 lockForPayment 를 먼저 부른다.
 */
public interface GiftPaymentHooks {
    /** 선물 행(과 그 금액권)을 FOR UPDATE 로 잠그고 결제한 고객(보낸 사람) id. 선물이 없으면 empty */
    Optional<UUID> lockForPayment(UUID giftId);

    /** 아직 결제를 진행해도 되는 선물인가 (결제 대기 상태) — 취소·정리된 선물은 승인 요청을 받지 않는다 */
    boolean payable(UUID giftId);

    /**
     * 결제 상태가 바뀐 직후, 같은 트랜잭션·같은 잠금 안에서. paid 면 선물을 보내고(금액권 gifting, 받는 사람 푸시),
     * failed 면 선물 payment_failed·금액권 void. 그 밖의 상태는 무시해도 된다
     */
    void paymentChanged(UUID giftId, String paymentStatus);

    /** 이 예약 결제에 쓰는 중인 금액권 금액(원, 없으면 0) — 카드 결제 금액 = 예약 금액 − 이 값 (3회차) */
    long voucherCredit(UUID bookingId);

    /**
     * 이 회차 금액 중 금액권이 낸 몫(원) — 쓰는 중이거나, 회차 취소로 금액권에 돌려준 몫까지 (빼기로 되돌린 것은 아니다).
     * 정기 요금제 시리즈 결제에서 취소된 회차의 카드 환불 = 회차 금액 − 할인 − 이 값 (2026-11-14, 금액권은 회차 금액 비율로 나눠 쓴다)
     */
    long voucherShare(UUID bookingId);
}
