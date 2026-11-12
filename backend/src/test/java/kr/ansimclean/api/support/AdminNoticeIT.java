package kr.ansimclean.api.support;

import kr.ansimclean.api.ItSupport;
import kr.ansimclean.api.common.activity.ActivityLogService;
import kr.ansimclean.api.profile.ProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 통합 (Docker 필요): 어드민이 공지를 올리면 고객 앱 목록(GET /support/notices?audience=customer)에 바로 보이고,
 * 매니저 대상으로 바꾸면 고객 목록에서 빠지며, 지우면 사라진다. 세 작업 모두 activity_logs 에 한 줄씩, 같은 키 재시도는 한 번만 올린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class AdminNoticeIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    MockMvc mvc;
    @Autowired
    ProfileService profiles;
    @Autowired
    ActivityLogService activity;
    @MockitoBean
    JwtDecoder jwtDecoder;

    final JsonMapper json = JsonMapper.builder().build();

    @Test
    void 올린_공지는_고객_목록에_바로_보이고_수정_삭제가_반영되며_이력이_남는다() throws Exception {
        ItSupport.User admin = ItSupport.adminWithMfa(profiles, jwtDecoder);
        String body = "{\"audience\":\"customer\",\"title\":\"설 연휴 안내\",\"body\":\"연휴에도 예약할 수 있어요\"}";
        String created = mvc.perform(post("/api/v1/admin/notices").header("Authorization", admin.bearer()).header("Idempotency-Key", "notice-it-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String id = json.readTree(created).get("id").asString();
        // 같은 키로 다시 보내도 같은 공지 하나
        mvc.perform(post("/api/v1/admin/notices").header("Authorization", admin.bearer()).header("Idempotency-Key", "notice-it-1")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));

        mvc.perform(get("/api/v1/support/notices?audience=customer")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].title").value("설 연휴 안내"));
        mvc.perform(get("/api/v1/support/notices?audience=manager")).andExpect(jsonPath("$[*].id", not(hasItem(id))));

        mvc.perform(put("/api/v1/admin/notices/" + id).header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"audience\":\"manager\",\"title\":\"매니저 공지\",\"body\":\"본문\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/support/notices?audience=customer")).andExpect(jsonPath("$[*].id", not(hasItem(id))));
        mvc.perform(get("/api/v1/support/notices?audience=manager")).andExpect(jsonPath("$[*].id", hasItem(id)));

        mvc.perform(delete("/api/v1/admin/notices/" + id).header("Authorization", admin.bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/notices/" + id).header("Authorization", admin.bearer())).andExpect(status().isNotFound());

        assertThat(activity.history("notice", id)).extracting("action")
                .containsExactlyInAnyOrder(ActivityLogService.NOTICE_DELETE, ActivityLogService.NOTICE_UPDATE, ActivityLogService.NOTICE_CREATE);
    }
}
