package kr.ansimclean.api.payment;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import kr.ansimclean.api.common.error.ApiException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TossPaymentGatewayTest {
    @Test void spring_can_create_gateway_with_its_production_constructor() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(CheckoutProperties.class, () -> new CheckoutProperties("test_ck_context", "test_sk_context"));
            context.register(TossPaymentGateway.class);
            context.refresh();
            assertThat(context.getBean(TossPaymentGateway.class)).isNotNull();
        }
    }
    @Test void confirmation_uses_server_amount_and_stable_provider_idempotency_key() {
        var builder = RestClient.builder().baseUrl("https://api.tosspayments.com/v1");
        var server = MockRestServiceServer.bindTo(builder).build();
        var gateway = new TossPaymentGateway(builder.build());
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andExpect(header("Idempotency-Key", "confirm-order-123"))
                .andExpect(content().json("{\"orderId\":\"order-123\",\"paymentKey\":\"key\",\"amount\":85000}"))
                .andRespond(withSuccess("{\"orderId\":\"order-123\",\"paymentKey\":\"key\",\"status\":\"DONE\",\"currency\":\"KRW\",\"totalAmount\":85000,\"balanceAmount\":85000,\"card\":{\"number\":\"masked\"}}", MediaType.APPLICATION_JSON));
        assertThat(gateway.confirm("order-123", "key", 85000).status()).isEqualTo("DONE");
        server.verify();
    }
    @Test void unknown_lookup_is_not_treated_as_failed_payment() {
        var builder = RestClient.builder().baseUrl("https://api.tosspayments.com/v1");
        var server = MockRestServiceServer.bindTo(builder).build();
        var gateway = new TossPaymentGateway(builder.build());
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/orders/order-123")).andRespond(withResourceNotFound());
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm")).andRespond(withServerError());
        assertThat(gateway.lookup("order-123")).isNull();
        assertThatThrownBy(() -> gateway.confirm("order-123", "key", 85000)).isInstanceOf(ApiException.class)
                .hasMessageNotContaining("key");
        server.verify();
    }
    @Test void refund_retries_use_the_same_id_and_cancel_the_remaining_balance() {
        var builder = RestClient.builder().baseUrl("https://api.tosspayments.com/v1");
        var server = MockRestServiceServer.bindTo(builder).build();
        var gateway = new TossPaymentGateway(builder.build());
        UUID refund = UUID.randomUUID();
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/key/cancel"))
                .andExpect(header("Idempotency-Key", "refund-" + refund)).andExpect(content().json("{\"cancelReason\":\"예약 취소\"}"))
                .andRespond(withSuccess("{\"orderId\":\"order-123\",\"paymentKey\":\"key\",\"status\":\"CANCELED\",\"currency\":\"KRW\",\"totalAmount\":85000,\"balanceAmount\":0}", MediaType.APPLICATION_JSON));
        assertThat(gateway.cancel("order-123", "key", refund).status()).isEqualTo("CANCELED");
        server.verify();
    }
}
