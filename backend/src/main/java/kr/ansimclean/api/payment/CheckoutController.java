package kr.ansimclean.api.payment;

import java.util.UUID;
import jakarta.validation.Valid;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.payment.dto.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(SecurityConfig.API + "/customer")
public class CheckoutController {
    private final CheckoutService service;
    public CheckoutController(CheckoutService service) { this.service = service; }
    @PostMapping("/bookings/{bookingId}/checkout")
    public CheckoutResponse prepare(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID bookingId,
                                    @RequestHeader("Idempotency-Key") String key) { return service.prepare(user.id(), bookingId, key); }
    @GetMapping("/payments/{id}")
    public CheckoutResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) { return service.get(user.id(), id); }
    @PostMapping("/payments/{id}/confirm")
    public CheckoutResponse confirm(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
                                    @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ConfirmPaymentRequest req) {
        return service.confirm(user.id(), id, key, req);
    }
}
