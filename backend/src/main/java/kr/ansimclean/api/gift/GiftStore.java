package kr.ansimclean.api.gift;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 금액권·선물 표(V070 vouchers·gifts)의 SQL 모음. gift 패키지(GiftService·GiftLifecycle·GiftPaymentBridge)만 쓴다.
 * 잠금 순서: 선물(lockGift) → 금액권(lockVoucher) → 결제(CheckoutService). 상대 이름표는 profiles 를 읽기만 한다.
 */
@Component
public class GiftStore {
    public record GiftRow(UUID id, UUID senderId, UUID recipientId, UUID voucherId, String method, String message, long amount, String label,
                          String status, Instant createdAt, Instant sentAt, Instant expiresAt, Instant decidedAt) {}

    public record VoucherRow(UUID id, UUID ownerId, UUID purchaserId, String catalogKey, String label, long faceAmount, long balance,
                             String status, boolean transferable, Instant purchasedAt, Instant receivedAt, Instant validUntil, Instant createdAt) {}

    /** 선물 목록 한 줄: 선물 + 상대(보낸 목록이면 받는 사람, 받은 목록이면 보낸 사람)의 이름표 */
    public record GiftWithPerson(GiftRow gift, UUID personId, String nickname, String tag, String name, boolean deleted) {}

    private static final String GIFT_COLS = "g.id, g.sender_id, g.recipient_id, g.voucher_id, g.method, g.message, g.amount, g.label, g.status, g.created_at, g.sent_at, g.expires_at, g.decided_at";
    private static final String VOUCHER_COLS = "id, owner_id, purchaser_id, catalog_key, label, face_amount, balance, status, transferable, purchased_at, received_at, valid_until, created_at";

    private final JdbcClient jdbc;

    public GiftStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static Instant at(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toInstant();
    }

    private static Timestamp ts(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }

    private static GiftRow gift(ResultSet rs, int i) throws SQLException {
        return new GiftRow(rs.getObject("id", UUID.class), rs.getObject("sender_id", UUID.class), rs.getObject("recipient_id", UUID.class),
                rs.getObject("voucher_id", UUID.class), rs.getString("method"), rs.getString("message"), rs.getLong("amount"), rs.getString("label"),
                rs.getString("status"), at(rs, "created_at"), at(rs, "sent_at"), at(rs, "expires_at"), at(rs, "decided_at"));
    }

    private static VoucherRow voucher(ResultSet rs, int i) throws SQLException {
        return new VoucherRow(rs.getObject("id", UUID.class), rs.getObject("owner_id", UUID.class), rs.getObject("purchaser_id", UUID.class),
                rs.getString("catalog_key"), rs.getString("label"), rs.getLong("face_amount"), rs.getLong("balance"), rs.getString("status"),
                rs.getBoolean("transferable"), at(rs, "purchased_at"), at(rs, "received_at"), at(rs, "valid_until"), at(rs, "created_at"));
    }

    // ── 선물 ─────────────────────────────────────────────────────────

    public Optional<GiftRow> gift(UUID id) {
        return jdbc.sql("select " + GIFT_COLS + " from gifts g where g.id = :id").param("id", id).query(GiftStore::gift).optional();
    }

    /** 선물 행 잠금 (FOR UPDATE) — 수락·거절·취소·만료·결제 결과가 한 줄로 선다 */
    public Optional<GiftRow> lockGift(UUID id) {
        return jdbc.sql("select " + GIFT_COLS + " from gifts g where g.id = :id for update").param("id", id).query(GiftStore::gift).optional();
    }

    public UUID insertGift(UUID sender, UUID recipient, UUID voucherId, String method, String message, long amount, String label,
                           String status, Instant now, Instant sentAt, Instant expiresAt) {
        return jdbc.sql("""
                insert into gifts(sender_id, recipient_id, voucher_id, method, message, amount, label, status, created_at, sent_at, expires_at)
                values (:s, :r, :v, :m, :msg, :amount, :label, :status, :now, :sent, :exp) returning id
                """).param("s", sender).param("r", recipient).param("v", voucherId).param("m", method).param("msg", message, Types.VARCHAR)
                .param("amount", amount).param("label", label).param("status", status).param("now", ts(now))
                .param("sent", ts(sentAt), Types.TIMESTAMP).param("exp", ts(expiresAt), Types.TIMESTAMP)
                .query(UUID.class).single();
    }

    /** 결제 확정·보유 금액권 선물: 받는 사람에게 보냈다 */
    public void markSent(UUID id, Instant sentAt, Instant expiresAt) {
        jdbc.sql("update gifts set status = 'sent', sent_at = :sent, expires_at = :exp where id = :id")
                .param("sent", ts(sentAt)).param("exp", ts(expiresAt)).param("id", id).update();
    }

    /** 수락·거절·만료·취소·결제 실패 */
    public void decide(UUID id, String status, Instant now) {
        jdbc.sql("update gifts set status = :s, decided_at = :now where id = :id").param("s", status).param("now", ts(now)).param("id", id).update();
    }

    /** 마감이 지난 보낸 선물 (만료 대상) */
    public List<UUID> dueGiftIds(Instant now, int limit) {
        return jdbc.sql("select id from gifts where status = 'sent' and expires_at <= :now order by expires_at limit :n")
                .param("now", ts(now)).param("n", limit).query(UUID.class).list();
    }

    /** 결제를 시작하고 cutoff 까지 승인 요청이 없는 선물 (결제창 이탈 정리 대상 후보) */
    public List<UUID> awaitingBefore(Instant cutoff, int limit) {
        return jdbc.sql("select id from gifts where status = 'awaiting_payment' and created_at <= :c order by created_at limit :n")
                .param("c", ts(cutoff)).param("n", limit).query(UUID.class).list();
    }

    private List<GiftWithPerson> giftsWithPerson(String where, String personCol, UUID me) {
        return jdbc.sql("select " + GIFT_COLS + ", p.id as person_id, p.nickname, p.friend_tag, p.name, (p.deleted_at is not null) as deleted"
                        + " from gifts g join profiles p on p.id = g." + personCol + " where " + where + " order by g.created_at desc limit 100")
                .param("me", me)
                .query((rs, i) -> new GiftWithPerson(gift(rs, i), rs.getObject("person_id", UUID.class), rs.getString("nickname"),
                        rs.getString("friend_tag"), rs.getString("name"), rs.getBoolean("deleted"))).list();
    }

    /** 내가 보낸 선물 (결제 전 정리된 것 포함 — 이력), 최근 100건 */
    public List<GiftWithPerson> sentBy(UUID me) {
        return giftsWithPerson("g.sender_id = :me", "recipient_id", me);
    }

    /** 내가 받은 선물 (결제가 끝나 실제로 보내진 것만), 최근 100건 */
    public List<GiftWithPerson> receivedBy(UUID me) {
        return giftsWithPerson("g.recipient_id = :me and g.status not in ('awaiting_payment', 'payment_failed')", "sender_id", me);
    }

    /** 선물 한 건 + 보낸 사람·받는 사람 이름표 (상세) */
    public Optional<GiftWithPerson> giftWithPerson(UUID id, boolean personIsRecipient) {
        String col = personIsRecipient ? "recipient_id" : "sender_id";
        return jdbc.sql("select " + GIFT_COLS + ", p.id as person_id, p.nickname, p.friend_tag, p.name, (p.deleted_at is not null) as deleted"
                        + " from gifts g join profiles p on p.id = g." + col + " where g.id = :id")
                .param("id", id)
                .query((rs, i) -> new GiftWithPerson(gift(rs, i), rs.getObject("person_id", UUID.class), rs.getString("nickname"),
                        rs.getString("friend_tag"), rs.getString("name"), rs.getBoolean("deleted"))).optional();
    }

    // ── 금액권 ───────────────────────────────────────────────────────

    public Optional<VoucherRow> voucher(UUID id) {
        return jdbc.sql("select " + VOUCHER_COLS + " from vouchers where id = :id").param("id", id).query(GiftStore::voucher).optional();
    }

    /** 금액권 행 잠금 (FOR UPDATE) — 같은 금액권의 동시 선물·(3회차) 예약 사용이 한 줄로 선다 */
    public Optional<VoucherRow> lockVoucher(UUID id) {
        return jdbc.sql("select " + VOUCHER_COLS + " from vouchers where id = :id for update").param("id", id).query(GiftStore::voucher).optional();
    }

    /** 새로 구매하는 금액권 (결제 전 unpaid — 보낸 사람이 소유자·구매자) */
    public UUID insertUnpaidVoucher(UUID purchaser, String catalogKey, String label, long amount, Instant now) {
        return jdbc.sql("""
                insert into vouchers(owner_id, purchaser_id, catalog_key, label, face_amount, balance, status, created_at, updated_at)
                values (:p, :p, :k, :l, :a, :a, 'unpaid', :now, :now) returning id
                """).param("p", purchaser).param("k", catalogKey).param("l", label).param("a", amount).param("now", ts(now))
                .query(UUID.class).single();
    }

    /** 결제 확정: 선물 중으로 잠그고 구매 시각을 적는다 */
    public void markPaidGifting(UUID id, Instant now) {
        jdbc.sql("update vouchers set status = 'gifting', purchased_at = :now where id = :id and status = 'unpaid'")
                .param("now", ts(now)).param("id", id).update();
    }

    /** 상태만 바꾼다 (held → gifting, unpaid → void) */
    public void setStatus(UUID id, String status) {
        jdbc.sql("update vouchers set status = :s where id = :id").param("s", status).param("id", id).update();
    }

    /** 소유자를 바꾸고 보유(held)로 — 수락(받는 사람)·반환(보낸 사람). receivedAt 이 null 이면 그대로 둔다 */
    public void hold(UUID id, UUID owner, Instant validUntil, Instant receivedAt) {
        jdbc.sql("update vouchers set owner_id = :o, status = 'held', valid_until = :v, received_at = coalesce(:r, received_at) where id = :id")
                .param("o", owner).param("v", ts(validUntil)).param("r", ts(receivedAt), Types.TIMESTAMP).param("id", id).update();
    }

    /** 내 금액권 (보유·선물 중·환불 접수), 최근 순. 모두 쓴(used)·환불 끝난 것은 빠진다 */
    public List<VoucherRow> vouchersOf(UUID owner) {
        return jdbc.sql("select " + VOUCHER_COLS + " from vouchers where owner_id = :o and status in ('held', 'gifting', 'refund_requested') order by created_at desc")
                .param("o", owner).query(GiftStore::voucher).list();
    }

    // ── 탈퇴 ─────────────────────────────────────────────────────────

    /** 처리 중인 선물(보낸·받은)이나 쓸 수 있거나 환불받을 수 있는 금액권(만료 ∧ 5만 원 이하는 소멸이라 빼고), 환불 접수 중인 금액권이 있나 (GiftDeletionGuard) */
    public boolean hasOpenGiftOrBalance(UUID user) {
        return jdbc.sql("""
                select exists(select 1 from gifts where (sender_id = :u or recipient_id = :u) and status in ('awaiting_payment', 'sent'))
                    or exists(select 1 from vouchers where owner_id = :u and (status = 'refund_requested'
                        or (status in ('held', 'gifting') and balance > 0 and (valid_until is null or valid_until > now() or balance > 50000))))
                """).param("u", user).query(Boolean.class).single();
    }

    /** 탈퇴 + 5년 파기: 이 계정이 주고받은 선물의 메시지(자유 입력)를 지운다. 금액·시각 이력은 남긴다 */
    public void scrubMessages(UUID user) {
        jdbc.sql("update gifts set message = null where (sender_id = :u or recipient_id = :u) and message is not null").param("u", user).update();
    }
}
