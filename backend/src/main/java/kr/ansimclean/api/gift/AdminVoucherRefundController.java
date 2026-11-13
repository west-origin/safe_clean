package kr.ansimclean.api.gift;

import jakarta.validation.Valid;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.gift.dto.AdminRefundAccountResponse;
import kr.ansimclean.api.gift.dto.AdminVoucherRefundItem;
import kr.ansimclean.api.gift.dto.RefundDecisionRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 어드민 금액권 계좌 환불 (/admin/voucher-refunds/**, 어드민 + MFA — SecurityConfig + AccessPolicy.VOUCHER_REFUND_MANAGE) */
@RestController
@RequestMapping(SecurityConfig.API + "/admin/voucher-refunds")
public class AdminVoucherRefundController {
    private final AdminVoucherRefundService service;

    public AdminVoucherRefundController(AdminVoucherRefundService service) {
        this.service = service;
    }

    @GetMapping
    public List<AdminVoucherRefundItem> list(@AuthenticationPrincipal CurrentUser actor, @RequestParam(value = "status", required = false) String status) {
        return service.list(actor, status);
    }

    @GetMapping("/{id}/account")
    public AdminRefundAccountResponse account(@AuthenticationPrincipal CurrentUser actor, @PathVariable UUID id) {
        return service.account(actor, id);
    }

    @PostMapping("/{id}/pay")
    public Map<String, Object> pay(@AuthenticationPrincipal CurrentUser actor, @PathVariable UUID id, @Valid @RequestBody(required = false) RefundDecisionRequest req) {
        service.pay(actor, id, req == null ? null : req.note());
        return Map.of("id", id, "status", "paid");
    }

    @PostMapping("/{id}/reject")
    public Map<String, Object> reject(@AuthenticationPrincipal CurrentUser actor, @PathVariable UUID id, @Valid @RequestBody(required = false) RefundDecisionRequest req) {
        service.reject(actor, id, req == null ? null : req.note());
        return Map.of("id", id, "status", "rejected");
    }
}
