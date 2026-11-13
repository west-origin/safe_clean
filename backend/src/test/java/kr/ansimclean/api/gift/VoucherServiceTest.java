package kr.ansimclean.api.gift;

import kr.ansimclean.api.booking.Booking;
import kr.ansimclean.api.booking.BookingService;
import kr.ansimclean.api.booking.TestBookings;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.common.web.Idempotent;
import kr.ansimclean.api.gift.dto.RefundRequest;
import kr.ansimclean.api.payment.CheckoutService;
import kr.ansimclean.api.profile.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Base64;
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
 * 금액권 사용·복원·환불 규칙 (docs/domain.md 금액권 사용·환불 규칙). SQL·예약·결제는 가짜, 암호화는 진짜 키로.
 * 실제 잠금·동시성(같은 금액권 선물 vs 예약 사용)은 VoucherIT (Docker 필요)
 */
class VoucherServiceTest {
    static final Instant NOW = Instant.parse("2026-11-13T03:00:00Z");
    final UUID me = UUID.randomUUID();
    GiftStore gifts = mock(GiftStore.class);
    VoucherStore store = mock(VoucherStore.class);
    BookingService bookings = mock(BookingService.class);
    CheckoutService checkout = mock(CheckoutService.class);
    ProfileService profiles = mock(ProfileService.class);
    Idempotent idempotent = mock(Idempotent.class);
    RefundAccountCipher cipher = new RefundAccountCipher(Base64.getEncoder().encodeToString(new byte[32]));
    VoucherService service;
    Booking booking; // 85,000원 예약

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new VoucherService(gifts, store, bookings, checkout, profiles, cipher, idempotent, new AppClock(Clock.fixed(NOW, AppClock.ZONE)));
        when(idempotent.run(any(), any(), any(), any(), any())).thenAnswer(inv -> ((Supplier<Object>) inv.getArgument(4)).get());
        booking = TestBookings.requested(me, LocalDate.of(2026, 11, 20), LocalTime.NOON);
        when(bookings.lockForUpdate(booking.getId())).thenReturn(booking);
        when(store.appliedFor(booking.getId())).thenReturn(Optional.empty());
        when(checkout.closePendingBookingPayment(booking.getId())).thenReturn("none");
    }

    private static void assertCode(Runnable r, ErrorCode code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting(e -> ((ApiException) e).code()).isEqualTo(code);
    }

    private GiftStore.VoucherRow voucher(UUID owner, UUID purchaser, long face, long balance, String status, Instant purchasedAt, Instant receivedAt, Instant validUntil) {
        var v = new GiftStore.VoucherRow(UUID.randomUUID(), owner, purchaser, "studio_once", "원룸 1회 청소", face, balance, status, true, purchasedAt, receivedAt, validUntil, purchasedAt);
        when(gifts.lockVoucher(v.id())).thenReturn(Optional.of(v));
        when(gifts.voucher(v.id())).thenReturn(Optional.of(v));
        return v;
    }

    // ── 사용 ──

    @Test
    void 잔액만큼_쓰고_잠금은_예약_금액권_결제_순서다() {
        var v = voucher(me, me, 69000, 69000, "held", NOW.minus(Duration.ofDays(10)), NOW, NOW.plus(Duration.ofDays(300)));
        service.apply(me, booking.getId(), v.id());
        var order = inOrder(bookings, gifts, checkout, store);
        order.verify(bookings).lockForUpdate(booking.getId());
        order.verify(gifts).lockVoucher(v.id());
        order.verify(checkout).closePendingBookingPayment(booking.getId());
        order.verify(store).insertUse(v.id(), booking.getId(), me, 69000, NOW);
        verify(store).setBalance(v.id(), 0, "used");
        assertThat(booking.getPaymentStatus()).isEqualTo("unpaid"); // 85,000 중 16,000 은 카드로
    }

    @Test
    void 예약_금액보다_많으면_예약_금액만_쓰고_결제가_끝난다() {
        var v = voucher(me, me, 140000, 140000, "held", NOW.minus(Duration.ofDays(10)), NOW, NOW.plus(Duration.ofDays(300)));
        service.apply(me, booking.getId(), v.id());
        verify(store).insertUse(v.id(), booking.getId(), me, 85000, NOW);
        verify(store).setBalance(v.id(), 55000, "held");
        assertThat(booking.getPaymentStatus()).isEqualTo("paid");
    }

    @Test
    void 쓸_수_없는_금액권과_예약은_막는다() {
        var others = voucher(UUID.randomUUID(), me, 69000, 69000, "held", NOW, NOW, NOW.plus(Duration.ofDays(300)));
        assertCode(() -> service.apply(me, booking.getId(), others.id()), ErrorCode.NOT_FOUND);
        for (var bad : new GiftStore.VoucherRow[] {
                voucher(me, me, 69000, 69000, "gifting", NOW, NOW, NOW.plus(Duration.ofDays(300))),
                voucher(me, me, 69000, 69000, "refund_requested", NOW, NOW, NOW.plus(Duration.ofDays(300))),
                voucher(me, me, 69000, 69000, "held", NOW, NOW, NOW.minusSeconds(1)),          // 만료
                voucher(me, me, 69000, 0, "used", NOW, NOW, NOW.plus(Duration.ofDays(300))) }) {
            assertCode(() -> service.apply(me, booking.getId(), bad.id()), ErrorCode.INVALID_STATE);
        }
        var ok = voucher(me, me, 69000, 69000, "held", NOW, NOW, NOW.plus(Duration.ofDays(300)));
        when(checkout.closePendingBookingPayment(booking.getId())).thenReturn("authorized"); // 카드 승인 요청이 이미 있다
        assertCode(() -> service.apply(me, booking.getId(), ok.id()), ErrorCode.INVALID_STATE);

        Booking someoneElses = TestBookings.requested(UUID.randomUUID(), LocalDate.of(2026, 11, 20), LocalTime.NOON);
        when(bookings.lockForUpdate(someoneElses.getId())).thenReturn(someoneElses);
        assertCode(() -> service.apply(me, someoneElses.getId(), ok.id()), ErrorCode.NOT_FOUND);
        verify(store, never()).insertUse(any(), any(), any(), anyLong(), any());
    }

    @Test
    void 예약당_한_장_같은_금액권이면_같은_결과() {
        var v = voucher(me, me, 69000, 69000, "held", NOW, NOW, NOW.plus(Duration.ofDays(300)));
        when(store.appliedFor(booking.getId())).thenReturn(Optional.of(new VoucherStore.UseRow(UUID.randomUUID(), v.id(), booking.getId(), me, 69000, "applied")));
        assertThat(service.apply(me, booking.getId(), v.id()).appliedAmount()).isEqualTo(69000);
        var other = voucher(me, me, 69000, 69000, "held", NOW, NOW, NOW.plus(Duration.ofDays(300)));
        assertCode(() -> service.apply(me, booking.getId(), other.id()), ErrorCode.INVALID_STATE);
        verify(store, never()).insertUse(any(), any(), any(), anyLong(), any());
    }

    @Test
    void 빼거나_예약이_취소되면_잔액을_돌려준다() {
        var v = voucher(me, me, 69000, 0, "used", NOW, NOW, NOW.plus(Duration.ofDays(300)));
        var use = new VoucherStore.UseRow(UUID.randomUUID(), v.id(), booking.getId(), me, 69000, "applied");
        when(store.appliedFor(booking.getId())).thenReturn(Optional.of(use));
        when(store.markRestored(eq(use.id()), any(), eq(NOW))).thenReturn(1);
        service.remove(me, booking.getId());
        verify(store).markRestored(use.id(), "removed", NOW);
        verify(store).setBalance(v.id(), 69000, "held");

        // 카드 승인 요청 뒤에는 뺄 수 없다
        when(checkout.closePendingBookingPayment(booking.getId())).thenReturn("paid");
        assertCode(() -> service.remove(me, booking.getId()), ErrorCode.INVALID_STATE);

        // 취소된 예약 → 복원 (예약이 아직 살아 있으면 아무것도 안 한다)
        when(store.use(use.id())).thenReturn(Optional.of(use));
        service.restoreCancelled(use.id());
        verify(store, never()).markRestored(use.id(), "booking_cancelled", NOW);
        booking.cancel("customer", NOW, 24);
        service.restoreCancelled(use.id());
        verify(store).markRestored(use.id(), "booking_cancelled", NOW);
    }

    // ── 환불 ──

    @Test
    void 환불_규칙표() {
        UUID friend = UUID.randomUUID();
        Instant bought = NOW.minus(Duration.ofDays(3));
        // 구매자가 갖고 있고 아무도 받지 않은 미사용, 7일 안 → 카드 100%
        var e = VoucherService.eligibility(new GiftStore.VoucherRow(UUID.randomUUID(), me, me, "k", "l", 69000, 69000, "held", true, bought, null, bought.plus(Duration.ofDays(365)), bought), NOW);
        assertThat(e.method()).isEqualTo("card");
        assertThat(e.amount()).isEqualTo(69000);
        // 받은 금액권, 7일 안 미사용 → 계좌 100%
        e = VoucherService.eligibility(new GiftStore.VoucherRow(UUID.randomUUID(), me, friend, "k", "l", 69000, 69000, "held", true, bought, NOW, NOW.plus(Duration.ofDays(365)), bought), NOW);
        assertThat(e.method()).isEqualTo("bank");
        assertThat(e.rate()).isEqualTo(100);
        // 7일 안이어도 일부 사용 → 불가
        e = VoucherService.eligibility(new GiftStore.VoucherRow(UUID.randomUUID(), me, friend, "k", "l", 69000, 30000, "held", true, bought, NOW, NOW.plus(Duration.ofDays(365)), bought), NOW);
        assertThat(e.method()).isNull();
        // 만료 ∧ 잔액 5만 원 초과 → 95% (원 단위 내림)
        Instant old = NOW.minus(Duration.ofDays(400));
        e = VoucherService.eligibility(new GiftStore.VoucherRow(UUID.randomUUID(), me, friend, "k", "l", 140000, 50001, "held", true, old, old, NOW.minusSeconds(1), old), NOW);
        assertThat(e.method()).isEqualTo("bank");
        assertThat(e.rate()).isEqualTo(95);
        assertThat(e.amount()).isEqualTo(47500); // 50001 × 0.95 = 47500.95 → 47500
        // 만료 ∧ 5만 원 이하 → 불가
        e = VoucherService.eligibility(new GiftStore.VoucherRow(UUID.randomUUID(), me, friend, "k", "l", 140000, 50000, "held", true, old, old, NOW.minusSeconds(1), old), NOW);
        assertThat(e.method()).isNull();
        // 7일 지남 ∧ 유효기간 안 → 불가
        e = VoucherService.eligibility(new GiftStore.VoucherRow(UUID.randomUUID(), me, friend, "k", "l", 69000, 69000, "held", true, NOW.minus(Duration.ofDays(8)), NOW, NOW.plus(Duration.ofDays(300)), old), NOW);
        assertThat(e.method()).isNull();
        // 선물 중·환불 접수 → 불가
        e = VoucherService.eligibility(new GiftStore.VoucherRow(UUID.randomUUID(), me, me, "k", "l", 69000, 69000, "gifting", true, bought, null, null, bought), NOW);
        assertThat(e.method()).isNull();
    }

    @Test
    void 카드_환불은_선물_금액권_결제_순서로_잠그고_바로_refunded() {
        Instant bought = NOW.minus(Duration.ofDays(2));
        var v = voucher(me, me, 69000, 69000, "held", bought, null, bought.plus(Duration.ofDays(365)));
        UUID gift = UUID.randomUUID();
        when(store.purchaseGiftOf(v.id())).thenReturn(Optional.of(gift));
        when(checkout.requestGiftRefund(gift)).thenReturn(true);
        var res = service.requestRefund(me, "k", v.id(), null);
        assertThat(res.method()).isEqualTo("card");
        assertThat(res.status()).isEqualTo("processing");
        var order = inOrder(gifts, checkout, store);
        order.verify(gifts).lockGift(gift);
        order.verify(gifts).lockVoucher(v.id());
        order.verify(checkout).requestGiftRefund(gift);
        order.verify(store).setBalance(v.id(), 0, "refunded");
        verify(store).insertRefund(v.id(), me, "card", 100, 69000, 69000, "processing", null, null, null, null, NOW);
    }

    @Test
    void 계좌_환불은_계좌번호를_암호화해_두고_사용을_막는다() {
        Instant bought = NOW.minus(Duration.ofDays(2));
        var v = voucher(me, UUID.randomUUID(), 69000, 69000, "held", bought, NOW.minus(Duration.ofDays(1)), NOW.plus(Duration.ofDays(364)));
        assertCode(() -> service.requestRefund(me, null, v.id(), new RefundRequest("국민은행", "12a", "김고객")), ErrorCode.VALIDATION);
        assertCode(() -> service.requestRefund(me, null, v.id(), new RefundRequest(null, "123456789012", "김고객")), ErrorCode.VALIDATION);
        var res = service.requestRefund(me, null, v.id(), new RefundRequest("국민은행", "123-4567-8901-23", "김고객"));
        assertThat(res.method()).isEqualTo("bank");
        assertThat(res.amount()).isEqualTo(69000);
        verify(store).setBalance(v.id(), 69000, "refund_requested");
        var cipherText = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(store).insertRefund(eq(v.id()), eq(me), eq("bank"), eq(100), eq(69000L), eq(69000L), eq("requested"), eq("국민은행"), eq("김고객"),
                cipherText.capture(), eq("0123"), eq(NOW));
        assertThat(cipherText.getValue()).doesNotContain("12345678901");
        assertThat(cipher.decrypt(cipherText.getValue())).isEqualTo("12345678901 23".replace(" ", ""));
        verify(checkout, never()).requestGiftRefund(any());
    }

    @Test
    void 암호화_키가_없으면_계좌_환불을_받지_않는다() {
        service = new VoucherService(gifts, store, bookings, checkout, profiles, new RefundAccountCipher(""), idempotent, new AppClock(Clock.fixed(NOW, AppClock.ZONE)));
        Instant bought = NOW.minus(Duration.ofDays(2));
        var v = voucher(me, UUID.randomUUID(), 69000, 69000, "held", bought, NOW, NOW.plus(Duration.ofDays(364)));
        assertCode(() -> service.requestRefund(me, null, v.id(), new RefundRequest("국민은행", "12345678901", "김고객")), ErrorCode.INVALID_STATE);
        verify(store, never()).insertRefund(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), anyLong(), anyLong(), any(), any(), any(), any(), any(), any());
    }
}
