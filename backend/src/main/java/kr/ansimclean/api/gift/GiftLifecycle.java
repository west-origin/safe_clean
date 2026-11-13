package kr.ansimclean.api.gift;

import kr.ansimclean.api.catalog.AppSettingsService;
import kr.ansimclean.api.common.events.GiftReceived;
import kr.ansimclean.api.common.outbox.OutboxService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 선물·금액권의 상태 전이 몇 가지를 한 곳에 둔다 — GiftService(보유 금액권 선물·반환)와 GiftPaymentBridge(결제 확정 뒤 보내기)가 같이 쓴다.
 * 부르는 쪽이 선물 → 금액권 순서로 잠근 트랜잭션 안에서만 부른다
 */
@Component
public class GiftLifecycle {
    static final String ACCEPT_DAYS = "gift_accept_days";
    static final String VALID_DAYS = "voucher_valid_days";

    private final GiftStore store;
    private final OutboxService outbox;
    private final AppSettingsService settings;

    public GiftLifecycle(GiftStore store, OutboxService outbox, AppSettingsService settings) {
        this.store = store;
        this.outbox = outbox;
        this.settings = settings;
    }

    /** 받는 사람에게 보낸다: 선물 sent + 받는 마감(지금 + gift_accept_days) + 받는 사람 푸시. 금액권은 부르는 쪽이 gifting 으로 바꿔 둔다 */
    void send(GiftStore.GiftRow g, Instant now) {
        store.markSent(g.id(), now, now.plus(Duration.ofDays(settings.integer(ACCEPT_DAYS))));
        outbox.publish(new GiftReceived(g.id(), g.recipientId()));
    }

    /**
     * 금액권을 보낸 사람에게 돌려준다(거절·만료·취소). 아무도 받은 적 없어 유효기간이 없으면 구매일 + 유효기간(1년)으로 정한다 —
     * 받지 않은 금액권이 영원히 남지 않게 (코드에서 정함)
     */
    void returnToSender(GiftStore.GiftRow g, GiftStore.VoucherRow v, Instant now) {
        Instant validUntil = v.validUntil() != null ? v.validUntil()
                : (v.purchasedAt() != null ? v.purchasedAt() : now).plus(Duration.ofDays(settings.integer(VALID_DAYS)));
        store.hold(v.id(), g.senderId(), validUntil, null);
    }

    /** 받는 마감 기간(일) — 보유 금액권 선물은 유효기간이 이보다 많이 남아야 보낼 수 있다 */
    int acceptDays() {
        return settings.integer(ACCEPT_DAYS);
    }

    int validDays() {
        return settings.integer(VALID_DAYS);
    }
}
