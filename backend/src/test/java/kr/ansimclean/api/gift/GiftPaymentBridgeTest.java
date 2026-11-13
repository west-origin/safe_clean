package kr.ansimclean.api.gift;

import kr.ansimclean.api.catalog.AppSettingsService;
import kr.ansimclean.api.common.events.GiftReceived;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.time.AppClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 결제 쪽이 선물에 알리는 곳: 결제 확정 = 선물 보내기(같은 트랜잭션), 결제 실패 = 선물 실패·금액권 무효, 잠금 순서 선물 → 금액권 */
class GiftPaymentBridgeTest {
    static final Instant NOW = Instant.parse("2026-11-13T03:00:00Z");
    GiftStore store = mock(GiftStore.class);
    OutboxService outbox = mock(OutboxService.class);
    AppSettingsService settings = mock(AppSettingsService.class);
    GiftPaymentBridge bridge;
    final UUID sender = UUID.randomUUID();
    final UUID recipient = UUID.randomUUID();
    final UUID voucher = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(settings.integer("gift_accept_days")).thenReturn(7);
        bridge = new GiftPaymentBridge(store, new GiftLifecycle(store, outbox, settings), new AppClock(Clock.fixed(NOW, AppClock.ZONE)));
    }

    private GiftStore.GiftRow gift(String status) {
        var g = new GiftStore.GiftRow(UUID.randomUUID(), sender, recipient, voucher, "purchase", null, 69000, "원룸", status, NOW, null, null, null);
        when(store.gift(g.id())).thenReturn(Optional.of(g));
        when(store.lockGift(g.id())).thenReturn(Optional.of(g));
        return g;
    }

    @Test
    void 잠금은_선물_다음_금액권_결제한_사람을_돌려준다() {
        var g = gift("awaiting_payment");
        assertThat(bridge.lockForPayment(g.id())).contains(sender);
        var order = inOrder(store);
        order.verify(store).lockGift(g.id());
        order.verify(store).lockVoucher(voucher);
        assertThat(bridge.lockForPayment(UUID.randomUUID())).isEmpty();
        assertThat(bridge.payable(g.id())).isTrue();
        assertThat(bridge.payable(gift("cancelled").id())).isFalse();
    }

    @Test
    void 결제가_확정되면_금액권을_잠그고_받는_사람에게_보낸다() {
        var g = gift("awaiting_payment");
        bridge.paymentChanged(g.id(), "paid");
        verify(store).markPaidGifting(voucher, NOW);
        verify(store).markSent(g.id(), NOW, NOW.plus(Duration.ofDays(7)));
        verify(outbox).publish(new GiftReceived(g.id(), recipient));
    }

    @Test
    void 결제_실패면_선물_실패와_금액권_무효_그_밖의_상태는_무시() {
        var g = gift("awaiting_payment");
        bridge.paymentChanged(g.id(), "authorized");
        verify(store, never()).decide(any(), any(), any());
        bridge.paymentChanged(g.id(), "failed");
        verify(store).decide(g.id(), "payment_failed", NOW);
        verify(store).setStatus(voucher, "void");
        verify(outbox, never()).publish(any());
    }

    @Test
    void 이미_정리된_선물의_결제_확정은_다시_보내지_않는다() {
        var g = gift("cancelled");
        bridge.paymentChanged(g.id(), "paid");
        verify(store, never()).markSent(any(), any(), any());
        verify(outbox, never()).publish(any());
    }
}
