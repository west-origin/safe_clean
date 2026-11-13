package kr.ansimclean.api.gift;

import kr.ansimclean.api.catalog.AppSettingsService;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.events.GiftReceived;
import kr.ansimclean.api.common.events.GiftResolved;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.common.web.Idempotent;
import kr.ansimclean.api.common.web.RateLimiter;
import kr.ansimclean.api.friend.FriendService;
import kr.ansimclean.api.gift.dto.CreateGiftRequest;
import kr.ansimclean.api.payment.CheckoutService;
import kr.ansimclean.api.profile.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GiftService 규칙 (docs/domain.md 금액권·선물 규칙). GiftStore(SQL)·결제·친구는 가짜, GiftLifecycle 은 진짜(설정만 가짜: 받는 기간 7일·유효기간 365일).
 * 실제 SQL·잠금·동시성은 GiftIT (Docker 필요)
 */
class GiftServiceTest {
    static final Instant NOW = Instant.parse("2026-11-13T03:00:00Z");
    final UUID me = UUID.randomUUID();
    final UUID friend = UUID.randomUUID();

    GiftStore store = mock(GiftStore.class);
    FriendService friends = mock(FriendService.class);
    CheckoutService checkout = mock(CheckoutService.class);
    ProfileService profiles = mock(ProfileService.class);
    AppSettingsService settings = mock(AppSettingsService.class);
    OutboxService outbox = mock(OutboxService.class);
    Idempotent idempotent = mock(Idempotent.class);
    RateLimiter rateLimiter = mock(RateLimiter.class);
    GiftService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(settings.integer("gift_accept_days")).thenReturn(7);
        when(settings.integer("voucher_valid_days")).thenReturn(365);
        when(settings.node("gift_catalog")).thenReturn(JsonMapper.builder().build().readTree("""
                [{"key":"studio_once","group":"원룸 정기청소","label":"원룸 1회 청소 (1회 2시간)","amount":69000}]"""));
        GiftLifecycle lifecycle = new GiftLifecycle(store, outbox, settings);
        service = new GiftService(store, lifecycle, friends, checkout, profiles, settings, outbox, idempotent, rateLimiter, new AppClock(Clock.fixed(NOW, AppClock.ZONE)));
        when(idempotent.run(any(), any(), any(), any(), any())).thenAnswer(inv -> ((Supplier<Object>) inv.getArgument(4)).get());
        when(friends.lockFriendship(me, friend)).thenReturn(true);
        when(friends.areFriends(any(), any())).thenReturn(true);
        when(store.giftWithPerson(any(), anyBoolean())).thenAnswer(inv -> store.gift(inv.getArgument(0))
                .map(g -> new GiftStore.GiftWithPerson(g, friend, "친구", "0420", "김친구", false)));
    }

    private static boolean anyBoolean() {
        return org.mockito.ArgumentMatchers.anyBoolean();
    }

    private static void assertCode(Runnable r, ErrorCode code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting(e -> ((ApiException) e).code()).isEqualTo(code);
    }

    private GiftStore.VoucherRow voucher(UUID owner, long face, long balance, String status, boolean transferable, Instant validUntil) {
        return new GiftStore.VoucherRow(UUID.randomUUID(), owner, owner, "studio_once", "원룸 1회 청소 (1회 2시간)", face, balance, status, transferable,
                NOW.minus(Duration.ofDays(3)), null, validUntil, NOW.minus(Duration.ofDays(3)));
    }

    private GiftStore.GiftRow gift(UUID voucherId, String status, Instant expiresAt) {
        GiftStore.GiftRow g = new GiftStore.GiftRow(UUID.randomUUID(), me, friend, voucherId, "purchase", null, 69000, "원룸 1회 청소 (1회 2시간)",
                status, NOW.minus(Duration.ofDays(1)), NOW.minus(Duration.ofDays(1)), expiresAt, null);
        when(store.gift(g.id())).thenReturn(Optional.of(g));
        when(store.lockGift(g.id())).thenReturn(Optional.of(g));
        return g;
    }

    // ── 보내기 ──

    @Test
    void 새로_구매하면_금액권_선물_결제_주문을_서버_금액으로_만든다() {
        UUID voucherId = UUID.randomUUID();
        UUID giftId = UUID.randomUUID();
        when(store.insertUnpaidVoucher(me, "studio_once", "원룸 1회 청소 (1회 2시간)", 69000, NOW)).thenReturn(voucherId);
        when(store.insertGift(me, friend, voucherId, "purchase", "고마워", 69000, "원룸 1회 청소 (1회 2시간)", "awaiting_payment", NOW, null, null)).thenReturn(giftId);
        when(store.gift(giftId)).thenReturn(Optional.of(new GiftStore.GiftRow(giftId, me, friend, voucherId, "purchase", "고마워", 69000, "원룸 1회 청소 (1회 2시간)",
                "awaiting_payment", NOW, null, null, null)));
        var res = service.create(me, "k", new CreateGiftRequest(friend, "purchase", "studio_once", null, " 고마워 "));
        assertThat(res.gift().status()).isEqualTo("awaiting_payment");
        assertThat(res.gift().amountLabel()).isEqualTo("69,000원");
        var order = inOrder(friends, store, checkout);
        order.verify(friends).lockFriendship(me, friend);
        order.verify(store).insertUnpaidVoucher(me, "studio_once", "원룸 1회 청소 (1회 2시간)", 69000, NOW);
        order.verify(checkout).prepareGift(giftId, 69000);
        verify(outbox, never()).publish(any()); // 결제가 확정돼야 받는 사람에게 간다
    }

    @Test
    void 친구가_아니거나_나_자신이거나_목록에_없는_상품이면_보내지_않는다() {
        UUID stranger = UUID.randomUUID();
        when(friends.lockFriendship(me, stranger)).thenReturn(false);
        assertCode(() -> service.create(me, null, new CreateGiftRequest(stranger, "purchase", "studio_once", null, null)), ErrorCode.INVALID_STATE);
        assertCode(() -> service.create(me, null, new CreateGiftRequest(me, "purchase", "studio_once", null, null)), ErrorCode.VALIDATION);
        assertCode(() -> service.create(me, null, new CreateGiftRequest(friend, "purchase", "first_trial", null, null)), ErrorCode.VALIDATION);
        assertCode(() -> service.create(me, null, new CreateGiftRequest(friend, "cash", null, null, null)), ErrorCode.VALIDATION);
        verify(store, never()).insertUnpaidVoucher(any(), any(), any(), anyLong(), any());
        verify(checkout, never()).prepareGift(any(), anyLong());
    }

    @Test
    void 보유_금액권은_미사용_양도가능_기간이_넉넉할_때만_잠그고_바로_보낸다() {
        var ok = voucher(me, 69000, 69000, "held", true, NOW.plus(Duration.ofDays(200)));
        when(store.lockVoucher(ok.id())).thenReturn(Optional.of(ok));
        UUID giftId = UUID.randomUUID();
        when(store.insertGift(eq(me), eq(friend), eq(ok.id()), eq("voucher"), any(), eq(69000L), any(), eq("awaiting_payment"), eq(NOW), any(), any())).thenReturn(giftId);
        when(store.gift(giftId)).thenReturn(Optional.of(new GiftStore.GiftRow(giftId, me, friend, ok.id(), "voucher", null, 69000, "원룸", "sent", NOW, NOW, NOW.plus(Duration.ofDays(7)), null)));
        var res = service.create(me, null, new CreateGiftRequest(friend, "voucher", null, ok.id(), null));
        assertThat(res.checkout()).isNull();
        var order = inOrder(friends, store);
        order.verify(friends).lockFriendship(me, friend);
        order.verify(store).lockVoucher(ok.id());
        order.verify(store).setStatus(ok.id(), "gifting");
        verify(store).markSent(giftId, NOW, NOW.plus(Duration.ofDays(7)));
        verify(outbox).publish(new GiftReceived(giftId, friend));

        for (var bad : new GiftStore.VoucherRow[] {
                voucher(me, 69000, 30000, "held", true, NOW.plus(Duration.ofDays(200))),   // 일부 사용
                voucher(me, 69000, 69000, "gifting", true, NOW.plus(Duration.ofDays(200))), // 이미 선물 중
                voucher(me, 69000, 69000, "held", false, NOW.plus(Duration.ofDays(200))),  // 양도 불가(프로모션)
                voucher(me, 69000, 69000, "held", true, NOW.plus(Duration.ofDays(3))),     // 받는 기간보다 유효기간이 짧다
                voucher(me, 69000, 69000, "held", true, NOW.minus(Duration.ofDays(1))) }) { // 만료
            when(store.lockVoucher(bad.id())).thenReturn(Optional.of(bad));
            assertCode(() -> service.create(me, null, new CreateGiftRequest(friend, "voucher", null, bad.id(), null)), ErrorCode.INVALID_STATE);
        }
        var others = voucher(UUID.randomUUID(), 69000, 69000, "held", true, null);
        when(store.lockVoucher(others.id())).thenReturn(Optional.of(others));
        assertCode(() -> service.create(me, null, new CreateGiftRequest(friend, "voucher", null, others.id(), null)), ErrorCode.NOT_FOUND);
    }

    @Test
    void 금액권_목록은_선물_가능과_임박_경고를_알려준다() {
        when(store.vouchersOf(me)).thenReturn(java.util.List.of(
                voucher(me, 69000, 69000, "held", true, NOW.plus(Duration.ofDays(20))),
                voucher(me, 69000, 69000, "held", true, NOW.plus(Duration.ofDays(5)))));
        var list = service.vouchers(me).vouchers();
        assertThat(list.get(0).giftable()).isTrue();
        assertThat(list.get(0).expiringSoon()).isTrue();
        assertThat(list.get(1).giftable()).isFalse();
        assertThat(list.get(1).blockedReason()).contains("유효기간");
    }

    // ── 받기·거절·취소·만료 ──

    @Test
    void 받으면_소유자가_바뀌고_받은_날부터_1년_보낸_사람에게_알린다() {
        var v = voucher(me, 69000, 69000, "gifting", true, null);
        var g = gift(v.id(), "sent", NOW.plus(Duration.ofDays(2)));
        when(store.lockVoucher(v.id())).thenReturn(Optional.of(v));
        service.accept(friend, g.id());
        var order = inOrder(store);
        order.verify(store).lockGift(g.id());
        order.verify(store).lockVoucher(v.id());
        order.verify(store).hold(v.id(), friend, NOW.plus(Duration.ofDays(365)), NOW);
        verify(store).decide(g.id(), "accepted", NOW);
        verify(outbox).publish(new GiftResolved(g.id(), me, "accepted"));
    }

    @Test
    void 받기는_받는_사람만_기간_안에_친구일_때만() {
        var v = voucher(me, 69000, 69000, "gifting", true, null);
        var g = gift(v.id(), "sent", NOW.plus(Duration.ofDays(2)));
        assertCode(() -> service.accept(me, g.id()), ErrorCode.NOT_FOUND); // 보낸 사람이 받기를 누름
        assertCode(() -> service.accept(UUID.randomUUID(), g.id()), ErrorCode.NOT_FOUND);
        when(friends.areFriends(friend, me)).thenReturn(false);
        assertCode(() -> service.accept(friend, g.id()), ErrorCode.INVALID_STATE);

        var late = gift(v.id(), "sent", NOW.minusSeconds(1));
        assertCode(() -> service.accept(friend, late.id()), ErrorCode.INVALID_STATE);
        var unpaid = gift(v.id(), "awaiting_payment", null);
        assertCode(() -> service.accept(friend, unpaid.id()), ErrorCode.NOT_FOUND); // 결제 전 선물은 받는 사람에게 없는 것
        var cancelled = gift(v.id(), "cancelled", null);
        assertCode(() -> service.accept(friend, cancelled.id()), ErrorCode.INVALID_STATE);
        verify(store, never()).hold(any(), eq(friend), any(), any());
    }

    @Test
    void 다시_선물한_금액권은_유효기간을_늘리지_않는다() {
        Instant original = NOW.plus(Duration.ofDays(100));
        var v = voucher(me, 69000, 69000, "gifting", true, original);
        var g = gift(v.id(), "sent", NOW.plus(Duration.ofDays(2)));
        when(store.lockVoucher(v.id())).thenReturn(Optional.of(v));
        service.accept(friend, g.id());
        verify(store).hold(v.id(), friend, original, NOW);
    }

    @Test
    void 거절과_만료는_보낸_사람에게_돌려주고_알리고_취소는_알리지_않는다() {
        var v = voucher(me, 69000, 69000, "gifting", true, null);
        when(store.lockVoucher(v.id())).thenReturn(Optional.of(v));
        Instant validFromPurchase = v.purchasedAt().plus(Duration.ofDays(365));

        var rejected = gift(v.id(), "sent", NOW.plus(Duration.ofDays(2)));
        service.reject(friend, rejected.id());
        verify(store).decide(rejected.id(), "rejected", NOW);
        verify(outbox).publish(new GiftResolved(rejected.id(), me, "rejected"));

        var expiring = gift(v.id(), "sent", NOW.minusSeconds(1));
        service.expire(expiring.id());
        verify(store).decide(expiring.id(), "expired", NOW);
        verify(outbox).publish(new GiftResolved(expiring.id(), me, "expired"));

        var notYet = gift(v.id(), "sent", NOW.plusSeconds(60));
        service.expire(notYet.id());
        verify(store, never()).decide(eq(notYet.id()), any(), any());

        var cancelled = gift(v.id(), "sent", NOW.plus(Duration.ofDays(2)));
        assertCode(() -> service.cancel(friend, cancelled.id()), ErrorCode.NOT_FOUND); // 받는 사람은 취소 못 함
        service.cancel(me, cancelled.id());
        verify(store).decide(cancelled.id(), "cancelled", NOW);
        verify(outbox, never()).publish(new GiftResolved(cancelled.id(), me, "cancelled"));
        verify(store, org.mockito.Mockito.times(3)).hold(v.id(), me, validFromPurchase, null);
    }

    @Test
    void 결제_대기_선물_취소는_승인_요청_전에만_금액권은_무효() {
        var v = voucher(me, 69000, 69000, "unpaid", true, null);
        var g = gift(v.id(), "awaiting_payment", null);
        when(checkout.giftPaymentStatus(g.id())).thenReturn(Optional.of("authorized"));
        assertCode(() -> service.cancel(me, g.id()), ErrorCode.INVALID_STATE);
        when(checkout.giftPaymentStatus(g.id())).thenReturn(Optional.of("pending"));
        service.cancel(me, g.id());
        verify(store).decide(g.id(), "cancelled", NOW);
        verify(store).setStatus(v.id(), "void");
    }

    @Test
    void 결제창을_닫고_1시간_지난_선물만_정리한다() {
        var v = voucher(me, 69000, 69000, "unpaid", true, null);
        var fresh = new GiftStore.GiftRow(UUID.randomUUID(), me, friend, v.id(), "purchase", null, 69000, "원룸", "awaiting_payment", NOW.minusSeconds(600), null, null, null);
        when(store.lockGift(fresh.id())).thenReturn(Optional.of(fresh));
        service.abandonIfUnpaid(fresh.id());
        verify(store, never()).decide(eq(fresh.id()), any(), any());

        var old = new GiftStore.GiftRow(UUID.randomUUID(), me, friend, v.id(), "purchase", null, 69000, "원룸", "awaiting_payment", NOW.minus(Duration.ofHours(2)), null, null, null);
        when(store.lockGift(old.id())).thenReturn(Optional.of(old));
        when(checkout.giftPaymentStatus(old.id())).thenReturn(Optional.of("authorized")); // 승인 요청이 있었다 — 결제 재조정이 정한다
        service.abandonIfUnpaid(old.id());
        verify(store, never()).decide(eq(old.id()), any(), any());
        when(checkout.giftPaymentStatus(old.id())).thenReturn(Optional.of("pending"));
        service.abandonIfUnpaid(old.id());
        verify(store).decide(old.id(), "cancelled", NOW);
        verify(store).setStatus(v.id(), "void");
    }
}
