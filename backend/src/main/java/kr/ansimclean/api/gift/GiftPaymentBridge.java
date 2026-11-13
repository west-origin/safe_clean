package kr.ansimclean.api.gift;

import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.payment.GiftPaymentHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * payment.CheckoutService 가 금액권 선물 결제를 처리할 때 부르는 곳 (GiftPaymentHooks 구현).
 * GiftService 가 아니라 따로 두는 이유: GiftService 는 CheckoutService 를 부르고 CheckoutService 는 이 클래스를 부른다 — 한 클래스면 빈이 서로를 기다린다.
 * 모든 메서드는 CheckoutService 의 트랜잭션 안에서 돈다. 잠금 순서 선물 → 금액권 → 결제
 */
@Component
public class GiftPaymentBridge implements GiftPaymentHooks {
    private static final Logger log = LoggerFactory.getLogger(GiftPaymentBridge.class);
    private final GiftStore store;
    private final VoucherStore vouchers;
    private final GiftLifecycle lifecycle;
    private final AppClock clock;

    public GiftPaymentBridge(GiftStore store, VoucherStore vouchers, GiftLifecycle lifecycle, AppClock clock) {
        this.store = store;
        this.vouchers = vouchers;
        this.lifecycle = lifecycle;
        this.clock = clock;
    }

    @Override
    public long voucherCredit(UUID bookingId) {
        return vouchers.creditFor(bookingId);
    }

    @Override
    public Optional<UUID> lockForPayment(UUID giftId) {
        return store.lockGift(giftId).map(g -> {
            store.lockVoucher(g.voucherId());
            return g.senderId();
        });
    }

    @Override
    public boolean payable(UUID giftId) {
        return store.gift(giftId).map(g -> GiftService.AWAITING_PAYMENT.equals(g.status())).orElse(false);
    }

    @Override
    public void paymentChanged(UUID giftId, String paymentStatus) {
        GiftStore.GiftRow g = store.gift(giftId).orElseThrow();
        boolean awaiting = GiftService.AWAITING_PAYMENT.equals(g.status());
        if ("paid".equals(paymentStatus)) {
            if (!awaiting) {
                // 결제 전 취소·정리 뒤에 승인이 확정된 경우 — 승인 요청 뒤에는 취소를 막으므로 생기면 안 된다. 환불은 3회차(구매 7일 안 카드 취소)에서 다룬다
                if (!GiftService.SENT.equals(g.status()) && !"accepted".equals(g.status()))
                    log.warn("선물 결제가 확정됐지만 선물 상태가 결제 대기가 아님 giftId={} status={}", giftId, g.status());
                return;
            }
            store.markPaidGifting(g.voucherId(), clock.now());
            lifecycle.send(g, clock.now());
        } else if ("refunded".equals(paymentStatus)) {
            // 구매 7일 안 카드 결제 취소가 PG 에서 확인됨 (금액권은 접수 때 이미 refunded)
            vouchers.cardRefundDone(g.voucherId(), clock.now());
        } else if ("failed".equals(paymentStatus) && awaiting) {
            store.decide(giftId, "payment_failed", clock.now());
            store.setStatus(g.voucherId(), "void");
        }
    }
}
