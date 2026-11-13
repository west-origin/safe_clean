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
 * 금액권 사용 원장(voucher_uses)·환불 요청(voucher_refunds)·잔액의 SQL (V071). gift 패키지만 쓴다.
 * 금액권 행 잠금은 GiftStore.lockVoucher. 예약 행 잠금은 booking.BookingService.lockForUpdate (잠금 순서 예약 → 금액권 → 결제)
 */
@Component
public class VoucherStore {
    public record UseRow(UUID id, UUID voucherId, UUID bookingId, UUID customerId, long amount, String status) {}

    public record RefundRow(UUID id, UUID voucherId, UUID ownerId, String method, int rate, long baseAmount, long amount, String status,
                            String bankName, String accountHolder, String accountCipher, String accountLast4, Instant requestedAt, Instant decidedAt, String note) {}

    /** 어드민 목록 한 줄: 환불 요청 + 요청한 고객 이름·금액권 이름 */
    public record RefundListRow(RefundRow refund, String ownerName, String voucherLabel) {}

    private final JdbcClient jdbc;

    public VoucherStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static Timestamp ts(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }

    private static Instant at(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toInstant();
    }

    private static UseRow use(ResultSet rs, int i) throws SQLException {
        return new UseRow(rs.getObject("id", UUID.class), rs.getObject("voucher_id", UUID.class), rs.getObject("booking_id", UUID.class),
                rs.getObject("customer_id", UUID.class), rs.getLong("amount"), rs.getString("status"));
    }

    private static final String REFUND_COLS = "r.id, r.voucher_id, r.owner_id, r.method, r.rate, r.base_amount, r.amount, r.status, r.bank_name, r.account_holder, "
            + "r.account_cipher, r.account_last4, r.requested_at, r.decided_at, r.note";

    private static RefundRow refund(ResultSet rs, int i) throws SQLException {
        return new RefundRow(rs.getObject("id", UUID.class), rs.getObject("voucher_id", UUID.class), rs.getObject("owner_id", UUID.class),
                rs.getString("method"), rs.getInt("rate"), rs.getLong("base_amount"), rs.getLong("amount"), rs.getString("status"),
                rs.getString("bank_name"), rs.getString("account_holder"), rs.getString("account_cipher"), rs.getString("account_last4"),
                at(rs, "requested_at"), at(rs, "decided_at"), rs.getString("note"));
    }

    // ── 사용 ─────────────────────────────────────────────────────────

    /** 이 예약에 쓰는 중인 금액권 (많아야 하나 — voucher_uses_one_per_booking) */
    public Optional<UseRow> appliedFor(UUID bookingId) {
        return jdbc.sql("select id, voucher_id, booking_id, customer_id, amount, status from voucher_uses where booking_id = :b and status = 'applied'")
                .param("b", bookingId).query(VoucherStore::use).optional();
    }

    /** 이 예약에 쓰는 중인 금액권 금액 (없으면 0) — 카드 결제 금액 계산 (GiftPaymentBridge.voucherCredit) */
    public long creditFor(UUID bookingId) {
        return jdbc.sql("select coalesce(sum(amount), 0) from voucher_uses where booking_id = :b and status = 'applied'")
                .param("b", bookingId).query(Long.class).single();
    }

    public UUID insertUse(UUID voucherId, UUID bookingId, UUID customerId, long amount, Instant now) {
        return jdbc.sql("insert into voucher_uses(voucher_id, booking_id, customer_id, amount, status, created_at) values (:v, :b, :c, :a, 'applied', :now) returning id")
                .param("v", voucherId).param("b", bookingId).param("c", customerId).param("a", amount).param("now", ts(now)).query(UUID.class).single();
    }

    /** 사용을 되돌렸다고 적는다 (applied 일 때만) — 바꾼 행 수 */
    public int markRestored(UUID useId, String reason, Instant now) {
        return jdbc.sql("update voucher_uses set status = 'restored', restored_at = :now, restore_reason = :r where id = :id and status = 'applied'")
                .param("now", ts(now)).param("r", reason).param("id", useId).update();
    }

    /** 취소된 예약에 남은 사용 (복원 대상) */
    public List<UseRow> appliedOnCancelledBookings(int limit) {
        return jdbc.sql("""
                select u.id, u.voucher_id, u.booking_id, u.customer_id, u.amount, u.status from voucher_uses u
                join bookings b on b.id = u.booking_id where u.status = 'applied' and b.status = 'cancelled' order by u.created_at limit :n
                """).param("n", limit).query(VoucherStore::use).list();
    }

    public Optional<UseRow> use(UUID id) {
        return jdbc.sql("select id, voucher_id, booking_id, customer_id, amount, status from voucher_uses where id = :id").param("id", id).query(VoucherStore::use).optional();
    }

    /** 잔액과 상태를 함께 바꾼다 (사용: 잔액 감소·0 이면 used / 복원: 잔액 증가·held) */
    public void setBalance(UUID voucherId, long balance, String status) {
        jdbc.sql("update vouchers set balance = :b, status = :s where id = :id").param("b", balance).param("s", status).param("id", voucherId).update();
    }

    // ── 환불 ─────────────────────────────────────────────────────────

    /** 이 금액권을 새로 구매한 선물 (카드 환불 대상 결제를 찾는다) */
    public Optional<UUID> purchaseGiftOf(UUID voucherId) {
        return jdbc.sql("select id from gifts where voucher_id = :v and method = 'purchase' order by created_at limit 1")
                .param("v", voucherId).query(UUID.class).optional();
    }

    public UUID insertRefund(UUID voucherId, UUID owner, String method, int rate, long base, long amount, String status,
                             String bankName, String holder, String cipher, String last4, Instant now) {
        return jdbc.sql("""
                insert into voucher_refunds(voucher_id, owner_id, method, rate, base_amount, amount, status, bank_name, account_holder, account_cipher, account_last4, requested_at)
                values (:v, :o, :m, :rate, :base, :amount, :s, :bank, :holder, :cipher, :last4, :now) returning id
                """).param("v", voucherId).param("o", owner).param("m", method).param("rate", rate).param("base", base).param("amount", amount)
                .param("s", status).param("bank", bankName, Types.VARCHAR).param("holder", holder, Types.VARCHAR).param("cipher", cipher, Types.VARCHAR)
                .param("last4", last4, Types.VARCHAR).param("now", ts(now)).query(UUID.class).single();
    }

    public Optional<RefundRow> openRefundOf(UUID voucherId) {
        return jdbc.sql("select " + REFUND_COLS + " from voucher_refunds r where r.voucher_id = :v and r.status in ('processing', 'requested')")
                .param("v", voucherId).query(VoucherStore::refund).optional();
    }

    public Optional<RefundRow> latestRefundOf(UUID voucherId) {
        return jdbc.sql("select " + REFUND_COLS + " from voucher_refunds r where r.voucher_id = :v order by r.requested_at desc limit 1")
                .param("v", voucherId).query(VoucherStore::refund).optional();
    }

    /** 환불 요청 잠금 (FOR UPDATE) — 두 어드민이 동시에 완료·반려해도 한 번만 */
    public Optional<RefundRow> lockRefund(UUID id) {
        return jdbc.sql("select " + REFUND_COLS + " from voucher_refunds r where r.id = :id for update").param("id", id).query(VoucherStore::refund).optional();
    }

    public Optional<RefundRow> refund(UUID id) {
        return jdbc.sql("select " + REFUND_COLS + " from voucher_refunds r where r.id = :id").param("id", id).query(VoucherStore::refund).optional();
    }

    /** 완료·반려: 결과를 적고 계좌번호 암호문을 지운다(끝 4자리만 남는다) */
    public void decideRefund(UUID id, String status, UUID admin, String note, Instant now) {
        jdbc.sql("update voucher_refunds set status = :s, decided_at = :now, decided_by = :a, note = :note, account_cipher = null where id = :id")
                .param("s", status).param("now", ts(now)).param("a", admin).param("note", note, Types.VARCHAR).param("id", id).update();
    }

    /** 카드 환불이 PG 에서 확인됨 (processing → done) */
    public void cardRefundDone(UUID voucherId, Instant now) {
        jdbc.sql("update voucher_refunds set status = 'done', decided_at = :now where voucher_id = :v and method = 'card' and status = 'processing'")
                .param("now", ts(now)).param("v", voucherId).update();
    }

    /** 어드민 목록: 계좌 환불 요청, 오래된 순 (status 로 거른다) */
    public List<RefundListRow> refunds(String status, int limit) {
        return jdbc.sql("select " + REFUND_COLS + ", p.name as owner_name, v.label as voucher_label from voucher_refunds r "
                        + "join profiles p on p.id = r.owner_id join vouchers v on v.id = r.voucher_id "
                        + "where r.method = 'bank' and r.status = :s order by r.requested_at limit :n")
                .param("s", status).param("n", limit)
                .query((rs, i) -> new RefundListRow(refund(rs, i), rs.getString("owner_name"), rs.getString("voucher_label"))).list();
    }

    /** 탈퇴 + 5년 파기: 남아 있는 계좌 암호문·예금주를 지운다 */
    public void scrubAccounts(UUID owner) {
        jdbc.sql("update voucher_refunds set account_cipher = null, account_holder = null where owner_id = :o").param("o", owner).update();
    }
}
