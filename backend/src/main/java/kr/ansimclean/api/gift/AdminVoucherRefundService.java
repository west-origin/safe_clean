package kr.ansimclean.api.gift;

import kr.ansimclean.api.common.activity.ActivityLogService;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.events.VoucherRefundDecided;
import kr.ansimclean.api.common.format.KoreanFormat;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.security.AccessPolicy;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.gift.dto.AdminRefundAccountResponse;
import kr.ansimclean.api.gift.dto.AdminVoucherRefundItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 어드민 금액권 계좌 환불 (docs/domain.md 금액권 사용·환불 규칙). 모든 메서드가 먼저 AccessPolicy 로 어드민 + MFA 를 확인한다.
 * 운영자가 계좌번호를 열어(활동 이력) 직접 송금한 뒤 '완료' — 금액권 refunded·잔액 0, 계좌 원문 삭제. '반려' — 금액권 다시 held.
 * 잠금 순서 금액권 → 환불 행 (고객 접수와 같은 앞부분). 두 어드민이 동시에 눌러도 한 번만 처리된다
 */
@Service
@Transactional
public class AdminVoucherRefundService {
    private final VoucherStore store;
    private final GiftStore gifts;
    private final RefundAccountCipher cipher;
    private final AccessPolicy policy;
    private final ActivityLogService activity;
    private final OutboxService outbox;
    private final AppClock clock;

    public AdminVoucherRefundService(VoucherStore store, GiftStore gifts, RefundAccountCipher cipher, AccessPolicy policy,
                                     ActivityLogService activity, OutboxService outbox, AppClock clock) {
        this.store = store;
        this.gifts = gifts;
        this.cipher = cipher;
        this.policy = policy;
        this.activity = activity;
        this.outbox = outbox;
        this.clock = clock;
    }

    private void require(CurrentUser actor, UUID id) {
        policy.require(actor, AccessPolicy.Action.VOUCHER_REFUND_MANAGE, AccessPolicy.Resource.of("voucher_refund", id));
    }

    /** status = requested(송금 대기, 기본) | paid | rejected. 오래된 순 200건 */
    @Transactional(readOnly = true)
    public List<AdminVoucherRefundItem> list(CurrentUser actor, String status) {
        require(actor, null);
        String s = status == null || status.isBlank() ? "requested" : status;
        if (!List.of("requested", "paid", "rejected").contains(s)) throw ApiException.validation("status 는 requested·paid·rejected 예요");
        return store.refunds(s, 200).stream().map(r -> {
            VoucherStore.RefundRow f = r.refund();
            return new AdminVoucherRefundItem(f.id(), f.voucherId(), r.voucherLabel(), r.ownerName(), f.rate(), f.baseAmount(), f.amount(),
                    KoreanFormat.won(f.amount()), f.bankName(), f.accountHolder(), f.accountLast4(), f.status(), f.requestedAt().toString());
        }).toList();
    }

    /** 송금하려고 계좌번호 원문을 연다 — 송금 대기 요청만, 열람은 활동 이력에 남는다 */
    public AdminRefundAccountResponse account(CurrentUser actor, UUID id) {
        require(actor, id);
        VoucherStore.RefundRow r = store.refund(id).filter(x -> "bank".equals(x.method())).orElseThrow(ApiException::notFound);
        if (!"requested".equals(r.status()) || r.accountCipher() == null) throw new ApiException(ErrorCode.INVALID_STATE, "처리가 끝나 계좌번호를 지운 요청이에요");
        String number = cipher.decrypt(r.accountCipher());
        activity.record(actor, ActivityLogService.VOUCHER_REFUND_ACCOUNT_VIEW, "voucher_refund", id.toString(), null, null, null);
        return new AdminRefundAccountResponse(r.bankName(), r.accountHolder(), number);
    }

    /** 송금 완료 — 이미 완료면 같은 결과, 반려된 요청은 INVALID_STATE */
    public void pay(CurrentUser actor, UUID id, String note) {
        require(actor, id);
        VoucherStore.RefundRow r = lockedBankRefund(id);
        if ("paid".equals(r.status())) return;
        if (!"requested".equals(r.status())) throw new ApiException(ErrorCode.INVALID_STATE, "이미 처리된 환불 요청이에요");
        store.decideRefund(id, "paid", actor.id(), note, clock.now());
        store.setBalance(r.voucherId(), 0, "refunded");
        activity.record(actor, ActivityLogService.VOUCHER_REFUND_PAY, "voucher_refund", id.toString(),
                Map.of("status", "requested"), Map.of("status", "paid", "amount", r.amount()), note);
        outbox.publish(new VoucherRefundDecided(id, r.ownerId(), "paid"));
    }

    /** 반려 — 금액권은 다시 쓸 수 있게(held). 이미 반려면 같은 결과, 완료된 요청은 INVALID_STATE */
    public void reject(CurrentUser actor, UUID id, String note) {
        require(actor, id);
        VoucherStore.RefundRow r = lockedBankRefund(id);
        if ("rejected".equals(r.status())) return;
        if (!"requested".equals(r.status())) throw new ApiException(ErrorCode.INVALID_STATE, "이미 처리된 환불 요청이에요");
        GiftStore.VoucherRow v = gifts.voucher(r.voucherId()).orElseThrow();
        store.decideRefund(id, "rejected", actor.id(), note, clock.now());
        store.setBalance(v.id(), v.balance(), "held");
        activity.record(actor, ActivityLogService.VOUCHER_REFUND_REJECT, "voucher_refund", id.toString(),
                Map.of("status", "requested"), Map.of("status", "rejected"), note);
        outbox.publish(new VoucherRefundDecided(id, r.ownerId(), "rejected"));
    }

    /** 금액권 → 환불 행 순서로 잠그고 다시 읽는다 */
    private VoucherStore.RefundRow lockedBankRefund(UUID id) {
        VoucherStore.RefundRow first = store.refund(id).filter(x -> "bank".equals(x.method())).orElseThrow(ApiException::notFound);
        gifts.lockVoucher(first.voucherId());
        return store.lockRefund(id).orElseThrow(ApiException::notFound);
    }
}
