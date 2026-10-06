package kr.ansimclean.api.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 통합 테스트 (Docker 필요): Flyway 마이그레이션 전부가 빈 Postgres 에 올라가고, 카탈로그·약관 응답이 앱 mock.js 와 JSON 단위로 같다.
 * 기대값은 scripts/backend-mock-export.mjs shapes 가 mock.js 에서 만든 src/test/resources/mock-shapes/*.json.
 * 통합 테스트의 기본 구성·실행 방법은 CODE_GUIDE.md 3-12. {@code @AutoConfigureMockMvc}: 실제 포트 없이 HTTP 요청을 흉내 내는 MockMvc 를 준다.
 * {@code @ServiceConnection}: 띄운 DB 주소를 스프링 설정에 자동으로 넣는다. {@code @ActiveProfiles("test")}: application-test.yml 을 쓴다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class CatalogIT {
    // static 이라 이 클래스의 테스트 전체가 컨테이너 하나를 같이 쓴다
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    MockMvc mvc;

    final JsonMapper json = JsonMapper.builder().build(); // 응답·기대 파일을 JSON 트리로 읽는 도구

    // @ParameterizedTest + @CsvSource: 아래 한 줄마다 (기대 파일 이름, 요청 경로) 를 넣어 같은 테스트를 한 번씩 돈다
    @ParameterizedTest
    @CsvSource({
            "category-home, /api/v1/catalog/categories/home",
            "category-office, /api/v1/catalog/categories/office",
            "category-appliance, /api/v1/catalog/categories/appliance",
            "category-move-in, /api/v1/catalog/categories/move-in",
            "options-home, /api/v1/catalog/categories/home/options",
            "options-office, /api/v1/catalog/categories/office/options",
            "options-appliance, /api/v1/catalog/categories/appliance/options",
            "options-move-in, /api/v1/catalog/categories/move-in/options",
            "notice-chips-home, /api/v1/catalog/categories/home/notice-chips",
            "notice-chips-office, /api/v1/catalog/categories/office/notice-chips",
            "notice-chips-appliance, /api/v1/catalog/categories/appliance/notice-chips",
            "notice-chips-move-in, /api/v1/catalog/categories/move-in/notice-chips",
            "confirm-copy-home, /api/v1/catalog/categories/home/confirm-copy",
            "confirm-copy-office, /api/v1/catalog/categories/office/confirm-copy",
            "confirm-copy-appliance, /api/v1/catalog/categories/appliance/confirm-copy",
            "confirm-copy-move-in, /api/v1/catalog/categories/move-in/confirm-copy",
            "time-slots, /api/v1/catalog/time-slots",
            "reasons-reject, /api/v1/catalog/reasons?kind=reject",
            "reasons-pause, /api/v1/catalog/reasons?kind=pause",
            "terms-customer, /api/v1/terms?audience=customer",
            "terms-manager, /api/v1/terms?audience=manager",
            "term-customer-service, /api/v1/terms/service?audience=customer",
            "term-manager-recording, /api/v1/terms/recording?audience=manager",
            "term-manager-privacy, /api/v1/terms/privacy?audience=manager",
    })
    void 응답이_앱_mock_과_같다(String shape, String path) throws Exception {
        // 로그인 없이 GET → 200 이어야 하고, 본문을 UTF-8 글자로 받는다 (한국어 문구가 깨지지 않게)
        String body = mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        // 기대 파일은 테스트 리소스 폴더(src/test/resources)에서 읽는다. 글자 그대로가 아니라 JSON 트리로 비교해 공백·줄바꿈 차이는 보지 않는다
        JsonNode expected = json.readTree(getClass().getResourceAsStream("/mock-shapes/" + shape + ".json"));
        assertThat(json.readTree(body)).isEqualTo(expected);
    }

    // 없는 카테고리·약관은 404. 마지막 줄: audience 가 customer·manager 가 아니면 400 (TermsService 가 VALIDATION 을 던진다)
    @Test
    void 없는_카테고리와_없는_약관은_404() throws Exception {
        mvc.perform(get("/api/v1/catalog/categories/nope")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/terms/nope?audience=customer")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/terms?audience=robot")).andExpect(status().isBadRequest());
    }

    // 서버 상태 확인 주소는 로그인 없이 열린다(SecurityConfig). 여기서는 200 인지만 본다
    @Test
    void health_는_UP() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
