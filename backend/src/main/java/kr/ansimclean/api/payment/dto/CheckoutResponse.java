package kr.ansimclean.api.payment.dto;
import java.util.UUID;
/** paymentKey/시크릿 키는 응답하지 않는다. orderId는 payments.id와 같다. 예약 결제면 bookingId, 금액권 선물 결제면 giftId (나머지 하나는 null) */
public record CheckoutResponse(UUID id, UUID bookingId, String orderId, long amount, String currency,
                               String status, long refundedAmount, String clientKey, String orderName,
                               boolean payable, String refundStatus, UUID giftId) {}
