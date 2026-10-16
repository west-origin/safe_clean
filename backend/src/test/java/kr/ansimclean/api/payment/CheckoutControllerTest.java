package kr.ansimclean.api.payment;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import kr.ansimclean.api.common.config.*;
import kr.ansimclean.api.common.error.GlobalExceptionHandler;
import kr.ansimclean.api.common.security.*;
import kr.ansimclean.api.payment.dto.CheckoutResponse;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({CheckoutController.class, PaymentWebhookController.class})
@Import({SecurityConfig.class, JwtToCurrentUserConverter.class, CorsConfig.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties(AppProperties.class)
@ActiveProfiles("test")
class CheckoutControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CheckoutService service;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean CurrentUserResolver resolver;
    UUID login(String role) {
        UUID id = UUID.randomUUID();
        var jwt = Jwt.withTokenValue("token").header("alg", "HS256").subject(id.toString())
                .claim("aud", List.of("authenticated")).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        when(jwtDecoder.decode("token")).thenReturn(jwt);
        when(resolver.resolve(id)).thenReturn(Optional.of(new CurrentUser(id, role, true)));
        return id;
    }
    @Test void endpoints_require_customer_role() throws Exception {
        String url = "/api/v1/customer/payments/" + UUID.randomUUID();
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        login(Roles.MANAGER);
        mvc.perform(get(url).header("Authorization", "Bearer token")).andExpect(status().isForbidden());
    }
    @Test void checkout_shape_has_no_provider_transaction_or_secret() throws Exception {
        UUID user = login(Roles.CUSTOMER), booking = UUID.randomUUID(), id = UUID.randomUUID();
        when(service.prepare(user, booking, "key")).thenReturn(new CheckoutResponse(id, booking, id.toString(), 85000, "KRW", "pending", 0, "test_ck_stub", "안심클린 방문 서비스", true, null));
        mvc.perform(post("/api/v1/customer/bookings/" + booking + "/checkout").header("Authorization", "Bearer token").header("Idempotency-Key", "key"))
                .andExpect(status().isOk()).andExpect(jsonPath("amount").value(85000))
                .andExpect(jsonPath("orderId").value(id.toString())).andExpect(jsonPath("currency").value("KRW"))
                .andExpect(jsonPath("paymentKey").doesNotExist()).andExpect(jsonPath("secretKey").doesNotExist());
    }
    @Test void confirmation_requires_idempotency_header_positive_amount_and_key() throws Exception {
        login(Roles.CUSTOMER);
        String url = "/api/v1/customer/payments/" + UUID.randomUUID() + "/confirm";
        mvc.perform(post(url).header("Authorization", "Bearer token").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(url).header("Authorization", "Bearer token").header("Idempotency-Key", "key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"paymentKey\":\"\",\"amount\":0}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void webhook_is_public_but_only_passes_order_id_to_service() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/api/v1/payments/webhooks/toss").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"PAYMENT_STATUS_CHANGED\",\"data\":{\"orderId\":\"" + id + "\",\"status\":\"DONE\",\"paymentKey\":\"forged\"}}"))
                .andExpect(status().isOk());
        verify(service).webhook(id.toString());
    }
}
