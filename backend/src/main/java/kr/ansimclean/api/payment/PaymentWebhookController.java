package kr.ansimclean.api.payment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import kr.ansimclean.api.common.security.SecurityConfig;

@RestController
@RequestMapping(SecurityConfig.API + "/payments/webhooks/toss")
public class PaymentWebhookController {
    public record Event(@Size(max = 100) String eventType, @Valid Data data) {}
    public record Data(@Size(max = 64) String orderId) {}
    private final CheckoutService service;
    public PaymentWebhookController(CheckoutService service) { this.service = service; }
    @PostMapping
    public void receive(@Valid @RequestBody Event event) {
        if ("PAYMENT_STATUS_CHANGED".equals(event.eventType()) && event.data() != null) service.webhook(event.data().orderId());
    }
}
