package kr.ansimclean.api.gift;

import kr.ansimclean.api.booking.Booking;
import kr.ansimclean.api.booking.BookingService;
import kr.ansimclean.api.booking.BookingStatus;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.format.KoreanFormat;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.common.web.Idempotent;
import kr.ansimclean.api.gift.dto.BookingVoucherResponse;
import kr.ansimclean.api.gift.dto.RefundQuoteResponse;
import kr.ansimclean.api.gift.dto.RefundRequest;
import kr.ansimclean.api.gift.dto.RefundResultResponse;
import kr.ansimclean.api.payment.CheckoutService;
import kr.ansimclean.api.profile.ProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 금액권 사용(예약 결제)·복원(예약 취소)·환불 (docs/domain.md 금액권 사용·환불 규칙). 모든 고객 메서드는 토큰의 고객 id(me) 기준.
 * 잠금 순서: 예약 → 금액권 → 결제, 환불은 (선물) → 금액권 → 결제·환불 행. 남의 예약·금액권은 NOT_FOUND
 */
@Service
@Transactional
public class VoucherService {
    /** 구매 후 이 기간 안이면 미사용 금액권을 100% 환불 */
    static final Duration FULL_REFUND_WINDOW = Duration.ofDays(7);
    /** 유효기간이 지난 뒤 잔액이 이 금액을 넘으면 95% 환불 */
    static final long EXPIRED_REFUND_MIN = 50_000;
    static final int EXPIRED_REFUND_RATE = 95;

    private final GiftStore gifts;
    private final VoucherStore store;
    private final BookingService bookings;
    private final CheckoutService checkout;
    private final ProfileService profiles;
    private final RefundAccountCipher cipher;
    private final Idempotent idempotent;
    private final AppClock clock;

    public VoucherService(GiftStore gifts, VoucherStore store, BookingService bookings, CheckoutService checkout, ProfileService profiles,
                          RefundAccountCipher cipher, Idempotent idempotent, AppClock clock) {
        this.gifts = gifts;
        this.store = store;
        this.bookings = bookings;
        this.checkout = checkout;
        this.profiles = profiles;
        this.cipher = cipher;
        this.idempotent = idempotent;
        this.clock = clock;
    }

    // ── 예약 결제에 쓰기 ─────────────────────────────────────────────

    /** 이 예약 결제에 쓰는 금액권 (결제 화면) */
    public BookingVoucherResponse current(UUID me, UUID bookingId) {
        return response(ownBooking(me, bookingId));
    }

    /**
     * 예약 결제에 금액권을 쓴다 (예약당 1장). 카드 결제 승인 요청 전에만 — 결제 대기 주문은 닫고, 다음 결제는 줄어든 금액으로.
     * 같은 금액권을 다시 보내면 같은 결과. 선물 보내기와 같은 금액권 행 잠금에서 만나 둘 중 하나만 된다
     */
    public BookingVoucherResponse apply(UUID me, UUID bookingId, UUID voucherId) {
        profiles.holdLive(me);
        Booking b = ownBooking(me, bookingId);
        if (b.getStatus() == BookingStatus.CANCELLED) throw new ApiException(ErrorCode.INVALID_STATE, "취소된 예약에는 금액권을 쓸 수 없어요");
        // 주 N회 요금제는 한 달치를 시리즈로 결제한다 — 회차 금액권과 시리즈 결제를 함께 계산하는 일은 다음 회차 (요금표 전환 2026-11-13)
        if (b.isWeeklyPlan()) throw new ApiException(ErrorCode.INVALID_STATE, WEEKLY_PLAN_BLOCKED);
        Optional<VoucherStore.UseRow> existing = store.appliedFor(bookingId);
        if (existing.isPresent()) {
            if (existing.get().voucherId().equals(voucherId)) return response(b);
            throw new ApiException(ErrorCode.INVALID_STATE, "이 예약에는 이미 금액권을 썼어요. 먼저 빼고 다시 골라주세요");
        }
        Instant now = clock.now();
        GiftStore.VoucherRow v = gifts.lockVoucher(voucherId).filter(x -> x.ownerId().equals(me)).orElseThrow(ApiException::notFound);
        String blocked = useBlockReason(v, now);
        if (blocked != null) throw new ApiException(ErrorCode.INVALID_STATE, blocked);
        if (!"none".equals(checkout.closePendingBookingPayment(bookingId)))
            throw new ApiException(ErrorCode.INVALID_STATE, "카드 결제를 진행한 예약에는 금액권을 쓸 수 없어요");
        if (b.getTotalAmount() <= 0) throw new ApiException(ErrorCode.INVALID_STATE, "결제할 금액이 없는 예약이에요");
        long amount = Math.min(v.balance(), b.getTotalAmount());
        store.insertUse(v.id(), bookingId, me, amount, now);
        long left = v.balance() - amount;
        store.setBalance(v.id(), left, left == 0 ? "used" : "held");
        if (amount == b.getTotalAmount()) b.recordPayment("paid"); // 금액권이 전부 덮었다 — 카드 결제 없음
        return response(b);
    }

    /** 금액권 빼기 — 카드 결제 승인 요청 전에만. 잔액을 돌려주고 결제 대기 주문은 닫는다 */
    public BookingVoucherResponse remove(UUID me, UUID bookingId) {
        profiles.holdLive(me);
        Booking b = ownBooking(me, bookingId);
        Optional<VoucherStore.UseRow> use = store.appliedFor(bookingId);
        if (use.isEmpty()) return response(b);
        GiftStore.VoucherRow v = gifts.lockVoucher(use.get().voucherId()).orElseThrow();
        if (!"none".equals(checkout.closePendingBookingPayment(bookingId)))
            throw new ApiException(ErrorCode.INVALID_STATE, "카드 결제를 진행한 뒤에는 금액권을 뺄 수 없어요");
        restore(use.get(), v, "removed");
        if (use.get().amount() == b.getTotalAmount()) b.recordPayment("unpaid");
        return response(b);
    }

    static final String WEEKLY_PLAN_BLOCKED = "정기 요금제 예약에는 금액권을 아직 쓸 수 없어요";

    /** 사용할 수 없는 이유 (쓸 수 있으면 null). 문구는 코드에서 정함 */
    static String useBlockReason(GiftStore.VoucherRow v, Instant now) {
        return switch (v.status()) {
            case "held" -> {
                if (v.validUntil() == null || !v.validUntil().isAfter(now)) yield "유효기간이 지난 금액권이에요";
                if (v.balance() <= 0) yield "남은 금액이 없는 금액권이에요";
                yield null;
            }
            case "gifting" -> "선물 중인 금액권은 쓸 수 없어요";
            case "refund_requested" -> "환불을 접수한 금액권은 쓸 수 없어요";
            default -> "쓸 수 없는 금액권이에요";
        };
    }

    /** 사용 원장을 되돌리고 잔액을 돌려준다. 이미 되돌렸으면 아무것도 안 한다 */
    private void restore(VoucherStore.UseRow use, GiftStore.VoucherRow v, String reason) {
        if (store.markRestored(use.id(), reason, clock.now()) == 0) return;
        // 환불이 끝난 금액권(만료 95% 환불)으로 돌아오는 금액은 새 잔액이 된다. 환불 접수 중이면 상태를 그대로 둔다
        long base = "refunded".equals(v.status()) ? 0 : v.balance();
        long balance = Math.min(v.faceAmount(), base + use.amount());
        store.setBalance(v.id(), balance, "refund_requested".equals(v.status()) ? "refund_requested" : "held");
    }

    /** 예약이 취소돼 남은 사용을 복원 (VoucherRestorer 가 한 건씩, 각자 트랜잭션). 예약 → 금액권 순서로 잠근다 */
    public void restoreCancelled(UUID useId) {
        VoucherStore.UseRow use = store.use(useId).orElse(null);
        if (use == null || !"applied".equals(use.status())) return;
        Booking b = bookings.lockForUpdate(use.bookingId());
        if (b.getStatus() != BookingStatus.CANCELLED) return;
        GiftStore.VoucherRow v = gifts.lockVoucher(use.voucherId()).orElseThrow();
        restore(use, v, "booking_cancelled");
        if (use.amount() == b.getTotalAmount()) b.recordPayment("refunded"); // 금액권만으로 낸 예약 — 카드 환불은 없다
    }

    @Transactional(readOnly = true)
    public List<UUID> cancelledUseIds() {
        return store.appliedOnCancelledBookings(100).stream().map(VoucherStore.UseRow::id).toList();
    }

    private Booking ownBooking(UUID me, UUID bookingId) {
        Booking b = bookings.lockForUpdate(bookingId);
        if (!me.equals(b.getCustomerId())) throw ApiException.notFound();
        return b;
    }

    private BookingVoucherResponse response(Booking b) {
        Optional<VoucherStore.UseRow> use = store.appliedFor(b.getId());
        long applied = use.map(VoucherStore.UseRow::amount).orElse(0L);
        long due = Math.max(0, b.getTotalAmount() - applied);
        String label = use.flatMap(u -> gifts.voucher(u.voucherId())).map(GiftStore.VoucherRow::label).orElse(null);
        return new BookingVoucherResponse(b.getId(), use.map(VoucherStore.UseRow::voucherId).orElse(null), label, applied, KoreanFormat.won(applied),
                due, KoreanFormat.won(due), use.isPresent() && due == 0);
    }

    // ── 환불 ─────────────────────────────────────────────────────────

    /** 환불 계산 결과. method == null 이면 환불 불가(reason) */
    record Eligibility(String method, int rate, long base, long amount, String reason) {
        static Eligibility no(String reason) {
            return new Eligibility(null, 0, 0, 0, reason);
        }
    }

    /**
     * 환불 규칙 (사용자 결정 2026-11-13): 구매 후 7일 이내 ∧ 미사용 → 100% (구매자가 갖고 있고 아무도 받지 않았으면 카드 결제 취소, 아니면 계좌) /
     * 유효기간 만료 ∧ 잔액 5만 원 초과 → 잔액의 95% 계좌 / 그 밖 불가
     */
    static Eligibility eligibility(GiftStore.VoucherRow v, Instant now) {
        switch (v.status()) {
            case "held" -> { }
            case "refund_requested" -> { return Eligibility.no("환불을 접수했어요. 처리되면 알려드릴게요"); }
            case "refunded" -> { return Eligibility.no("이미 환불한 금액권이에요"); }
            case "gifting" -> { return Eligibility.no("선물 중인 금액권은 환불할 수 없어요"); }
            case "used" -> { return Eligibility.no("모두 사용한 금액권이에요"); }
            default -> { return Eligibility.no("환불할 수 없는 금액권이에요"); }
        }
        boolean unused = v.balance() == v.faceAmount();
        boolean withinWeek = v.purchasedAt() != null && now.isBefore(v.purchasedAt().plus(FULL_REFUND_WINDOW));
        if (unused && withinWeek) {
            boolean neverReceived = v.receivedAt() == null && v.ownerId().equals(v.purchaserId());
            return new Eligibility(neverReceived ? "card" : "bank", 100, v.faceAmount(), v.faceAmount(), null);
        }
        boolean expired = v.validUntil() != null && !v.validUntil().isAfter(now);
        if (expired && v.balance() > EXPIRED_REFUND_MIN)
            return new Eligibility("bank", EXPIRED_REFUND_RATE, v.balance(), v.balance() * EXPIRED_REFUND_RATE / 100, null);
        if (expired) return Eligibility.no("유효기간이 지났고 남은 금액이 5만 원 이하라 환불할 수 없어요");
        return Eligibility.no("구매 후 7일이 지났거나 사용한 금액권은 유효기간이 끝난 뒤 남은 금액이 5만 원을 넘을 때 95%를 돌려드려요");
    }

    @Transactional(readOnly = true)
    public RefundQuoteResponse quote(UUID me, UUID voucherId) {
        GiftStore.VoucherRow v = gifts.voucher(voucherId).filter(x -> x.ownerId().equals(me)).orElseThrow(ApiException::notFound);
        Eligibility e = eligibility(v, clock.now());
        String open = store.openRefundOf(voucherId).map(VoucherStore.RefundRow::status).orElse(null);
        return new RefundQuoteResponse(e.method() != null, e.method(), e.method() == null ? null : e.rate(), e.amount(),
                e.method() == null ? null : KoreanFormat.won(e.amount()), e.reason(), "bank".equals(e.method()), open);
    }

    /** 환불 접수 (Idempotency-Key). 카드면 바로 결제 취소 작업, 계좌면 계좌를 암호화해 운영자 송금 대기 */
    public RefundResultResponse requestRefund(UUID me, String idempotencyKey, UUID voucherId, RefundRequest req) {
        return idempotent.run(idempotencyKey, me, List.of("voucher-refund", voucherId), RefundResultResponse.class, () -> doRefund(me, voucherId, req));
    }

    private RefundResultResponse doRefund(UUID me, UUID voucherId, RefundRequest req) {
        profiles.holdLive(me);
        Optional<UUID> purchaseGift = store.purchaseGiftOf(voucherId);
        purchaseGift.ifPresent(gifts::lockGift); // 잠금 순서 선물 → 금액권 → 결제
        GiftStore.VoucherRow v = gifts.lockVoucher(voucherId).filter(x -> x.ownerId().equals(me)).orElseThrow(ApiException::notFound);
        Instant now = clock.now();
        Eligibility e = eligibility(v, now);
        if (e.method() == null) throw new ApiException(ErrorCode.INVALID_STATE, e.reason());
        if ("card".equals(e.method())) {
            if (purchaseGift.isEmpty() || !checkout.requestGiftRefund(purchaseGift.get()))
                throw new ApiException(ErrorCode.INVALID_STATE, "카드 결제를 확인할 수 없어 환불할 수 없어요. 고객센터로 문의해주세요");
            store.setBalance(v.id(), 0, "refunded");
            UUID id = store.insertRefund(v.id(), me, "card", 100, e.base(), e.amount(), "processing", null, null, null, null, now);
            return new RefundResultResponse(id, "card", "processing", 100, e.amount(), KoreanFormat.won(e.amount()));
        }
        String bank = trimmed(req == null ? null : req.bankName());
        String holder = trimmed(req == null ? null : req.accountHolder());
        String digits = req == null || req.accountNumber() == null ? "" : req.accountNumber().replaceAll("[\\s-]", "");
        if (bank == null || holder == null) throw ApiException.validation("은행과 예금주를 입력해주세요");
        if (!digits.matches("[0-9]{6,20}")) throw ApiException.validation("계좌번호를 숫자로 입력해주세요");
        String cipherText = cipher.encrypt(digits); // 키가 없으면 여기서 접수를 막는다
        store.setBalance(v.id(), v.balance(), "refund_requested");
        UUID id = store.insertRefund(v.id(), me, "bank", e.rate(), e.base(), e.amount(), "requested", bank, holder, cipherText, digits.substring(digits.length() - 4), now);
        return new RefundResultResponse(id, "bank", "requested", e.rate(), e.amount(), KoreanFormat.won(e.amount()));
    }

    private static String trimmed(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
