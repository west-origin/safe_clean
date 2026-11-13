package kr.ansimclean.api.gift;

import kr.ansimclean.api.ManagerMvc;
import kr.ansimclean.api.common.config.AppProperties;
import kr.ansimclean.api.common.config.CorsConfig;
import kr.ansimclean.api.common.error.GlobalExceptionHandler;
import kr.ansimclean.api.common.security.AccessPolicy;
import kr.ansimclean.api.common.security.AdminTokens;
import kr.ansimclean.api.common.security.CurrentUserResolver;
import kr.ansimclean.api.common.security.JwtToCurrentUserConverter;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.gift.dto.BookingVoucherResponse;
import kr.ansimclean.api.gift.dto.RefundRequest;
import kr.ansimclean.api.gift.dto.RefundResultResponse;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 금액권 사용·환불(고객)과 계좌 환불 처리(어드민 + MFA)의 권한·JSON 모양 (api.md '금액권 사용·환불'). 서비스는 가짜 */
@WebMvcTest({ VoucherController.class, AdminVoucherRefundController.class })
@Import({ SecurityConfig.class, JwtToCurrentUserConverter.class, CorsConfig.class, GlobalExceptionHandler.class, AccessPolicy.class })
@EnableConfigurationProperties(AppProperties.class)
@ActiveProfiles("test")
class VoucherControllerTest {
    @Autowired
    MockMvc mvc;
    @MockitoBean
    VoucherService service;
    @MockitoBean
    AdminVoucherRefundService admin;
    @MockitoBean
    JwtDecoder jwtDecoder;
    @MockitoBean
    CurrentUserResolver resolver;

    UUID customer;
    final UUID booking = UUID.randomUUID();
    final UUID voucher = UUID.randomUUID();

    @BeforeEach
    void stub() {
        AdminTokens.stub(jwtDecoder, resolver);
        customer = ManagerMvc.customer(jwtDecoder, resolver, "c");
    }

    @Test
    void 예약_결제에_금액권_쓰기와_빼기() throws Exception {
        when(service.apply(customer, booking, voucher)).thenReturn(new BookingVoucherResponse(booking, voucher, "원룸 1회 청소", 69000, "69,000원", 16000, "16,000원", false));
        mvc.perform(post("/api/v1/customer/bookings/" + booking + "/voucher").header("Authorization", "Bearer c").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voucherId\":\"" + voucher + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedAmount").value(69000))
                .andExpect(jsonPath("$.remainingDue").value(16000))
                .andExpect(jsonPath("$.coveredByVoucher").value(false));
        mvc.perform(post("/api/v1/customer/bookings/" + booking + "/voucher").header("Authorization", "Bearer c").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/customer/bookings/" + booking + "/voucher").header("Authorization", "Bearer c")).andExpect(status().isOk());
        verify(service).remove(customer, booking);
        mvc.perform(get("/api/v1/customer/bookings/" + booking + "/voucher").header("Authorization", AdminTokens.bearer(AdminTokens.MANAGER_MFA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 환불_접수_모양() throws Exception {
        UUID refund = UUID.randomUUID();
        when(service.requestRefund(eq(customer), eq("k"), eq(voucher), any(RefundRequest.class))).thenReturn(new RefundResultResponse(refund, "bank", "requested", 100, 69000, "69,000원"));
        mvc.perform(post("/api/v1/customer/vouchers/" + voucher + "/refund").header("Authorization", "Bearer c").header("Idempotency-Key", "k")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bankName\":\"국민은행\",\"accountNumber\":\"12345678901\",\"accountHolder\":\"김고객\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundId").value(refund.toString()))
                .andExpect(jsonPath("$.accountNumber").doesNotExist());
    }

    @Test
    void 어드민_환불_처리는_어드민_MFA_만() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(get("/api/v1/admin/voucher-refunds").header("Authorization", "Bearer c")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/voucher-refunds").header("Authorization", AdminTokens.bearer(AdminTokens.ADMIN_NO_MFA))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/voucher-refunds/" + id + "/pay").header("Authorization", AdminTokens.bearer(AdminTokens.ADMIN_MFA))
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"송금\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("paid"));
        verify(admin).pay(any(), eq(id), eq("송금"));
    }
}
