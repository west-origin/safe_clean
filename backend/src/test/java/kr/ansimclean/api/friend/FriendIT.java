package kr.ansimclean.api.friend;

import kr.ansimclean.api.ItSupport;
import kr.ansimclean.api.profile.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 통합 (Docker 필요): 실제 Postgres·Flyway V069·advisory lock 으로 친구 흐름과 동시 요청 정합성을 본다.
 * 요청 → 받은/보낸 목록 → 수락 → 친구 목록 → 삭제, 서로 동시 요청 = 친구 1쌍·대기 0건, 차단의 효과, 초대 수락, 같은 닉네임 동시 설정의 태그 유일성, 탈퇴 회원 제외
 */
@SpringBootTest(properties = "app.invite.base-url=https://api.test")
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class FriendIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    MockMvc mvc;
    @Autowired
    ProfileService profiles;
    @Autowired
    JdbcTemplate jdbc;
    @MockitoBean
    JwtDecoder jwtDecoder;

    final JsonMapper json = JsonMapper.builder().build();
    ItSupport.User a;
    ItSupport.User b;

    @BeforeEach
    void setUp() {
        ItSupport.resetUserData(jdbc);
        a = customer("고객A", "에이");
        b = customer("고객B", "비이");
    }

    private ItSupport.User customer(String name, String nickname) {
        ItSupport.User u = ItSupport.signedUp(profiles, jwtDecoder, "customer", name);
        if (nickname != null) profiles.setFriendNickname(u.id(), nickname);
        return u;
    }

    private String tagOf(ItSupport.User u) {
        return jdbc.queryForObject("select friend_tag from profiles where id = ?", String.class, u.id());
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private ResultActions send(ItSupport.User from, ItSupport.User to) throws Exception {
        return mvc.perform(post("/api/v1/customer/friends/requests").header("Authorization", from.bearer()).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + to.id() + "\"}"));
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void 검색_요청_수락_목록_삭제() throws Exception {
        String handle = "비이#" + tagOf(b);
        JsonNode found = body(mvc.perform(get("/api/v1/customer/friends/search").param("q", handle).header("Authorization", a.bearer())).andExpect(status().isOk()));
        assertThat(found.get("user").get("userId").asText()).isEqualTo(b.id().toString());
        assertThat(found.get("user").get("maskedName").asText()).isEqualTo("고*B");
        assertThat(found.get("relation").asText()).isEqualTo("none");

        JsonNode sent = body(send(a, b).andExpect(status().isOk()));
        assertThat(sent.get("status").asText()).isEqualTo("pending");
        String requestId = sent.get("id").asText();
        assertThat(count("select count(*) from outbox_events where type = 'FriendRequested'")).isEqualTo(1);

        // 같은 요청을 다시 보내도 새로 만들지 않는다
        assertThat(body(send(a, b)).get("id").asText()).isEqualTo(requestId);

        mvc.perform(get("/api/v1/customer/friends/requests").param("box", "received").header("Authorization", b.bearer()))
                .andExpect(jsonPath("$.requests[0].requestId").value(requestId));
        mvc.perform(get("/api/v1/customer/friends/requests").param("box", "sent").header("Authorization", a.bearer()))
                .andExpect(jsonPath("$.requests[0].user.userId").value(b.id().toString()));

        // 보낸 사람은 수락할 수 없다 (남의 요청 = 404)
        mvc.perform(post("/api/v1/customer/friends/requests/" + requestId + "/accept").header("Authorization", a.bearer())).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/customer/friends/requests/" + requestId + "/accept").header("Authorization", b.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("accepted"));

        mvc.perform(get("/api/v1/customer/friends").header("Authorization", a.bearer())).andExpect(jsonPath("$.friends[0].user.userId").value(b.id().toString()));
        send(a, b).andExpect(status().isConflict()); // 이미 친구

        mvc.perform(delete("/api/v1/customer/friends/" + a.id()).header("Authorization", b.bearer())).andExpect(status().isOk());
        assertThat(count("select count(*) from friendships")).isZero();
        mvc.perform(get("/api/v1/customer/friends").header("Authorization", a.bearer())).andExpect(jsonPath("$.friends").isEmpty());
    }

    @Test
    void 서로_동시에_요청하면_친구_한_쌍이_되고_대기_요청은_남지_않는다() throws Exception {
        for (int round = 0; round < 5; round++) {
            jdbc.update("delete from friendships");
            jdbc.update("delete from friend_requests");
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<String>> results = new ArrayList<>();
            results.add(pool.submit(() -> { go.await(); return body(send(a, b)).get("status").asText(); }));
            results.add(pool.submit(() -> { go.await(); return body(send(b, a)).get("status").asText(); }));
            go.countDown();
            List<String> statuses = new ArrayList<>();
            for (Future<String> f : results) statuses.add(f.get());
            pool.shutdown();
            assertThat(statuses).containsExactlyInAnyOrder("pending", "accepted");
            assertThat(count("select count(*) from friendships")).isEqualTo(1);
            assertThat(count("select count(*) from friend_requests where status = 'pending'")).isZero();
            assertThat(count("select count(*) from friend_requests where status = 'accepted'")).isEqualTo(1);
        }
    }

    @Test
    void 수락과_취소가_동시에_와도_하나만_된다() throws Exception {
        for (int round = 0; round < 5; round++) {
            jdbc.update("delete from friendships");
            jdbc.update("delete from friend_requests");
            String id = body(send(a, b)).get("id").asText();
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch go = new CountDownLatch(1);
            Future<Integer> accept = pool.submit(() -> { go.await(); return mvc.perform(post("/api/v1/customer/friends/requests/" + id + "/accept").header("Authorization", b.bearer())).andReturn().getResponse().getStatus(); });
            Future<Integer> cancel = pool.submit(() -> { go.await(); return mvc.perform(post("/api/v1/customer/friends/requests/" + id + "/cancel").header("Authorization", a.bearer())).andReturn().getResponse().getStatus(); });
            go.countDown();
            List<Integer> codes = List.of(accept.get(), cancel.get());
            pool.shutdown();
            assertThat(codes).containsExactlyInAnyOrder(200, 409);
            String status = jdbc.queryForObject("select status from friend_requests where id = ?::uuid", String.class, id);
            assertThat(count("select count(*) from friendships")).isEqualTo("accepted".equals(status) ? 1 : 0);
        }
    }

    @Test
    void 차단하면_친구가_풀리고_상대는_검색_요청을_못_한다() throws Exception {
        String id = body(send(a, b)).get("id").asText();
        mvc.perform(post("/api/v1/customer/friends/requests/" + id + "/accept").header("Authorization", b.bearer())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/customer/friends/blocks").header("Authorization", b.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + a.id() + "\"}")).andExpect(status().isOk());
        assertThat(count("select count(*) from friendships")).isZero();

        mvc.perform(get("/api/v1/customer/friends/search").param("q", "비이#" + tagOf(b)).header("Authorization", a.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user").isEmpty());
        send(a, b).andExpect(status().isNotFound());

        mvc.perform(delete("/api/v1/customer/friends/blocks/" + a.id()).header("Authorization", b.bearer())).andExpect(status().isOk());
        send(a, b).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("pending")); // 해제 뒤엔 다시 요청할 수 있다 (친구로 돌아오지는 않는다)
    }

    @Test
    void 초대_링크를_수락하면_바로_친구_차단한_상대는_못_쓴다() throws Exception {
        JsonNode invite = body(mvc.perform(post("/api/v1/customer/friends/invites").header("Authorization", a.bearer())).andExpect(status().isOk()));
        String code = invite.get("code").asText();
        assertThat(invite.get("url").asText()).isEqualTo("https://api.test/i/" + code);
        mvc.perform(get("/i/" + code)).andExpect(status().isOk()); // 공개 페이지

        mvc.perform(get("/api/v1/customer/friends/invites/" + code).header("Authorization", b.bearer()))
                .andExpect(jsonPath("$.inviter.userId").value(a.id().toString())).andExpect(jsonPath("$.relation").value("none"));
        mvc.perform(post("/api/v1/customer/friends/invites/" + code + "/accept").header("Authorization", b.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("accepted"));
        assertThat(count("select count(*) from friend_requests where source = 'invite' and status = 'accepted'")).isEqualTo(1);
        assertThat(count("select count(*) from friendships")).isEqualTo(1);

        ItSupport.User c = customer("고객C", "씨이");
        mvc.perform(post("/api/v1/customer/friends/blocks").header("Authorization", a.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + c.id() + "\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/customer/friends/invites/" + code + "/accept").header("Authorization", c.bearer())).andExpect(status().isNotFound());
    }

    @Test
    void 같은_닉네임을_동시에_정해도_태그가_겹치지_않는다() throws Exception {
        List<ItSupport.User> users = new ArrayList<>();
        for (int i = 0; i < 6; i++) users.add(customer("동명" + i, null));
        ExecutorService pool = Executors.newFixedThreadPool(users.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (ItSupport.User u : users) futures.add(pool.submit(() -> { go.await(); return profiles.setFriendNickname(u.id(), "같은닉"); }));
        go.countDown();
        for (Future<?> f : futures) f.get();
        pool.shutdown();
        assertThat(count("select count(distinct friend_tag) from profiles where nickname = '같은닉'")).isEqualTo(users.size());
    }

    @Test
    void 탈퇴한_친구는_목록에서_빠진다() throws Exception {
        String id = body(send(a, b)).get("id").asText();
        mvc.perform(post("/api/v1/customer/friends/requests/" + id + "/accept").header("Authorization", b.bearer())).andExpect(status().isOk());
        jdbc.update("update profiles set deleted_at = now(), nickname = null where id = ?", b.id());
        mvc.perform(get("/api/v1/customer/friends").header("Authorization", a.bearer())).andExpect(jsonPath("$.friends").isEmpty());
    }
}
