package kr.ansimclean.api.gift;

import kr.ansimclean.api.ItSupport;
import kr.ansimclean.api.MutableClock;
import kr.ansimclean.api.auth.AccountDeletionGuard;
import kr.ansimclean.api.common.outbox.OutboxDispatcher;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.payment.TossPaymentGateway;
import kr.ansimclean.api.profile.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 통합 (Docker 필요): 실제 Postgres·V070·기존 결제 경로(아웃박스 → PaymentWorkHandler, PG 만 대역)로 금액권 선물을 본다.
 * 구매 → 승인 → 결제 확정과 선물 보내기가 같이 → 받기(소유자 이전·1년), 결제 실패, 친구 아님, 같은 금액권 동시 선물 1건만,
 * 받기·취소 경합 1건만, 받는 기간 만료 반환, 탈퇴 가드
 */
@SpringBootTest(properties = { "app.payment.client-key=test_ck_stub", "app.payment.secret-key=test_sk_stub" })
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(GiftIT.Clock.class)
class GiftIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");
    static final Instant T0 = Instant.parse("2026-11-13T03:00:00Z");
    static final MutableClock CLOCK = new MutableClock(T0, AppClock.ZONE);

    @TestConfiguration(proxyBeanMethods = false)
    static class Clock {
        @Bean
        @Primary
        AppClock giftClock() {
            return new AppClock(CLOCK);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ProfileService profiles;
    @Autowired JdbcTemplate jdbc;
    @Autowired OutboxDispatcher dispatcher;
    @Autowired GiftExpirer expirer;
    @Autowired List<AccountDeletionGuard> guards;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean TossPaymentGateway gateway;

    final JsonMapper json = JsonMapper.builder().build();
    ItSupport.User a;
    ItSupport.User b;
    ItSupport.User c;

    @BeforeEach
    void setUp() {
        CLOCK.set(T0);
        ItSupport.resetUserData(jdbc);
        a = customer("고객A", "에이");
        b = customer("고객B", "비이");
        c = customer("고객C", "씨이");
        befriend(a, b);
        befriend(b, c);
    }

    private ItSupport.User customer(String name, String nickname) {
        ItSupport.User u = ItSupport.signedUp(profiles, jwtDecoder, "customer", name);
        profiles.setFriendNickname(u.id(), nickname);
        return u;
    }

    private void befriend(ItSupport.User x, ItSupport.User y) {
        jdbc.update("insert into friendships(user_a, user_b) values (least(?::uuid, ?::uuid), greatest(?::uuid, ?::uuid))", x.id(), y.id(), x.id(), y.id());
    }

    private UUID heldVoucher(ItSupport.User owner, Instant validUntil) {
        return jdbc.queryForObject("""
                insert into vouchers(owner_id, purchaser_id, catalog_key, label, face_amount, balance, status, purchased_at, valid_until)
                values (?, ?, 'studio_once', '원룸 1회 청소 (1회 2시간)', 69000, 69000, 'held', ?, ?) returning id
                """, UUID.class, owner.id(), owner.id(), java.sql.Timestamp.from(T0.minus(Duration.ofDays(1))), validUntil == null ? null : java.sql.Timestamp.from(validUntil));
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private ResultActions send(ItSupport.User from, String bodyJson) throws Exception {
        return mvc.perform(post("/api/v1/customer/gifts").header("Authorization", from.bearer()).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(bodyJson));
    }

    private ResultActions act(ItSupport.User who, String giftId, String action) throws Exception {
        return mvc.perform(post("/api/v1/customer/gifts/" + giftId + "/" + action).header("Authorization", who.bearer()));
    }

    private String one(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }

    private JsonNode buyAndConfirm(String paymentKey, String pgStatus) throws Exception {
        JsonNode created = body(send(a, "{\"recipientId\":\"" + b.id() + "\",\"method\":\"purchase\",\"catalogKey\":\"studio_once\",\"message\":\"이사 축하해\"}")
                .andExpect(status().isOk()));
        assertThat(created.get("gift").get("status").asText()).isEqualTo("awaiting_payment");
        assertThat(created.get("checkout").get("amount").asLong()).isEqualTo(69000);
        String orderId = created.get("checkout").get("orderId").asText();
        mvc.perform(post("/api/v1/customer/payments/" + orderId + "/confirm").header("Authorization", a.bearer()).header("Idempotency-Key", "c-" + orderId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"paymentKey\":\"" + paymentKey + "\",\"amount\":69000}")).andExpect(status().isOk());
        when(gateway.lookup(orderId)).thenReturn(null);
        when(gateway.confirm(orderId, paymentKey, 69000)).thenReturn(new TossPaymentGateway.Result(paymentKey, orderId, pgStatus, "KRW", 69000, 69000));
        dispatcher.dispatchBatch();
        return created;
    }

    @Test
    void 구매_선물은_결제가_확정될_때_같이_보내지고_받으면_소유자가_바뀐다() throws Exception {
        // 결제 전에는 받는 사람에게 보이지 않는다
        JsonNode created = buyAndConfirm("pk-1", "DONE");
        String giftId = created.get("gift").get("id").asText();
        assertThat(one("select status from gifts where id = ?::uuid", giftId)).isEqualTo("sent");
        assertThat(one("select v.status from vouchers v join gifts g on g.voucher_id = v.id where g.id = ?::uuid", giftId)).isEqualTo("gifting");
        assertThat(one("select p.status from payments p where p.gift_id = ?::uuid", giftId)).isEqualTo("paid");
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where type = 'GiftReceived'", Integer.class)).isEqualTo(1);

        mvc.perform(get("/api/v1/customer/gifts").param("box", "received").header("Authorization", b.bearer()))
                .andExpect(jsonPath("$.gifts[0].id").value(giftId))
                .andExpect(jsonPath("$.gifts[0].message").value("이사 축하해"))
                .andExpect(jsonPath("$.gifts[0].person.userId").value(a.id().toString()));
        act(c, giftId, "accept").andExpect(status().isNotFound()); // 남의 선물
        act(b, giftId, "accept").andExpect(status().isOk()).andExpect(jsonPath("$.gift.status").value("accepted"));
        act(b, giftId, "accept").andExpect(status().isOk()); // 다시 눌러도 같다
        act(a, giftId, "cancel").andExpect(status().isConflict()); // 받은 뒤에는 취소 못 함

        UUID voucher = jdbc.queryForObject("select voucher_id from gifts where id = ?::uuid", UUID.class, giftId);
        assertThat(jdbc.queryForObject("select owner_id from vouchers where id = ?", UUID.class, voucher)).isEqualTo(b.id());
        assertThat(jdbc.queryForObject("select valid_until from vouchers where id = ?", java.sql.Timestamp.class, voucher).toInstant()).isEqualTo(T0.plus(Duration.ofDays(365)));
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where type = 'GiftResolved'", Integer.class)).isEqualTo(1);
        mvc.perform(get("/api/v1/customer/vouchers").header("Authorization", b.bearer()))
                .andExpect(jsonPath("$.vouchers[0].id").value(voucher.toString()))
                .andExpect(jsonPath("$.vouchers[0].giftable").value(true));
    }

    @Test
    void 결제가_실패하면_선물도_실패하고_받는_사람에게_가지_않는다() throws Exception {
        JsonNode created = buyAndConfirm("pk-2", "ABORTED");
        String giftId = created.get("gift").get("id").asText();
        assertThat(one("select status from gifts where id = ?::uuid", giftId)).isEqualTo("payment_failed");
        assertThat(one("select v.status from vouchers v join gifts g on g.voucher_id = v.id where g.id = ?::uuid", giftId)).isEqualTo("void");
        mvc.perform(get("/api/v1/customer/gifts").param("box", "received").header("Authorization", b.bearer())).andExpect(jsonPath("$.gifts").isEmpty());
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where type = 'GiftReceived'", Integer.class)).isZero();
    }

    @Test
    void 친구가_아니면_서버가_막는다() throws Exception {
        send(a, "{\"recipientId\":\"" + c.id() + "\",\"method\":\"purchase\",\"catalogKey\":\"studio_once\"}").andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from gifts", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from payments", Integer.class)).isZero();
    }

    @Test
    void 같은_금액권을_두_친구에게_동시에_보내도_하나만_된다() throws Exception {
        for (int round = 0; round < 5; round++) {
            UUID voucher = heldVoucher(b, T0.plus(Duration.ofDays(300)));
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> codes = new ArrayList<>();
            for (ItSupport.User to : List.of(a, c)) {
                codes.add(pool.submit(() -> { go.await(); return send(b, "{\"recipientId\":\"" + to.id() + "\",\"method\":\"voucher\",\"voucherId\":\"" + voucher + "\"}").andReturn().getResponse().getStatus(); }));
            }
            go.countDown();
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> f : codes) results.add(f.get());
            pool.shutdown();
            assertThat(results).containsExactlyInAnyOrder(200, 409);
            assertThat(jdbc.queryForObject("select count(*) from gifts where voucher_id = ? and status = 'sent'", Integer.class, voucher)).isEqualTo(1);
            assertThat(one("select status from vouchers where id = ?", voucher)).isEqualTo("gifting");
        }
    }

    @Test
    void 받기와_취소가_동시에_와도_하나만_되고_소유자가_맞다() throws Exception {
        for (int round = 0; round < 5; round++) {
            UUID voucher = heldVoucher(b, T0.plus(Duration.ofDays(300)));
            String giftId = body(send(b, "{\"recipientId\":\"" + a.id() + "\",\"method\":\"voucher\",\"voucherId\":\"" + voucher + "\"}")).get("gift").get("id").asText();
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch go = new CountDownLatch(1);
            Future<Integer> accept = pool.submit(() -> { go.await(); return act(a, giftId, "accept").andReturn().getResponse().getStatus(); });
            Future<Integer> cancel = pool.submit(() -> { go.await(); return act(b, giftId, "cancel").andReturn().getResponse().getStatus(); });
            go.countDown();
            List<Integer> results = List.of(accept.get(), cancel.get());
            pool.shutdown();
            assertThat(results).containsExactlyInAnyOrder(200, 409);
            String giftStatus = one("select status from gifts where id = ?::uuid", giftId);
            UUID owner = jdbc.queryForObject("select owner_id from vouchers where id = ?", UUID.class, voucher);
            assertThat(owner).isEqualTo("accepted".equals(giftStatus) ? a.id() : b.id());
            assertThat(one("select status from vouchers where id = ?", voucher)).isEqualTo("held");
        }
    }

    @Test
    void 받는_기간이_지나면_보낸_사람에게_돌아가고_알린다() throws Exception {
        UUID voucher = heldVoucher(b, null);
        String giftId = body(send(b, "{\"recipientId\":\"" + c.id() + "\",\"method\":\"voucher\",\"voucherId\":\"" + voucher + "\"}")).get("gift").get("id").asText();
        CLOCK.set(T0.plus(Duration.ofDays(7)).plusSeconds(1));
        expirer.run();
        assertThat(one("select status from gifts where id = ?::uuid", giftId)).isEqualTo("expired");
        assertThat(jdbc.queryForObject("select owner_id from vouchers where id = ?", UUID.class, voucher)).isEqualTo(b.id());
        assertThat(one("select status from vouchers where id = ?", voucher)).isEqualTo("held");
        // 아무도 받은 적 없는 금액권은 구매일 + 1년으로 정해진다
        assertThat(jdbc.queryForObject("select valid_until from vouchers where id = ?", java.sql.Timestamp.class, voucher).toInstant())
                .isEqualTo(T0.minus(Duration.ofDays(1)).plus(Duration.ofDays(365)));
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where type = 'GiftResolved'", Integer.class)).isEqualTo(1);
        act(c, giftId, "accept").andExpect(status().isConflict());
    }

    @Test
    void 남은_금액권이나_처리_중인_선물이_있으면_탈퇴를_막는다() {
        heldVoucher(b, T0.plus(Duration.ofDays(100)));
        assertThat(guards.stream().map(g -> g.blockReason(b.id(), "customer")).filter(java.util.Objects::nonNull)).isNotEmpty();
        assertThat(guards.stream().map(g -> g.blockReason(c.id(), "customer")).filter(java.util.Objects::nonNull)).isEmpty();
    }
}
