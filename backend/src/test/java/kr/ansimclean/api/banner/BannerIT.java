package kr.ansimclean.api.banner;

import kr.ansimclean.api.ItSupport;
import kr.ansimclean.api.common.activity.ActivityLogService;
import kr.ansimclean.api.common.supabase.SupabaseStorageClient;
import kr.ansimclean.api.profile.ProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 통합 (Docker 필요): 어드민이 배너 두 장을 올리면 고객 홈 배너(GET /customer/banners)에 순서대로 보이고, 순서를 바꾸면 따라 바뀌며,
 * 기간이 끝난 배너는 빠지고, 지우면 사라지고 옛 파일 삭제가 아웃박스에 남는다. Storage 는 가짜(MockitoBean).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class BannerIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    MockMvc mvc;
    @Autowired
    ProfileService profiles;
    @Autowired
    ActivityLogService activity;
    @Autowired
    JdbcTemplate jdbc;
    @MockitoBean
    JwtDecoder jwtDecoder;
    @MockitoBean
    SupabaseStorageClient storage;

    final JsonMapper json = JsonMapper.builder().build();

    private String upload(ItSupport.User admin, String title, String endsOn) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "b.png", "image/png", BannerTestImages.png(36, 11));
        var req = multipart("/api/v1/admin/banners").file(file).param("title", title).param("description", "설명").header("Authorization", admin.bearer());
        if (endsOn != null) req.param("endsOn", endsOn);
        String body = mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body).get("id").asString();
    }

    @Test
    void 올린_배너는_고객_홈에_순서대로_보이고_순서_기간_삭제가_반영된다() throws Exception {
        when(storage.signedUrl(eq(BannerImages.BUCKET), anyString(), anyInt())).thenAnswer(inv -> "https://signed/" + inv.getArgument(1));
        ItSupport.User admin = ItSupport.adminWithMfa(profiles, jwtDecoder);
        ItSupport.User customer = ItSupport.signedUp(profiles, jwtDecoder, "customer", "고객");
        String a = upload(admin, "첫 청소 쿠폰", null);
        String b = upload(admin, "선물하기", null);
        String ended = upload(admin, "지난 이벤트", "2020-01-01");

        mvc.perform(get("/api/v1/customer/banners").header("Authorization", customer.bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].id").value(a)).andExpect(jsonPath("$[1].id").value(b))
                .andExpect(jsonPath("$[0].title").value("첫 청소 쿠폰"));

        mvc.perform(put("/api/v1/admin/banners/order").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"" + ended + "\",\"" + b + "\",\"" + a + "\"]}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/customer/banners").header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$[0].id").value(b)).andExpect(jsonPath("$[1].id").value(a));

        mvc.perform(delete("/api/v1/admin/banners/" + b).header("Authorization", admin.bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/customer/banners").header("Authorization", customer.bearer())).andExpect(jsonPath("$.length()").value(1));
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where type = 'BannerImageDeleted' and payload->>'path' like ?", Integer.class, b + "/%")).isEqualTo(1);
        assertThat(activity.history("banner", b)).extracting("action").containsExactlyInAnyOrder(ActivityLogService.BANNER_CREATE, ActivityLogService.BANNER_DELETE);
    }
}
