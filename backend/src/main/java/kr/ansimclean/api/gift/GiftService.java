package kr.ansimclean.api.gift;

import kr.ansimclean.api.catalog.AppSettingsService;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.events.GiftResolved;
import kr.ansimclean.api.common.format.KoreanFormat;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.common.web.Idempotent;
import kr.ansimclean.api.common.web.RateLimiter;
import kr.ansimclean.api.friend.FriendService;
import kr.ansimclean.api.gift.dto.CatalogItem;
import kr.ansimclean.api.gift.dto.CreateGiftRequest;
import kr.ansimclean.api.gift.dto.GiftCatalogResponse;
import kr.ansimclean.api.gift.dto.GiftDetailResponse;
import kr.ansimclean.api.gift.dto.GiftView;
import kr.ansimclean.api.gift.dto.GiftsResponse;
import kr.ansimclean.api.gift.dto.VoucherView;
import kr.ansimclean.api.gift.dto.VouchersResponse;
import kr.ansimclean.api.payment.CheckoutService;
import kr.ansimclean.api.payment.dto.CheckoutResponse;
import kr.ansimclean.api.profile.ProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 금액권 선물 (docs/domain.md '고객 친구·금액권 선물' › 금액권·선물 규칙). 모든 메서드는 토큰의 고객 id(me) 기준.
 * 잠금 순서: (친구 쌍) → 선물 → 금액권 → 결제. 남의 선물·금액권은 NOT_FOUND.
 * 새로 구매한 선물이 받는 사람에게 가는 순간은 결제 확정 트랜잭션(GiftPaymentBridge) — 여기서는 결제 주문까지만 만든다.
 */
@Service
@Transactional
public class GiftService {
    static final String AWAITING_PAYMENT = "awaiting_payment";
    static final String SENT = "sent";
    static final String METHOD_PURCHASE = "purchase";
    static final String METHOD_VOUCHER = "voucher";
    /** 결제창을 연 뒤 승인 요청 없이 이만큼 지나면 선물을 정리한다 (코드에서 정함) */
    static final Duration PAYMENT_ABANDON_AFTER = Duration.ofHours(1);
    /** 유효기간이 이보다 적게 남으면 금액권 목록에 경고 (코드에서 정함) */
    static final Duration EXPIRING_SOON = Duration.ofDays(30);
    static final String CATALOG = "gift_catalog";

    private final GiftStore store;
    private final GiftLifecycle lifecycle;
    private final FriendService friends;
    private final CheckoutService checkout;
    private final ProfileService profiles;
    private final AppSettingsService settings;
    private final OutboxService outbox;
    private final Idempotent idempotent;
    private final RateLimiter rateLimiter;
    private final AppClock clock;

    public GiftService(GiftStore store, GiftLifecycle lifecycle, FriendService friends, CheckoutService checkout, ProfileService profiles,
                       AppSettingsService settings, OutboxService outbox, Idempotent idempotent, RateLimiter rateLimiter, AppClock clock) {
        this.store = store;
        this.lifecycle = lifecycle;
        this.friends = friends;
        this.checkout = checkout;
        this.profiles = profiles;
        this.settings = settings;
        this.outbox = outbox;
        this.idempotent = idempotent;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    // ── 목록 ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public GiftCatalogResponse catalog() {
        return new GiftCatalogResponse(catalogItems(), lifecycle.acceptDays(), lifecycle.validDays());
    }

    private List<CatalogItem> catalogItems() {
        List<CatalogItem> items = new ArrayList<>();
        for (JsonNode n : settings.node(CATALOG)) {
            long amount = n.get("amount").asLong();
            items.add(new CatalogItem(n.get("key").asText(), n.get("group").asText(), n.get("label").asText(), amount, KoreanFormat.won(amount)));
        }
        return items;
    }

    @Transactional(readOnly = true)
    public VouchersResponse vouchers(UUID me) {
        Instant now = clock.now();
        return new VouchersResponse(store.vouchersOf(me).stream().map(v -> voucherView(v, now)).toList());
    }

    private VoucherView voucherView(GiftStore.VoucherRow v, Instant now) {
        String blocked = giftBlockReason(v, now);
        String useBlocked = VoucherService.useBlockReason(v, now); // 예약 결제에 쓸 수 있나 — 사용 API 와 같은 규칙
        boolean soon = v.validUntil() != null && v.validUntil().isBefore(now.plus(EXPIRING_SOON));
        return new VoucherView(v.id(), v.label(), v.faceAmount(), v.balance(), KoreanFormat.won(v.balance()), v.status(),
                iso(v.validUntil()), v.validUntil() == null ? null : KoreanFormat.dateLong(AppClock.toSeoul(v.validUntil()).toLocalDate()) + "까지",
                blocked == null, soon, blocked, useBlocked == null, useBlocked);
    }

    /** 보유 금액권을 선물할 수 없는 이유 (할 수 있으면 null). 문구는 코드에서 정함 */
    private String giftBlockReason(GiftStore.VoucherRow v, Instant now) {
        if ("gifting".equals(v.status())) return "선물을 보내고 받는 사람의 결정을 기다리고 있어요";
        if ("refund_requested".equals(v.status())) return "환불을 접수한 금액권이에요";
        if (!"held".equals(v.status())) return "선물할 수 없는 금액권이에요";
        if (!v.transferable()) return "선물할 수 없는 금액권이에요";
        if (v.balance() != v.faceAmount()) return "사용한 금액권은 선물할 수 없어요";
        if (v.validUntil() != null && !v.validUntil().isAfter(now)) return "유효기간이 지난 금액권이에요";
        if (v.validUntil() != null && v.validUntil().isBefore(now.plus(Duration.ofDays(lifecycle.acceptDays()))))
            return "유효기간이 얼마 남지 않아 선물할 수 없어요";
        return null;
    }

    @Transactional(readOnly = true)
    public GiftsResponse gifts(UUID me, String box) {
        List<GiftStore.GiftWithPerson> rows = switch (box == null ? "" : box) {
            case "sent" -> store.sentBy(me);
            case "received" -> store.receivedBy(me);
            default -> throw ApiException.validation("box 는 sent 또는 received 예요");
        };
        return new GiftsResponse(box, rows.stream().map(r -> view(r, box)).toList());
    }

    /** 선물 한 건 (보낸 사람·받는 사람만). 보낸 사람의 결제 대기 선물이면 결제창 정보도 */
    public GiftDetailResponse detail(UUID me, UUID giftId) {
        GiftStore.GiftRow g = store.gift(giftId).orElseThrow(ApiException::notFound);
        boolean sender = g.senderId().equals(me);
        if (!sender && !(g.recipientId().equals(me) && isDelivered(g))) throw ApiException.notFound();
        GiftStore.GiftWithPerson row = store.giftWithPerson(giftId, sender).orElseThrow(ApiException::notFound);
        CheckoutResponse pay = sender && AWAITING_PAYMENT.equals(g.status()) ? checkout.giftCheckout(giftId).orElse(null) : null;
        return new GiftDetailResponse(view(row, sender ? "sent" : "received"), pay);
    }

    private static boolean isDelivered(GiftStore.GiftRow g) {
        return !AWAITING_PAYMENT.equals(g.status()) && !"payment_failed".equals(g.status());
    }

    // ── 보내기 ───────────────────────────────────────────────────────

    /** 선물 보내기 (Idempotency-Key). 새로 구매면 결제 주문을 같이 만들어 돌려주고, 보유 금액권이면 바로 받는 사람에게 간다 */
    public GiftDetailResponse create(UUID me, String idempotencyKey, CreateGiftRequest req) {
        return idempotent.run(idempotencyKey, me, List.of("gift", req), GiftDetailResponse.class, () -> doCreate(me, req));
    }

    private GiftDetailResponse doCreate(UUID me, CreateGiftRequest req) {
        rateLimiter.check("gift-create", me.toString(), RateLimiter.GIFT_CREATE_PER_USER_PER_HOUR);
        if (me.equals(req.recipientId())) throw ApiException.validation("나에게는 선물할 수 없어요");
        String message = req.message() == null || req.message().isBlank() ? null : req.message().strip();
        if (message != null && message.codePointCount(0, message.length()) > 200) throw ApiException.validation("메시지는 200자까지 쓸 수 있어요");
        profiles.holdLive(me); // 탈퇴와 줄을 선다
        // 친구 쌍 잠금 안에서 친구인지 본다 — 이 트랜잭션이 끝날 때까지 삭제·차단이 끼어들지 못한다
        if (!friends.lockFriendship(me, req.recipientId())) throw new ApiException(ErrorCode.INVALID_STATE, "친구에게만 선물할 수 있어요");
        Instant now = clock.now();
        if (METHOD_PURCHASE.equals(req.method())) {
            CatalogItem item = catalogItems().stream().filter(i -> i.key().equals(req.catalogKey())).findFirst()
                    .orElseThrow(() -> ApiException.validation("선물할 상품을 골라주세요"));
            UUID voucherId = store.insertUnpaidVoucher(me, item.key(), item.label(), item.amount(), now);
            UUID giftId = store.insertGift(me, req.recipientId(), voucherId, METHOD_PURCHASE, message, item.amount(), item.label(), AWAITING_PAYMENT, now, null, null);
            CheckoutResponse pay = checkout.prepareGift(giftId, item.amount()); // 결제 키가 없으면 여기서 실패하고 선물·금액권도 만들어지지 않는다
            return new GiftDetailResponse(view(store.giftWithPerson(giftId, true).orElseThrow(), "sent"), pay);
        }
        if (!METHOD_VOUCHER.equals(req.method())) throw ApiException.validation("선물 방식은 purchase 또는 voucher 예요");
        if (req.voucherId() == null) throw ApiException.validation("선물할 금액권을 골라주세요");
        GiftStore.VoucherRow v = store.lockVoucher(req.voucherId()).filter(x -> x.ownerId().equals(me)).orElseThrow(ApiException::notFound);
        String blocked = giftBlockReason(v, now);
        if (blocked != null) throw new ApiException(ErrorCode.INVALID_STATE, blocked);
        store.setStatus(v.id(), "gifting");
        UUID giftId = store.insertGift(me, req.recipientId(), v.id(), METHOD_VOUCHER, message, v.balance(), v.label(), AWAITING_PAYMENT, now, null, null);
        lifecycle.send(store.gift(giftId).orElseThrow(), now);
        return new GiftDetailResponse(view(store.giftWithPerson(giftId, true).orElseThrow(), "sent"), null);
    }

    // ── 받기·거절·취소·만료 ───────────────────────────────────────────

    /** 받기 — 받는 사람만, 보낸 상태·마감 전·아직 친구. 소유자가 받는 사람으로 바뀐다. 이미 받았으면 같은 결과 */
    public GiftDetailResponse accept(UUID me, UUID giftId) {
        GiftStore.GiftRow g = lockAs(giftId, me, false);
        if (!"accepted".equals(g.status())) {
            requireSent(g);
            Instant now = clock.now();
            if (!g.expiresAt().isAfter(now)) throw new ApiException(ErrorCode.INVALID_STATE, "받을 수 있는 기간이 지났어요");
            if (!friends.areFriends(me, g.senderId())) throw new ApiException(ErrorCode.INVALID_STATE, "친구 사이가 아니어서 받을 수 없어요");
            profiles.holdLive(me);
            GiftStore.VoucherRow v = store.lockVoucher(g.voucherId()).orElseThrow();
            // 받은 날부터 1년. 받은 금액권을 다시 선물한 경우처럼 이미 유효기간이 있으면 늘리지 않는다
            Instant validUntil = v.validUntil() != null ? v.validUntil() : now.plus(Duration.ofDays(lifecycle.validDays()));
            store.hold(v.id(), me, validUntil, now);
            store.decide(g.id(), "accepted", now);
            outbox.publish(new GiftResolved(g.id(), g.senderId(), "accepted"));
        }
        return detail(me, giftId);
    }

    /** 거절 — 받는 사람만. 금액권은 보낸 사람에게 돌아가고 보낸 사람에게 알린다 */
    public GiftDetailResponse reject(UUID me, UUID giftId) {
        GiftStore.GiftRow g = lockAs(giftId, me, false);
        if (!"rejected".equals(g.status())) {
            requireSent(g);
            giveBack(g, "rejected", true);
        }
        return detail(me, giftId);
    }

    /**
     * 취소 — 보낸 사람만, 받기 전까지. 보낸 선물이면 금액권이 돌아온다(받는 사람에게 알리지 않는다).
     * 결제 대기 선물은 승인 요청 전(결제 pending)일 때만 — 금액권은 무효. 승인 요청 뒤에는 결제 결과를 기다려야 한다
     */
    public GiftDetailResponse cancel(UUID me, UUID giftId) {
        GiftStore.GiftRow g = lockAs(giftId, me, true);
        if (!"cancelled".equals(g.status())) {
            if (AWAITING_PAYMENT.equals(g.status())) {
                store.lockVoucher(g.voucherId());
                String pay = checkout.giftPaymentStatus(giftId).orElse("pending");
                if (!"pending".equals(pay) && !"failed".equals(pay)) throw new ApiException(ErrorCode.INVALID_STATE, "결제를 확인하고 있어요. 잠시 후 다시 시도해주세요");
                store.decide(g.id(), "cancelled", clock.now());
                store.setStatus(g.voucherId(), "void");
            } else {
                requireSent(g);
                giveBack(g, "cancelled", false);
            }
        }
        return detail(me, giftId);
    }

    /** 만료 (GiftExpirer 가 한 건씩, 각자 트랜잭션). 이미 처리됐거나 마감 전이면 아무것도 안 한다 */
    public void expire(UUID giftId) {
        GiftStore.GiftRow g = store.lockGift(giftId).orElse(null);
        if (g == null || !SENT.equals(g.status()) || g.expiresAt().isAfter(clock.now())) return;
        giveBack(g, "expired", true);
    }

    /** 결제창 이탈 정리 (GiftExpirer). 결제 대기로 1시간이 지났고 승인 요청이 없었으면 선물 취소·금액권 무효 */
    public void abandonIfUnpaid(UUID giftId) {
        GiftStore.GiftRow g = store.lockGift(giftId).orElse(null);
        if (g == null || !AWAITING_PAYMENT.equals(g.status()) || g.createdAt().isAfter(clock.now().minus(PAYMENT_ABANDON_AFTER))) return;
        store.lockVoucher(g.voucherId());
        String pay = checkout.giftPaymentStatus(giftId).orElse("pending");
        if (!"pending".equals(pay) && !"failed".equals(pay)) return; // 승인 요청이 있었다 — 결제 재조정이 결과를 정한다
        store.decide(g.id(), "cancelled", clock.now());
        store.setStatus(g.voucherId(), "void");
    }

    @Transactional(readOnly = true)
    public List<UUID> dueGiftIds() {
        return store.dueGiftIds(clock.now(), 100);
    }

    @Transactional(readOnly = true)
    public List<UUID> abandonedGiftIds() {
        return store.awaitingBefore(clock.now().minus(PAYMENT_ABANDON_AFTER), 100);
    }

    /** 금액권을 보낸 사람에게 돌려주고 선물을 status 로 닫는다. notify 면 보낸 사람에게 결과 푸시 */
    private void giveBack(GiftStore.GiftRow g, String status, boolean notify) {
        Instant now = clock.now();
        GiftStore.VoucherRow v = store.lockVoucher(g.voucherId()).orElseThrow();
        lifecycle.returnToSender(g, v, now);
        store.decide(g.id(), status, now);
        if (notify) outbox.publish(new GiftResolved(g.id(), g.senderId(), status));
    }

    /** 선물을 잠그고, asSender 면 보낸 사람·아니면 받는 사람(보내진 선물만)인지 본다 — 아니면 NOT_FOUND */
    private GiftStore.GiftRow lockAs(UUID giftId, UUID me, boolean asSender) {
        GiftStore.GiftRow g = store.lockGift(giftId).orElseThrow(ApiException::notFound);
        boolean ok = asSender ? g.senderId().equals(me) : g.recipientId().equals(me) && isDelivered(g);
        if (!ok) throw ApiException.notFound();
        return g;
    }

    private static void requireSent(GiftStore.GiftRow g) {
        if (!SENT.equals(g.status())) throw new ApiException(ErrorCode.INVALID_STATE, switch (g.status()) {
            case "cancelled" -> "보낸 사람이 선물을 취소했어요";
            case "expired" -> "받을 수 있는 기간이 지났어요";
            default -> "이미 처리된 선물이에요";
        });
    }

    // ── 표시 ─────────────────────────────────────────────────────────

    private GiftView view(GiftStore.GiftWithPerson r, String direction) {
        GiftStore.GiftRow g = r.gift();
        String expiresLabel = SENT.equals(g.status()) && g.expiresAt() != null
                ? KoreanFormat.dateShort(AppClock.toSeoul(g.expiresAt()).toLocalDate()) + " " + KoreanFormat.clock(g.expiresAt()) + "까지" : null;
        return new GiftView(g.id(), direction, g.method(), g.status(), g.amount(), KoreanFormat.won(g.amount()), g.label(), g.message(),
                FriendService.person(r.personId(), r.nickname(), r.tag(), r.name(), r.deleted()),
                iso(g.createdAt()), iso(g.sentAt()), iso(g.expiresAt()), expiresLabel, iso(g.decidedAt()));
    }

    private static String iso(Instant i) {
        return i == null ? null : i.toString();
    }

    /** 탈퇴 가드용 */
    @Transactional(readOnly = true)
    public boolean hasOpenGiftOrBalance(UUID user) {
        return store.hasOpenGiftOrBalance(user);
    }
}
