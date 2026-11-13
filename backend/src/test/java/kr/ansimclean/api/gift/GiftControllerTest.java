package kr.ansimclean.api.gift;

import kr.ansimclean.api.ManagerMvc;
import kr.ansimclean.api.common.config.AppProperties;
import kr.ansimclean.api.common.config.CorsConfig;
import kr.ansimclean.api.common.error.GlobalExceptionHandler;
import kr.ansimclean.api.common.security.AccessPolicy;
import kr.ansimclean.api.common.security.CurrentUserResolver;
import kr.ansimclean.api.common.security.JwtToCurrentUserConverter;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.friend.dto.FriendUser;
import kr.ansimclean.api.gift.dto.CatalogItem;
import kr.ansimclean.api.gift.dto.CreateGiftRequest;
import kr.ansimclean.api.gift.dto.GiftCatalogResponse;
import kr.ansimclean.api.gift.dto.GiftDetailResponse;
import kr.ansimclean.api.gift.dto.GiftView;
import kr.ansimclean.api.payment.dto.CheckoutResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /customer/gifts/** · /customer/vouchers 의 권한·JSON 모양 (api.md '금액권·선물'). 서비스는 가짜 */
@WebMvcTest(GiftController.class)
@Import({ SecurityConfig.class, JwtToCurrentUserConverter.class, CorsConfig.class, GlobalExceptionHandler.class, AccessPolicy.class })
@EnableConfigurationProperties(AppProperties.class)
@ActiveProfiles("test")
class GiftControllerTest {
    @Autowired
    MockMvc mvc;
    @MockitoBean
    GiftService service;
    @MockitoBean
    JwtDecoder jwtDecoder;
    @MockitoBean
    CurrentUserResolver resolver;

    UUID customer;
    final UUID friend = UUID.randomUUID();

    @BeforeEach
    void stub() {
        customer = ManagerMvc.customer(jwtDecoder, resolver, "c");
        ManagerMvc.manager(jwtDecoder, resolver, "m");
    }

    @Test
    void 고객만_쓸_수_있다() throws Exception {
        mvc.perform(get("/api/v1/customer/vouchers")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/customer/vouchers").header("Authorization", "Bearer m")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/customer/gifts").header("Authorization", "Bearer m").contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipientId\":\"" + friend + "\",\"method\":\"purchase\",\"catalogKey\":\"studio_once\"}")).andExpect(status().isForbidden());
    }

    @Test
    void 목록과_구매_선물의_JSON_모양() throws Exception {
        when(service.catalog()).thenReturn(new GiftCatalogResponse(List.of(new CatalogItem("studio_once", "원룸 정기청소", "원룸 1회 청소 (1회 2시간)", 69000, "69,000원")), 7, 365));
        mvc.perform(get("/api/v1/customer/gifts/catalog").header("Authorization", "Bearer c"))
                .andExpect(jsonPath("$.items[0].key").value("studio_once"))
                .andExpect(jsonPath("$.items[0].amountLabel").value("69,000원"))
                .andExpect(jsonPath("$.acceptDays").value(7));

        UUID giftId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        var view = new GiftView(giftId, "sent", "purchase", "awaiting_payment", 69000, "69,000원", "원룸 1회 청소 (1회 2시간)", "축하해",
                new FriendUser(friend, "청소왕", "0420", "청소왕#0420", "김*수"), "2026-11-13T03:00:00Z", null, null, null, null);
        var pay = new CheckoutResponse(orderId, null, orderId.toString(), 69000, "KRW", "pending", 0, "test_ck_stub", "안심클린 금액권 선물", true, null, giftId);
        when(service.create(eq(customer), eq("k1"), any(CreateGiftRequest.class))).thenReturn(new GiftDetailResponse(view, pay));
        mvc.perform(post("/api/v1/customer/gifts").header("Authorization", "Bearer c").header("Idempotency-Key", "k1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientId\":\"" + friend + "\",\"method\":\"purchase\",\"catalogKey\":\"studio_once\",\"message\":\"축하해\",\"amount\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gift.status").value("awaiting_payment"))
                .andExpect(jsonPath("$.gift.person.handle").value("청소왕#0420"))
                .andExpect(jsonPath("$.checkout.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.checkout.giftId").value(giftId.toString()))
                .andExpect(jsonPath("$.checkout.bookingId").isEmpty());
        // 앱이 보낸 amount 는 요청 모양에 없어 버려진다 — 금액은 서버 목록 값만
        verify(service).create(eq(customer), eq("k1"), eq(new CreateGiftRequest(friend, "purchase", "studio_once", null, "축하해")));

        mvc.perform(post("/api/v1/customer/gifts").header("Authorization", "Bearer c").contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"purchase\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/customer/gifts/" + giftId + "/accept").header("Authorization", "Bearer c")).andExpect(status().isOk());
        verify(service).accept(customer, giftId);
    }
}
