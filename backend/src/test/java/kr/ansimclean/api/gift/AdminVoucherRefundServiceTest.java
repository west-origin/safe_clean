package kr.ansimclean.api.gift;

import kr.ansimclean.api.common.activity.ActivityLogService;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.events.VoucherRefundDecided;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.security.AccessPolicy;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.Roles;
import kr.ansimclean.api.common.time.AppClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

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

/** 어드민 계좌 환불: 어드민 + MFA 만, 계좌 열람은 활동 이력, 완료 = 금액권 refunded·원문 삭제·고객 푸시, 반려 = 금액권 held, 다시 눌러도 한 번만 */
class AdminVoucherRefundServiceTest {
    static final Instant NOW = Instant.parse("2026-11-13T03:00:00Z");
    VoucherStore store = mock(VoucherStore.class);
    GiftStore gifts = mock(GiftStore.class);
    ActivityLogService activity = mock(ActivityLogService.class);
    OutboxService outbox = mock(OutboxService.class);
    RefundAccountCipher cipher = new RefundAccountCipher(Base64.getEncoder().encodeToString(new byte[32]));
    AdminVoucherRefundService service;
    final CurrentUser admin = new CurrentUser(UUID.randomUUID(), Roles.ADMIN, true, null, true);
    final UUID owner = UUID.randomUUID();
    final UUID voucher = UUID.randomUUID();
    final UUID refundId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AdminVoucherRefundService(store, gifts, cipher, new AccessPolicy(), activity, outbox, new AppClock(Clock.fixed(NOW, AppClock.ZONE)));
        stubRefund("requested");
        when(gifts.voucher(voucher)).thenReturn(Optional.of(new GiftStore.VoucherRow(voucher, owner, owner, "k", "l", 69000, 69000, "refund_requested", true, NOW, NOW, NOW, NOW)));
    }

    private void stubRefund(String status) {
        var r = new VoucherStore.RefundRow(refundId, voucher, owner, "bank", 100, 69000, 69000, status, "국민은행", "김고객",
                "requested".equals(status) ? cipher.encrypt("12345678901") : null, "8901", NOW, null, null);
        when(store.refund(refundId)).thenReturn(Optional.of(r));
        when(store.lockRefund(refundId)).thenReturn(Optional.of(r));
    }

    @Test
    void 어드민_MFA_가_아니면_막는다() {
        for (CurrentUser u : new CurrentUser[] { new CurrentUser(admin.id(), Roles.ADMIN, true, null, false), new CurrentUser(owner, Roles.CUSTOMER, true, null, true) }) {
            assertThatThrownBy(() -> service.pay(u, refundId, null)).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.FORBIDDEN);
            assertThatThrownBy(() -> service.account(u, refundId)).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.FORBIDDEN);
        }
        verify(store, never()).decideRefund(any(), any(), any(), any(), any());
    }

    @Test
    void 계좌_열람은_원문을_주고_활동_이력을_남긴다() {
        var acc = service.account(admin, refundId);
        assertThat(acc.accountNumber()).isEqualTo("12345678901");
        verify(activity).record(admin, ActivityLogService.VOUCHER_REFUND_ACCOUNT_VIEW, "voucher_refund", refundId.toString(), null, null, null);
    }

    @Test
    void 완료는_금액권_다음_환불_행을_잠그고_한_번만() {
        service.pay(admin, refundId, "송금함");
        var order = inOrder(gifts, store);
        order.verify(gifts).lockVoucher(voucher);
        order.verify(store).lockRefund(refundId);
        order.verify(store).decideRefund(refundId, "paid", admin.id(), "송금함", NOW);
        verify(store).setBalance(voucher, 0, "refunded");
        verify(outbox).publish(new VoucherRefundDecided(refundId, owner, "paid"));

        stubRefund("paid");
        service.pay(admin, refundId, null); // 다시 눌러도 같다
        verify(outbox).publish(any());
        assertThatThrownBy(() -> service.reject(admin, refundId, null)).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.INVALID_STATE);
        assertThatThrownBy(() -> service.account(admin, refundId)).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.INVALID_STATE);
    }

    @Test
    void 반려는_금액권을_다시_쓸_수_있게() {
        service.reject(admin, refundId, "예금주 불일치");
        verify(store).decideRefund(refundId, "rejected", admin.id(), "예금주 불일치", NOW);
        verify(store).setBalance(voucher, 69000, "held");
        verify(store, never()).setBalance(eq(voucher), anyLong(), eq("refunded"));
        verify(outbox).publish(new VoucherRefundDecided(refundId, owner, "rejected"));
    }
}
