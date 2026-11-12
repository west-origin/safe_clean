package kr.ansimclean.api.banner;

import kr.ansimclean.api.banner.dto.AdminBannerResponse;
import kr.ansimclean.api.banner.dto.HomeBannerResponse;
import kr.ansimclean.api.common.config.AppProperties;
import kr.ansimclean.api.common.config.CorsConfig;
import kr.ansimclean.api.common.error.GlobalExceptionHandler;
import kr.ansimclean.api.common.security.AccessPolicy;
import kr.ansimclean.api.common.security.AdminTokens;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.CurrentUserResolver;
import kr.ansimclean.api.common.security.JwtToCurrentUserConverter;
import kr.ansimclean.api.common.security.Roles;
import kr.ansimclean.api.common.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /admin/banners 의 권한(어드민 + MFA)·multipart·JSON 모양, /customer/banners 의 권한 (api.md "어드민 배너"). 서비스는 가짜 */
@WebMvcTest({ AdminBannerController.class, CustomerBannerController.class })
@Import({ SecurityConfig.class, JwtToCurrentUserConverter.class, CorsConfig.class, GlobalExceptionHandler.class, AccessPolicy.class })
@EnableConfigurationProperties(AppProperties.class)
@ActiveProfiles("test")
class AdminBannerControllerTest {
    @Autowired
    MockMvc mvc;
    @MockitoBean
    AdminBannerService service;
    @MockitoBean
    BannerService customer;
    @MockitoBean
    JwtDecoder jwtDecoder;
    @MockitoBean
    CurrentUserResolver resolver;

    static final String ADMIN = AdminTokens.bearer(AdminTokens.ADMIN_MFA);
    final UUID id = UUID.randomUUID();
    final AdminBannerResponse sample = new AdminBannerResponse(id, "첫 청소 쿠폰", "신규 고객", "https://signed", "2026-11-01", null, "11월 1일부터", "live", "게시 중");
    final MockMultipartFile file = new MockMultipartFile("file", "b.png", "image/png", new byte[] { 1, 2, 3 });

    @BeforeEach
    void stub() {
        AdminTokens.stub(jwtDecoder, resolver);
    }

    @Test
    void 어드민_MFA_가_아니면_배너_관리가_막힌다() throws Exception {
        mvc.perform(get("/api/v1/admin/banners")).andExpect(status().isUnauthorized());
        for (String token : new String[] { AdminTokens.ADMIN_NO_MFA, AdminTokens.MANAGER_MFA }) {
            String auth = AdminTokens.bearer(token);
            mvc.perform(get("/api/v1/admin/banners").header("Authorization", auth)).andExpect(status().isForbidden());
            mvc.perform(multipart("/api/v1/admin/banners").file(file).param("title", "t").header("Authorization", auth)).andExpect(status().isForbidden());
            mvc.perform(delete("/api/v1/admin/banners/" + id).header("Authorization", auth)).andExpect(status().isForbidden());
        }
        verify(service, never()).create(any(), any(), any(), any());
    }

    @Test
    void 올리기는_파일과_문구를_multipart_로_받고_응답_모양() throws Exception {
        when(service.create(any(), eq("image/png"), any(), any())).thenReturn(sample);
        mvc.perform(multipart("/api/v1/admin/banners").file(file).param("title", "첫 청소 쿠폰").param("description", "신규 고객").param("startsOn", "2026-11-01")
                        .header("Authorization", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString())).andExpect(jsonPath("$.imageUrl").value("https://signed"))
                .andExpect(jsonPath("$.startsOn").value("2026-11-01")).andExpect(jsonPath("$.endsOn").doesNotExist())
                .andExpect(jsonPath("$.periodLabel").value("11월 1일부터")).andExpect(jsonPath("$.state").value("live")).andExpect(jsonPath("$.stateLabel").value("게시 중"));
        verify(service).create(any(), eq("image/png"), eq(new byte[] { 1, 2, 3 }), eq(new kr.ansimclean.api.banner.dto.AdminBannerTextRequest("첫 청소 쿠폰", "신규 고객", "2026-11-01", null)));
    }

    @Test
    void 문구_수정_이미지_교체_순서_삭제_경로() throws Exception {
        when(service.update(any(), eq(id), any())).thenReturn(sample);
        mvc.perform(put("/api/v1/admin/banners/" + id).header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"t\",\"description\":\"\",\"startsOn\":null,\"endsOn\":\"2026-12-31\"}")).andExpect(status().isOk());
        when(service.replaceImage(any(), eq(id), eq("image/png"), any())).thenReturn(sample);
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/admin/banners/" + id + "/image").file(file).header("Authorization", ADMIN)).andExpect(status().isOk());
        when(service.reorder(any(), eq(List.of(id)))).thenReturn(List.of(sample));
        mvc.perform(put("/api/v1/admin/banners/order").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[\"" + id + "\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id.toString()));
        mvc.perform(delete("/api/v1/admin/banners/" + id).header("Authorization", ADMIN)).andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
        verify(service).delete(any(), eq(id));
    }

    @Test
    void 고객_배너는_고객만_본다() throws Exception {
        UUID cid = UUID.randomUUID();
        when(jwtDecoder.decode("customer")).thenReturn(Jwt.withTokenValue("customer").header("alg", "HS256").subject(cid.toString()).claim("aud", List.of("authenticated"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build());
        when(resolver.resolve(cid)).thenReturn(Optional.of(new CurrentUser(cid, Roles.CUSTOMER, true)));
        when(customer.liveBanners()).thenReturn(List.of(new HomeBannerResponse(id, "첫 청소 쿠폰", "신규 고객", "https://signed")));
        mvc.perform(get("/api/v1/customer/banners").header("Authorization", "Bearer customer")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("첫 청소 쿠폰")).andExpect(jsonPath("$[0].description").value("신규 고객"))
                .andExpect(jsonPath("$[0].imageUrl").value("https://signed"));
        mvc.perform(get("/api/v1/customer/banners").header("Authorization", AdminTokens.bearer(AdminTokens.MANAGER_MFA))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/customer/banners")).andExpect(status().isUnauthorized());
    }
}
