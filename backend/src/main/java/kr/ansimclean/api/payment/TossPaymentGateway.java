package kr.ansimclean.api.payment;

import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;

/** PG I/O 전용. 결과는 저장된 주문번호·키·금액·통화와 대조한 뒤에만 DB에 적용한다. */
@Component
public class TossPaymentGateway {
    public record Result(String paymentKey, String orderId, String status, String currency,
                         long totalAmount, long balanceAmount) {}
    private final RestClient client;

    @Autowired // RestClient를 받는 테스트 생성자와 구분해 실제 기동 시 사용할 생성자를 지정한다.
    public TossPaymentGateway(CheckoutProperties properties) {
        var http = java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(10));
        client = RestClient.builder().baseUrl("https://api.tosspayments.com/v1")
                .requestFactory(factory).defaultHeaders(h -> h.setBasicAuth(properties.secretKey(), "")).build();
    }

    // 테스트에서는 실제 HTTP 서버 대신 MockRestServiceServer를 연결한다.
    TossPaymentGateway(RestClient client) { this.client = client; }

    public Result lookup(String orderId) {
        try {
            return client.get().uri("/payments/orders/{id}", orderId).retrieve().body(Result.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) return null;
            throw unavailable();
        } catch (org.springframework.web.client.RestClientException e) { throw unavailable(); }
    }

    public Result confirm(String orderId, String paymentKey, long amount) {
        try {
            return client.post().uri("/payments/confirm").header("Idempotency-Key", "confirm-" + orderId)
                    .body(Map.of("orderId", orderId, "paymentKey", paymentKey, "amount", amount))
                    .retrieve().body(Result.class);
        } catch (org.springframework.web.client.RestClientException e) {
            // 4xx도 바로 실패 확정하지 않는다. 다음 PG 조회로 결과를 확인한다.
            throw unavailable();
        }
    }

    public Result cancel(String orderId, String paymentKey, java.util.UUID refundId) {
        try {
            return client.post().uri("/payments/{key}/cancel", paymentKey)
                    .header("Idempotency-Key", "refund-" + refundId)
                    .body(Map.of("cancelReason", "예약 취소"))
                    .retrieve().body(Result.class);
        } catch (org.springframework.web.client.RestClientException e) { throw unavailable(); }
    }

    private ApiException unavailable() {
        return new ApiException(ErrorCode.UPSTREAM, "결제 결과를 확인하고 있어요. 잠시 후 다시 확인해주세요");
    }
}
