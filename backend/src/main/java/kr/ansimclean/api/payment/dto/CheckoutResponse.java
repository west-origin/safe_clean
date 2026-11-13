package kr.ansimclean.api.payment.dto;
import java.util.UUID;
/**
 * paymentKey/시크릿 키는 응답하지 않는다. orderId는 payments.id와 같다.
 * 예약 결제면 bookingId, 금액권 선물 결제면 giftId, 주 N회 요금제 한 달치 결제면 seriesId (V074 — 나머지는 null)
 */
public record CheckoutResponse(UUID id, UUID bookingId, String orderId, long amount, String currency,
                               String status, long refundedAmount, String clientKey, String orderName,
                               boolean payable, String refundStatus, UUID giftId, UUID seriesId) {
    /** 시리즈 칸이 없는 모양 (예약·선물 결제) */
    public CheckoutResponse(UUID id, UUID bookingId, String orderId, long amount, String currency, String status, long refundedAmount,
                            String clientKey, String orderName, boolean payable, String refundStatus, UUID giftId) {
        this(id, bookingId, orderId, amount, currency, status, refundedAmount, clientKey, orderName, payable, refundStatus, giftId, null);
    }
}
