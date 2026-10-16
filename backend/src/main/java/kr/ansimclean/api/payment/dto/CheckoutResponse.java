package kr.ansimclean.api.payment.dto;
import java.util.UUID;
/** paymentKey/시크릿 키는 응답하지 않는다. orderId는 payments.id와 같다. */
public record CheckoutResponse(UUID id, UUID bookingId, String orderId, long amount, String currency,
                               String status, long refundedAmount, String clientKey, String orderName,
                               boolean payable, String refundStatus) {}
