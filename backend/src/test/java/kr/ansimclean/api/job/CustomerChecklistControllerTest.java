package kr.ansimclean.api.job;

import kr.ansimclean.api.ManagerMvc;
import kr.ansimclean.api.common.config.AppProperties;
import kr.ansimclean.api.common.config.CorsConfig;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.GlobalExceptionHandler;
import kr.ansimclean.api.common.security.CurrentUserResolver;
import kr.ansimclean.api.common.security.JwtToCurrentUserConverter;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.job.dto.CustomerChecklistResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerChecklistController.class)
@Import({ SecurityConfig.class, JwtToCurrentUserConverter.class, CorsConfig.class, GlobalExceptionHandler.class })
@EnableConfigurationProperties(AppProperties.class)
@ActiveProfiles("test")
class CustomerChecklistControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean JobService service;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean CurrentUserResolver resolver;

    @Test
    void 고객_응답_계약과_남의_예약_404() throws Exception {
        UUID customer = ManagerMvc.customer(jwtDecoder, resolver, "c");
        UUID booking = UUID.randomUUID();
        UUID item = UUID.randomUUID();
        when(service.customerChecklist(customer, booking)).thenReturn(new CustomerChecklistResponse(1, 2, 50,
                List.of(new CustomerChecklistResponse.Group("거실", 1, 2, List.of(new CustomerChecklistResponse.Item(item, "바닥 청소", true)))),
                List.of(new CustomerChecklistResponse.Mark(item, "거실", "바닥 청소", 165L)), List.of("요청")));
        mvc.perform(get("/api/v1/customer/bookings/" + booking + "/checklist").header("Authorization", "Bearer c"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.done").value(1)).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.percent").value(50)).andExpect(jsonPath("$.groups[0].items[0].done").value(true))
                .andExpect(jsonPath("$.timeline[0].offsetSeconds").value(165)).andExpect(jsonPath("$.requests[0]").value("요청"));
        UUID other = UUID.randomUUID();
        when(service.customerChecklist(customer, other)).thenThrow(ApiException.notFound());
        mvc.perform(get("/api/v1/customer/bookings/" + other + "/checklist").header("Authorization", "Bearer c")).andExpect(status().isNotFound());
    }

    @Test
    void 고객만_읽을_수_있다() throws Exception {
        ManagerMvc.manager(jwtDecoder, resolver, "m");
        String path = "/api/v1/customer/bookings/" + UUID.randomUUID() + "/checklist";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization", "Bearer m")).andExpect(status().isForbidden());
    }
}
