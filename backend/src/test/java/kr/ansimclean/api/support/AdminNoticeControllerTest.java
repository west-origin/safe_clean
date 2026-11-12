package kr.ansimclean.api.support;

import kr.ansimclean.api.common.config.AppProperties;
import kr.ansimclean.api.common.config.CorsConfig;
import kr.ansimclean.api.common.error.GlobalExceptionHandler;
import kr.ansimclean.api.common.security.AccessPolicy;
import kr.ansimclean.api.common.security.AdminTokens;
import kr.ansimclean.api.common.security.CurrentUserResolver;
import kr.ansimclean.api.common.security.JwtToCurrentUserConverter;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.support.dto.AdminNoticeResponse;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /admin/notices 의 경로 권한(어드민 + MFA)과 JSON 모양 (api.md "어드민 공지"). 서비스는 가짜 */
@WebMvcTest(AdminNoticeController.class)
@Import({ SecurityConfig.class, JwtToCurrentUserConverter.class, CorsConfig.class, GlobalExceptionHandler.class, AccessPolicy.class })
@EnableConfigurationProperties(AppProperties.class)
@ActiveProfiles("test")
class AdminNoticeControllerTest {
    @Autowired
    MockMvc mvc;
    @MockitoBean
    AdminNoticeService service;
    @MockitoBean
    JwtDecoder jwtDecoder;
    @MockitoBean
    CurrentUserResolver resolver;

    static final String ADMIN = AdminTokens.bearer(AdminTokens.ADMIN_MFA);
    static final String BODY = "{\"audience\":\"customer\",\"title\":\"설 연휴 안내\",\"body\":\"본문\"}";

    @BeforeEach
    void stub() {
        AdminTokens.stub(jwtDecoder, resolver);
    }

    @Test
    void 어드민_MFA_가_아니면_모든_공지_관리_경로가_막힌다() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(get("/api/v1/admin/notices")).andExpect(status().isUnauthorized());
        for (String token : new String[] { AdminTokens.ADMIN_NO_MFA, AdminTokens.MANAGER_MFA }) {
            String auth = AdminTokens.bearer(token);
            mvc.perform(get("/api/v1/admin/notices").header("Authorization", auth)).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/admin/notices").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
            mvc.perform(put("/api/v1/admin/notices/" + id).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
            mvc.perform(delete("/api/v1/admin/notices/" + id).header("Authorization", auth)).andExpect(status().isForbidden());
        }
        verify(service, never()).create(any(), any(), any());
    }

    @Test
    void 목록과_등록_수정_삭제의_응답_모양() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.list(any())).thenReturn(List.of(new AdminNoticeResponse(id, "all", "제목", "2026.11.12", null)));
        mvc.perform(get("/api/v1/admin/notices").header("Authorization", ADMIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString())).andExpect(jsonPath("$[0].audience").value("all"))
                .andExpect(jsonPath("$[0].title").value("제목")).andExpect(jsonPath("$[0].date").value("2026.11.12"));

        AdminNoticeResponse saved = new AdminNoticeResponse(id, "customer", "설 연휴 안내", "2026.11.12", "본문");
        when(service.create(any(), eq("key-1"), any())).thenReturn(saved);
        mvc.perform(post("/api/v1/admin/notices").header("Authorization", ADMIN).header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.body").value("본문")).andExpect(jsonPath("$.audience").value("customer"));

        when(service.update(any(), eq(id), any())).thenReturn(saved);
        mvc.perform(put("/api/v1/admin/notices/" + id).header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("설 연휴 안내"));

        mvc.perform(delete("/api/v1/admin/notices/" + id).header("Authorization", ADMIN)).andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
        verify(service).delete(any(), eq(id));
    }
}
