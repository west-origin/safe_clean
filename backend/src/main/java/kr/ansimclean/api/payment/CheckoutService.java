package kr.ansimclean.api.payment;

import java.util.List;
import java.util.UUID;
import kr.ansimclean.api.booking.Booking;
import kr.ansimclean.api.booking.BookingService;
import kr.ansimclean.api.booking.BookingStatus;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.events.PaymentWorkRequested;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.common.web.Idempotent;
import kr.ansimclean.api.payment.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DB 트랜잭션만 담당. 잠금 순서는 항상 예약 → 결제 → 환불이다.
 * 금액권 선물 결제(payments.gift_id, V070)는 예약 대신 선물이 앞에 선다: 선물(+금액권) → 결제 — GiftPaymentHooks 가 선물을 잠그고 결과를 받는다.
 * 예약 결제의 흐름·검사는 그대로이고, 메서드마다 결제 행이 어느 쪽인지(target) 보고 갈린다.
 * 주 N회 요금제(V074 — 요금표 전환 2026-11-13)는 한 달치를 시리즈 하나로 결제한다(payments.series_id): 잠금 순서 시리즈 → 회차(id 순) → 결제 → 환불.
 * 회차가 취소되면 그 회차 몫만 환불 행(payment_refunds.booking_id)을 만들어 토스 부분 취소한다 — 재조정이 상태를 보고 이어 간다.
 */
@Service
@Transactional
public class CheckoutService {
    private final PaymentRepository payments;
    private final PaymentRefundRepository refunds;
    private final BookingService bookings;
    private final CheckoutProperties properties;
    private final Idempotent idempotent;
    private final OutboxService outbox;
    private final AppClock clock;
    private final kr.ansimclean.api.profile.ProfileService profiles;
    private final GiftPaymentHooks gifts;
    static final String GIFT_ORDER_NAME = "안심클린 금액권 선물";
    static final String SERIES_ORDER_NAME = "안심클린 정기청소 한 달";
    static final String SERIES_CHANGED = "series_changed"; // 대기 주문 금액이 회차 취소로 바뀌어 닫음

    public CheckoutService(PaymentRepository payments, PaymentRefundRepository refunds, BookingService bookings,
                           CheckoutProperties properties, Idempotent idempotent, OutboxService outbox, AppClock clock,
                           kr.ansimclean.api.profile.ProfileService profiles, GiftPaymentHooks gifts) {
        this.payments = payments; this.refunds = refunds; this.bookings = bookings; this.properties = properties;
        this.idempotent = idempotent; this.outbox = outbox; this.clock = clock;
        this.profiles = profiles; this.gifts = gifts;
    }

    /**
     * 금액권 선물의 결제 주문을 만든다. GiftService.create 가 선물·금액권을 만든 같은 트랜잭션에서 부른다(선물 행은 이미 그 트랜잭션 것).
     * 금액은 서버 선물 목록 값 — 앱이 보낸 금액을 쓰지 않는다. 결제 키가 없으면 INVALID_STATE (선물 자체를 만들지 않는다)
     */
    public CheckoutResponse prepareGift(UUID giftId, long amount) {
        properties.requireEnabled();
        if (amount <= 0) throw new ApiException(ErrorCode.INVALID_STATE, "결제할 수 없는 금액이에요");
        Payment p = payments.save(Payment.pendingGift(giftId, amount, clock.now()));
        return giftResponse(p);
    }

    static final String VOUCHER_CHANGED = "voucher_changed";

    /**
     * 금액권을 예약에 쓰거나 뺄 때(gift.VoucherService): 이 예약의 살아 있는 카드 결제 상태. 결제 대기 주문(승인 요청 전)이면 닫고 "none".
     * 부르는 쪽이 예약 → 금액권을 먼저 잠근다(잠금 순서 예약 → 금액권 → 결제). 결제가 없으면 "none"
     */
    public String closePendingBookingPayment(UUID bookingId) {
        Payment p = payments.findFirstByBookingIdAndProviderAndStatusNotOrderByCreatedAtDesc(bookingId, "toss", "failed").orElse(null);
        if (p == null) return "none";
        p = payments.lockById(p.getId()).orElseThrow();
        if (p.abandon(VOUCHER_CHANGED, clock.now())) return "none";
        return "failed".equals(p.getStatus()) ? "none" : p.getStatus();
    }

    /**
     * 구매 7일 안 카드 결제 취소(금액권 환불, gift.VoucherService). 결제 확정된 선물 주문에 전액 환불 행을 만들고 작업을 아웃박스에 넣는다.
     * PaymentWorkHandler 가 토스 취소를 부르고, 확인되면 GiftPaymentHooks.paymentChanged(…, "refunded"). 부르는 쪽이 선물 → 금액권을 먼저 잠근다.
     * 확정된 결제가 없으면 false (환불할 수 없음)
     */
    public boolean requestGiftRefund(UUID giftId) {
        Payment found = payments.findFirstByGiftIdOrderByCreatedAtDesc(giftId).orElse(null);
        if (found == null) return false;
        Payment p = payments.lockById(found.getId()).orElseThrow();
        if (!"paid".equals(p.getStatus())) return false;
        refunds.findByPaymentId(p.getId()).orElseGet(() -> refunds.save(PaymentRefund.pending(p, clock.now())));
        p.defer(clock.now()); // 아웃박스가 실패해도 재조정이 5분 뒤 다시 집는다
        outbox.publish(new PaymentWorkRequested(p.getId()));
        return true;
    }

    /** 이 선물의 가장 최근 결제 상태 (없으면 empty). 부르는 쪽(GiftService)이 선물을 먼저 잠근 뒤 읽는다 */
    @Transactional(readOnly = true)
    public java.util.Optional<String> giftPaymentStatus(UUID giftId) {
        return payments.findFirstByGiftIdOrderByCreatedAtDesc(giftId).map(Payment::getStatus);
    }

    /** 이 선물의 가장 최근 결제 화면 정보 (결제창을 다시 열 때). 보낸 사람 확인은 GiftService 가 한다 */
    public java.util.Optional<CheckoutResponse> giftCheckout(UUID giftId) {
        return payments.findFirstByGiftIdOrderByCreatedAtDesc(giftId).map(this::giftResponse);
    }

    public CheckoutResponse prepare(UUID userId, UUID bookingId, String key) {
        properties.requireEnabled(); requireKey(key);
        return idempotent.run(key, userId, "checkout:" + bookingId, CheckoutResponse.class, () -> {
            profiles.holdLive(userId);
            // 주 N회 요금제 회차면 그 시리즈의 한 달치 결제를 준비한다 (회차 어디서 눌러도 같은 주문)
            java.util.Optional<UUID> series = bookings.weeklyPlanSeriesOf(userId, bookingId);
            if (series.isPresent()) return prepareSeries(userId, series.get());
            Booking b = ownBooking(userId, bookingId);
            Payment p = payments.findFirstByBookingIdAndProviderAndStatusNotOrderByCreatedAtDesc(bookingId, "toss", "failed").orElse(null);
            // 금액권을 쓰고 있으면 카드로 낼 금액은 그만큼 줄어든다 (3회차, 예약당 1장)
            long due = b.getPayableAmount() - gifts.voucherCredit(bookingId); // 첫 결제 혜택 할인(V075)도 뺀다
            if (p != null && "pending".equals(p.getStatus()) && p.getAmount() != due) {
                p.abandon(VOUCHER_CHANGED, clock.now()); // 금액이 바뀐 옛 대기 주문 — 보통은 금액권을 쓸 때 이미 닫혀 있다
                p = null;
            }
            if (p == null || "failed".equals(p.getStatus())) {
                if (b.getStatus() == BookingStatus.CANCELLED || b.getTotalAmount() <= 0) throw new ApiException(ErrorCode.INVALID_STATE, "이 예약은 결제할 수 없어요");
                if (b.getPayableAmount() <= 0) throw new ApiException(ErrorCode.INVALID_STATE, BENEFIT_COVERED);
                if (due <= 0) throw new ApiException(ErrorCode.INVALID_STATE, "금액권으로 결제가 끝났어요");
                p = payments.save(Payment.pending(bookingId, null, "toss", due, clock.now()));
            }
            return response(p, b);
        });
    }

    static final String BENEFIT_COVERED = "첫 결제 혜택으로 결제할 금액이 없어요";

    /** 이 회차에서 카드로 낼 몫 = 회차 금액 − 할인 − 금액권 몫 (금액권은 회차 금액 비율로 나눠 쓴다, 2026-11-14) */
    private long cardShare(Booking v, boolean applying) {
        return v.getPayableAmount() - (applying ? gifts.voucherCredit(v.getId()) : gifts.voucherShare(v.getId()));
    }

    /**
     * 금액권을 정기 요금제 시리즈에 쓰거나 뺄 때(gift.VoucherService): 시리즈의 살아 있는 카드 결제 상태. 결제 대기 주문이면 닫고 "none".
     * 부르는 쪽이 시리즈 → 회차 → 금액권을 먼저 잠근다
     */
    public String closePendingSeriesPayment(UUID seriesId) {
        Payment p = payments.findFirstBySeriesIdAndProviderAndStatusNotOrderByCreatedAtDesc(seriesId, "toss", "failed").orElse(null);
        if (p == null) return "none";
        p = payments.lockById(p.getId()).orElseThrow();
        if (p.abandon(VOUCHER_CHANGED, clock.now())) return "none";
        return "failed".equals(p.getStatus()) ? "none" : p.getStatus();
    }

    /** 시리즈 결제 준비: 금액 = 취소되지 않은 회차의 카드 몫 합. 대기 주문 금액이 다르면(그 사이 회차 취소·금액권) 닫고 새로 만든다 */
    private CheckoutResponse prepareSeries(UUID userId, UUID seriesId) {
        List<Booking> visits = bookings.lockSeriesVisits(userId, seriesId);
        long due = visits.stream().filter(v -> v.getStatus() != BookingStatus.CANCELLED).mapToLong(v -> cardShare(v, true)).sum();
        Payment p = payments.findFirstBySeriesIdAndProviderAndStatusNotOrderByCreatedAtDesc(seriesId, "toss", "failed")
                .flatMap(found -> payments.lockById(found.getId())).orElse(null);
        if (p != null && "pending".equals(p.getStatus()) && p.getAmount() != due && p.abandon(SERIES_CHANGED, clock.now())) {
            payments.flush(); // 시리즈당 살아 있는 주문 1건(부분 유니크) — 닫은 것을 먼저 쓰고 새 주문을 넣는다
            p = null;
        }
        if (p == null || "failed".equals(p.getStatus())) {
            boolean open = visits.stream().anyMatch(v -> v.getStatus() != BookingStatus.CANCELLED);
            if (due <= 0) throw new ApiException(ErrorCode.INVALID_STATE, open ? "금액권으로 결제가 끝났어요" : "이 예약은 결제할 수 없어요");
            p = payments.save(Payment.pendingSeries(seriesId, due, clock.now()));
        }
        return seriesResponse(p, visits);
    }

    public CheckoutResponse get(UUID userId, UUID id) {
        java.util.Optional<UUID> series = payments.findCheckoutSeriesId(id);
        if (series.isPresent()) {
            List<Booking> visits = bookings.lockSeriesVisits(userId, series.get());
            return seriesResponse(payments.lockById(id).orElseThrow(ApiException::notFound), visits);
        }
        UUID giftId = giftIdOf(id);
        if (giftId != null) {
            ownGift(userId, giftId);
            return giftResponse(payments.lockById(id).orElseThrow(ApiException::notFound));
        }
        Booking b = ownBooking(userId, bookingIdOf(id));
        return response(payments.lockById(id).orElseThrow(ApiException::notFound), b);
    }

    public CheckoutResponse confirm(UUID userId, UUID id, String key, ConfirmPaymentRequest req) {
        properties.requireEnabled(); requireKey(key);
        return idempotent.run(key, userId, java.util.List.of("confirm", id, req), CheckoutResponse.class, () -> {
            profiles.holdLive(userId);
            java.util.Optional<UUID> series = payments.findCheckoutSeriesId(id);
            if (series.isPresent()) return confirmSeries(userId, series.get(), id, req);
            UUID giftId = giftIdOf(id);
            if (giftId != null) return confirmGift(userId, giftId, id, req);
            Booking b = ownBooking(userId, bookingIdOf(id));
            Payment p = payments.lockById(id).orElseThrow(ApiException::notFound);
            if (b.getStatus() == BookingStatus.CANCELLED && "pending".equals(p.getStatus())) throw new ApiException(ErrorCode.INVALID_STATE);
            boolean enqueue = "pending".equals(p.getStatus());
            p.requestConfirmation(req.paymentKey(), req.amount(), clock.now());
            if (enqueue) { b.recordPayment(p.getStatus()); outbox.publish(new PaymentWorkRequested(id)); }
            return response(p, b);
        });
    }

    private CheckoutResponse confirmSeries(UUID userId, UUID seriesId, UUID id, ConfirmPaymentRequest req) {
        List<Booking> visits = bookings.lockSeriesVisits(userId, seriesId);
        Payment p = payments.lockById(id).orElseThrow(ApiException::notFound);
        boolean open = visits.stream().anyMatch(v -> v.getStatus() != BookingStatus.CANCELLED);
        if (!open && "pending".equals(p.getStatus())) throw new ApiException(ErrorCode.INVALID_STATE, "이 예약은 결제할 수 없어요");
        boolean enqueue = "pending".equals(p.getStatus());
        p.requestConfirmation(req.paymentKey(), req.amount(), clock.now());
        if (enqueue) { recordSeries(p, visits); outbox.publish(new PaymentWorkRequested(id)); }
        return seriesResponse(p, visits);
    }

    private CheckoutResponse confirmGift(UUID userId, UUID giftId, UUID id, ConfirmPaymentRequest req) {
        ownGift(userId, giftId);
        Payment p = payments.lockById(id).orElseThrow(ApiException::notFound);
        if ("pending".equals(p.getStatus()) && !gifts.payable(giftId)) throw new ApiException(ErrorCode.INVALID_STATE, "이 선물은 결제할 수 없어요");
        boolean enqueue = "pending".equals(p.getStatus());
        p.requestConfirmation(req.paymentKey(), req.amount(), clock.now());
        if (enqueue) { gifts.paymentChanged(giftId, p.getStatus()); outbox.publish(new PaymentWorkRequested(id)); }
        return giftResponse(p);
    }

    // 웹훅은 조회를 깨우는 신호일 뿐이다. 본문의 상태·paymentKey·금액을 신뢰하지 않는다.
    public void webhook(String orderId) {
        if (!properties.enabled() || orderId == null) return;
        UUID id;
        try { id = UUID.fromString(orderId); } catch (IllegalArgumentException e) { return; }
        Payment p = payments.lockById(id).orElse(null);
        if (p != null && "toss".equals(p.getProvider()) && p.acceptWebhook(clock.now())) outbox.publish(new PaymentWorkRequested(id));
    }

    public java.util.List<UUID> due() { return properties.enabled() ? payments.findDueIds(clock.now()) : java.util.List.of(); }
    @Transactional(readOnly = true)
    public boolean hasUnresolvedPayment(UUID userId) { return payments.hasUnresolvedPayment(userId); }
    public void enqueue(UUID id) {
        Payment p = payments.lockById(id).orElseThrow(ApiException::notFound);
        p.defer(clock.now()); outbox.publish(new PaymentWorkRequested(id));
    }

    /** cancelAmount = 부분 취소 금액(시리즈 회차 몫). null 이면 남은 잔액 전체 취소 */
    public record Work(UUID id, String paymentKey, long amount, boolean confirm, UUID refundId, Long cancelAmount) {
        public Work(UUID id, String paymentKey, long amount, boolean confirm, UUID refundId) { this(id, paymentKey, amount, confirm, refundId, null); }
    }

    public Work work(UUID id) {
        java.util.Optional<UUID> series = payments.findCheckoutSeriesId(id);
        if (series.isPresent()) return seriesWork(id, series.get());
        UUID giftId = giftIdOf(id);
        if (giftId != null) {
            gifts.lockForPayment(giftId);
            Payment p = payments.lockById(id).orElseThrow(ApiException::notFound);
            p.defer(clock.now());
            // 선물 결제 환불(구매 7일 안 카드 취소)은 3회차 — 그때 환불 행을 만든다. 지금은 남아 있는 대기 환불만 이어서 한다
            UUID refundId = refunds.findByPaymentId(id).filter(r -> "pending".equals(r.getStatus())).map(PaymentRefund::getId).orElse(null);
            return new Work(id, p.getProviderTxId(), p.getAmount(), "authorized".equals(p.getStatus()), refundId);
        }
        Booking b = bookings.lockForUpdate(bookingIdOf(id));
        Payment p = payments.lockById(id).orElseThrow(ApiException::notFound);
        p.defer(clock.now());
        UUID refundId = null;
        if (b.getStatus() == BookingStatus.CANCELLED && ("paid".equals(p.getStatus()) || "partially_refunded".equals(p.getStatus()))) {
            PaymentRefund refund = refunds.findByPaymentId(id).orElseGet(() -> refunds.save(PaymentRefund.pending(p, clock.now())));
            refundId = refund.getId();
        }
        return new Work(id, p.getProviderTxId(), p.getAmount(), "authorized".equals(p.getStatus()) && b.getStatus() != BookingStatus.CANCELLED, refundId);
    }

    /**
     * 시리즈 결제 작업: 결제가 확정됐는데 취소된 회차가 있으면 회차마다 환불 행(그 회차 금액)을 만들고, 아직 끝나지 않은 첫 행을 돌려준다.
     * 결제를 만들기 전에 취소된 회차는 결제 금액에 없으므로 환불하지 않는다. 승인은 취소되지 않은 회차가 있을 때만
     */
    private Work seriesWork(UUID id, UUID seriesId) {
        List<Booking> visits = bookings.lockSeriesVisits(null, seriesId);
        Payment p = payments.lockById(id).orElseThrow(ApiException::notFound);
        p.defer(clock.now());
        PaymentRefund next = null;
        if ("paid".equals(p.getStatus()) || "partially_refunded".equals(p.getStatus())) {
            List<PaymentRefund> rows = refunds.findByPaymentIdAndBookingIdIsNotNullOrderByCreatedAtAscIdAsc(id);
            java.util.Set<UUID> have = new java.util.HashSet<>();
            rows.forEach(r -> have.add(r.getBookingId()));
            for (Booking v : visits) {
                if (!paidFor(p, v) || v.getStatus() != BookingStatus.CANCELLED || have.contains(v.getId())) continue;
                long card = cardShare(v, false); // 금액권 몫은 VoucherRestorer 가 금액권 잔액으로 돌려준다
                if (card > 0) refunds.save(PaymentRefund.pendingVisit(p, v.getId(), card, clock.now()));
            }
            next = refunds.findByPaymentIdAndBookingIdIsNotNullOrderByCreatedAtAscIdAsc(id).stream()
                    .filter(r -> "pending".equals(r.getStatus())).findFirst().orElse(null);
        }
        boolean open = visits.stream().anyMatch(v -> v.getStatus() != BookingStatus.CANCELLED);
        return new Work(id, p.getProviderTxId(), p.getAmount(), "authorized".equals(p.getStatus()) && open,
                next == null ? null : next.getId(), next == null ? null : next.getAmount());
    }

    /** 이 회차가 이 결제 금액에 들어 있나 — 결제 주문을 만든 뒤에 취소된(또는 아직 취소되지 않은) 회차 */
    private static boolean paidFor(Payment p, Booking v) {
        return v.getStatus() != BookingStatus.CANCELLED || (v.getCancelledAt() != null && v.getCancelledAt().isAfter(p.getCreatedAt()));
    }

    /**
     * 시리즈 결제 상태를 회차에 옮긴다: 취소되지 않은 회차 = 결제 상태, 몫을 돌려준 취소 회차 = refunded, 몫이 결제에 든 취소 회차 = 결제 상태.
     * 환불 행은 만든 순서대로 PG 누적 환불액에 닿으면 done
     */
    private void recordSeries(Payment p, List<Booking> visits) {
        java.util.Set<UUID> refunded = new java.util.HashSet<>();
        long sum = 0;
        for (PaymentRefund r : refunds.findByPaymentIdAndBookingIdIsNotNullOrderByCreatedAtAscIdAsc(p.getId())) {
            sum += r.getAmount();
            if (sum > p.getRefundedAmount()) break;
            if ("pending".equals(r.getStatus())) r.done(clock.now());
            refunded.add(r.getBookingId());
        }
        for (Booking v : visits) {
            if (refunded.contains(v.getId())) v.recordPayment("refunded");
            else if (paidFor(p, v)) v.recordPayment(p.getStatus());
        }
    }

    public void apply(UUID id, TossPaymentGateway.Result result) {
        java.util.Optional<UUID> series = payments.findCheckoutSeriesId(id);
        if (series.isPresent()) {
            List<Booking> visits = bookings.lockSeriesVisits(null, series.get());
            Payment p = payments.lockById(id).orElseThrow(ApiException::notFound);
            if ("failed".equals(p.getStatus())) return;
            p.apply(result, clock.now());
            recordSeries(p, visits);
            return;
        }
        UUID giftId = giftIdOf(id);
        if (giftId != null) {
            gifts.lockForPayment(giftId);
            Payment p = payments.lockById(id).orElseThrow(ApiException::notFound);
            if ("failed".equals(p.getStatus())) return;
            p.apply(result, clock.now());
            // 결제 확정(paid)과 선물 보내기가 이 트랜잭션 하나에서 같이 커밋된다 — 결제는 됐는데 선물이 없는 순간이 없다
            gifts.paymentChanged(giftId, p.getStatus());
            if ("refunded".equals(p.getStatus())) refunds.findByPaymentId(id).ifPresent(r -> r.done(clock.now()));
            return;
        }
        Booking b = bookings.lockForUpdate(bookingIdOf(id));
        Payment p = payments.lockById(id).orElseThrow(ApiException::notFound);
        // 실패 확정 뒤 새 결제 시도가 생길 수 있다. 옛 실패 알림이 새 결제의 요약을 unpaid로 되돌리면 안 된다.
        if ("failed".equals(p.getStatus())) return;
        p.apply(result, clock.now()); b.recordPayment(p.getStatus());
        if ("refunded".equals(p.getStatus())) refunds.findByPaymentId(id).ifPresent(r -> r.done(clock.now()));
    }

    private Booking ownBooking(UUID userId, UUID bookingId) {
        Booking b = bookings.lockForUpdate(bookingId);
        if (!userId.equals(b.getCustomerId())) throw ApiException.notFound();
        return b;
    }
    /** 선물 결제면 그 선물을 잠그고 결제한 사람이 userId 인지 본다 (아니면 남의 것 — 404) */
    private void ownGift(UUID userId, UUID giftId) {
        UUID payer = gifts.lockForPayment(giftId).orElseThrow(ApiException::notFound);
        if (!payer.equals(userId)) throw ApiException.notFound();
    }
    /** 선물 결제면 선물 id, 예약 결제면 null. 없는 결제는 404 */
    private UUID giftIdOf(UUID id) {
        java.util.Optional<UUID> gift = payments.findCheckoutGiftId(id);
        if (gift.isPresent()) return gift.get();
        bookingIdOf(id); // 예약 결제인지 확인 (아니면 404)
        return null;
    }
    private UUID bookingIdOf(UUID id) {
        return payments.findCheckoutBookingId(id).orElseThrow(ApiException::notFound);
    }
    private void requireKey(String key) {
        if (key == null || key.isBlank()) throw ApiException.validation("Idempotency-Key 가 필요해요");
    }
    private CheckoutResponse response(Payment p, Booking b) {
        return new CheckoutResponse(p.getId(), p.getBookingId(), p.getId().toString(), p.getAmount(), "KRW",
                p.getStatus(), p.getRefundedAmount(), properties.clientKey(), "안심클린 방문 서비스",
                b.getStatus() != BookingStatus.CANCELLED && "pending".equals(p.getStatus()),
                refunds.findByPaymentId(p.getId()).map(PaymentRefund::getStatus).orElse(null), null);
    }
    private CheckoutResponse seriesResponse(Payment p, List<Booking> visits) {
        boolean open = visits.stream().anyMatch(v -> v.getStatus() != BookingStatus.CANCELLED);
        List<PaymentRefund> rows = refunds.findByPaymentIdAndBookingIdIsNotNullOrderByCreatedAtAscIdAsc(p.getId());
        String refundStatus = rows.isEmpty() ? null : rows.stream().anyMatch(r -> "pending".equals(r.getStatus())) ? "pending" : "done";
        return new CheckoutResponse(p.getId(), null, p.getId().toString(), p.getAmount(), "KRW", p.getStatus(), p.getRefundedAmount(),
                properties.clientKey(), SERIES_ORDER_NAME, open && "pending".equals(p.getStatus()), refundStatus, null, p.getSeriesId());
    }

    private CheckoutResponse giftResponse(Payment p) {
        return new CheckoutResponse(p.getId(), null, p.getId().toString(), p.getAmount(), "KRW",
                p.getStatus(), p.getRefundedAmount(), properties.clientKey(), GIFT_ORDER_NAME,
                "pending".equals(p.getStatus()) && gifts.payable(p.getGiftId()),
                refunds.findByPaymentId(p.getId()).map(PaymentRefund::getStatus).orElse(null), p.getGiftId());
    }
}
