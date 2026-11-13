package kr.ansimclean.api.gift;

import jakarta.validation.Valid;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.gift.dto.ApplyVoucherRequest;
import kr.ansimclean.api.gift.dto.BookingVoucherResponse;
import kr.ansimclean.api.gift.dto.RefundQuoteResponse;
import kr.ansimclean.api.gift.dto.RefundRequest;
import kr.ansimclean.api.gift.dto.RefundResultResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 고객 금액권 사용(예약 결제)·환불 (docs/api.md '금액권 사용·환불'). 가입을 끝낸 고객만 */
@RestController
@RequestMapping(SecurityConfig.API + "/customer")
public class VoucherController {
    private final VoucherService service;

    public VoucherController(VoucherService service) {
        this.service = service;
    }

    /** 이 예약 결제에 쓰는 금액권 (앱 getBookingVoucher) */
    @GetMapping("/bookings/{bookingId}/voucher")
    public BookingVoucherResponse current(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID bookingId) {
        return service.current(user.id(), bookingId);
    }

    /** 금액권 쓰기 (앱 applyVoucher) — 예약당 1장, 카드 결제 승인 요청 전에만 */
    @PostMapping("/bookings/{bookingId}/voucher")
    public BookingVoucherResponse apply(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID bookingId, @Valid @RequestBody ApplyVoucherRequest req) {
        return service.apply(user.id(), bookingId, req.voucherId());
    }

    /** 금액권 빼기 (앱 removeVoucher) */
    @DeleteMapping("/bookings/{bookingId}/voucher")
    public BookingVoucherResponse remove(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID bookingId) {
        return service.remove(user.id(), bookingId);
    }

    /** 지금 환불하면 (앱 getVoucherRefund) */
    @GetMapping("/vouchers/{id}/refund")
    public RefundQuoteResponse quote(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return service.quote(user.id(), id);
    }

    /** 환불 접수 (앱 requestVoucherRefund). Idempotency-Key 선택 */
    @PostMapping("/vouchers/{id}/refund")
    public RefundResultResponse refund(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id, @Valid @RequestBody(required = false) RefundRequest req,
                                       @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return service.requestRefund(user.id(), key, id, req);
    }
}
