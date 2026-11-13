package kr.ansimclean.api.gift;

import kr.ansimclean.api.ItSupport;
import kr.ansimclean.api.MutableClock;
import kr.ansimclean.api.auth.AccountDeletionGuard;
import kr.ansimclean.api.booking.BookingService;
import kr.ansimclean.api.booking.dto.CancelRequest;
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
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 통합 (Docker 필요): 금액권 사용·복원·환불을 실제 Postgres·V071·기존 결제 경로(PG 만 대역)로 본다.
 * 같은 금액권의 선물과 예약 사용 동시 → 하나만, 사용 → 카드 결제 금액 감소 → 예약 취소 → 복원, 전부 덮기, 카드 환불(구매 7일), 계좌 환불 + 어드민 완료, 탈퇴 가드
 */
@SpringBootTest(properties = { "app.payment.client-key=test_ck_stub", "app.payment.secret-key=test_sk_stub",
        "app.refund.account-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=" })
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(VoucherIT.Clock.class)
class VoucherIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");
    static final Instant T0 = Instant.parse("2026-11-13T03:00:00Z");
    static final MutableClock CLOCK = new MutableClock(T0, AppClock.ZONE);

    @TestConfiguration(proxyBeanMethods = false)
    static class Clock {
        @Bean
        @Primary
        AppClock voucherClock() {
            return new AppClock(CLOCK);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ProfileService profiles;
    @Autowired BookingService bookings;
    @Autowired JdbcTemplate jdbc;
    @Autowired OutboxDispatcher dispatcher;
    @Autowired VoucherRestorer restorer;
    @Autowired List<AccountDeletionGuard> guards;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean TossPaymentGateway gateway;

    final JsonMapper json = JsonMapper.builder().build();
    ItSupport.User a;
    ItSupport.User b;

    @BeforeEach
    void setUp() {
        CLOCK.set(T0);
        ItSupport.resetUserData(jdbc);
        a = ItSupport.signedUp(profiles, jwtDecoder, "customer", "고객A");
        b = ItSupport.signedUp(profiles, jwtDecoder, "customer", "고객B");
        profiles.setFriendNickname(a.id(), "에이");
        profiles.setFriendNickname(b.id(), "비이");
        jdbc.update("insert into friendships(user_a, user_b) values (least(?::uuid, ?::uuid), greatest(?::uuid, ?::uuid))", a.id(), b.id(), a.id(), b.id());
    }

    private UUID voucher(ItSupport.User owner, ItSupport.User purchaser, long face, long balance, Instant purchasedAt, Instant receivedAt, Instant validUntil) {
        return jdbc.queryForObject("""
                insert into vouchers(owner_id, purchaser_id, catalog_key, label, face_amount, balance, status, purchased_at, received_at, valid_until)
                values (?, ?, 'studio_once', '원룸 1회 청소', ?, ?, 'held', ?, ?, ?) returning id
                """, UUID.class, owner.id(), purchaser.id(), face, balance, ts(purchasedAt), ts(receivedAt), ts(validUntil));
    }

    private static Timestamp ts(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }

    /** 85,000원 예약 하나 (방문 11월 20일) */
    private UUID booking(ItSupport.User owner) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into bookings(id, customer_id, category_key, frequency, service_date, service_time,
                starts_at, ends_at, road_address, sido, sigungu, base_amount, options_amount, total_amount,
                platform_fee, duration_minutes, status)
                values (?, ?, 'home', 'oneTime', '2026-11-20', '12:00', '2026-11-20T03:00:00Z',
                '2026-11-20T06:00:00Z', '서울특별시 강남구 테헤란로 123', '서울특별시', '강남구', 80000, 5000, 85000, 8500, 180, 'requested')
                """, id, owner.id());
        return id;
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private ResultActions apply(ItSupport.User who, UUID booking, UUID voucher) throws Exception {
        return mvc.perform(post("/api/v1/customer/bookings/" + booking + "/voucher").header("Authorization", who.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"voucherId\":\"" + voucher + "\"}"));
    }

    private long balance(UUID voucher) {
        return jdbc.queryForObject("select balance from vouchers where id = ?", Long.class, voucher);
    }

    @Test
    void 같은_금액권에_선물과_예약_사용이_동시에_와도_하나만_된다() throws Exception {
        for (int round = 0; round < 5; round++) {
            UUID v = voucher(a, b, 69000, 69000, T0.minus(Duration.ofDays(10)), T0.minus(Duration.ofDays(9)), T0.plus(Duration.ofDays(300)));
            UUID bk = booking(a);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch go = new CountDownLatch(1);
            Future<Integer> gift = pool.submit(() -> { go.await(); return mvc.perform(post("/api/v1/customer/gifts").header("Authorization", a.bearer())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"recipientId\":\"" + b.id() + "\",\"method\":\"voucher\",\"voucherId\":\"" + v + "\"}"))
                    .andReturn().getResponse().getStatus(); });
            Future<Integer> use = pool.submit(() -> { go.await(); return apply(a, bk, v).andReturn().getResponse().getStatus(); });
            go.countDown();
            List<Integer> codes = List.of(gift.get(), use.get());
            pool.shutdown();
            assertThat(codes).containsExactlyInAnyOrder(200, 409);
            int gifts = jdbc.queryForObject("select count(*) from gifts where voucher_id = ?", Integer.class, v);
            int uses = jdbc.queryForObject("select count(*) from voucher_uses where voucher_id = ? and status = 'applied'", Integer.class, v);
            assertThat(gifts + uses).isEqualTo(1);
        }
    }

    @Test
    void 쓰면_카드_금액이_줄고_예약을_취소하면_돌아온다() throws Exception {
        UUID v = voucher(a, b, 69000, 69000, T0.minus(Duration.ofDays(10)), T0.minus(Duration.ofDays(9)), T0.plus(Duration.ofDays(300)));
        UUID bk = booking(a);
        apply(b, bk, v).andExpect(status().isNotFound()); // 남의 예약
        apply(a, bk, v).andExpect(status().isOk()).andExpect(jsonPath("$.remainingDue").value(16000));
        assertThat(balance(v)).isZero();
        assertThat(jdbc.queryForObject("select status from vouchers where id = ?", String.class, v)).isEqualTo("used");
        JsonNode pay = body(mvc.perform(post("/api/v1/customer/bookings/" + bk + "/checkout").header("Authorization", a.bearer()).header("Idempotency-Key", "ck-" + bk)));
        assertThat(pay.get("amount").asLong()).isEqualTo(16000);

        bookings.cancel(a.id(), bk, new CancelRequest("customer", false));
        restorer.run();
        assertThat(balance(v)).isEqualTo(69000);
        assertThat(jdbc.queryForObject("select status from vouchers where id = ?", String.class, v)).isEqualTo("held");
        assertThat(jdbc.queryForObject("select restore_reason from voucher_uses where voucher_id = ?", String.class, v)).isEqualTo("booking_cancelled");
    }

    @Test
    void 금액권이_전부_덮으면_카드_결제가_없고_취소하면_예약_결제는_환불로() throws Exception {
        UUID v = voucher(a, b, 140000, 140000, T0.minus(Duration.ofDays(10)), T0.minus(Duration.ofDays(9)), T0.plus(Duration.ofDays(300)));
        UUID bk = booking(a);
        apply(a, bk, v).andExpect(status().isOk()).andExpect(jsonPath("$.coveredByVoucher").value(true));
        assertThat(balance(v)).isEqualTo(55000);
        assertThat(jdbc.queryForObject("select payment_status from bookings where id = ?", String.class, bk)).isEqualTo("paid");
        mvc.perform(post("/api/v1/customer/bookings/" + bk + "/checkout").header("Authorization", a.bearer()).header("Idempotency-Key", "ck2-" + bk))
                .andExpect(status().isConflict());
        bookings.cancel(a.id(), bk, new CancelRequest("customer", false));
        restorer.run();
        assertThat(balance(v)).isEqualTo(140000);
        assertThat(jdbc.queryForObject("select payment_status from bookings where id = ?", String.class, bk)).isEqualTo("refunded");
    }

    @Test
    void 구매_7일_안_돌아온_금액권은_카드_결제_취소로_환불() throws Exception {
        // a 가 b 에게 구매 선물 → 결제 확정 → b 거절 → a 에게 돌아옴 (아무도 받지 않음)
        JsonNode created = body(mvc.perform(post("/api/v1/customer/gifts").header("Authorization", a.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipientId\":\"" + b.id() + "\",\"method\":\"purchase\",\"catalogKey\":\"studio_once\"}")));
        String orderId = created.get("checkout").get("orderId").asText();
        String giftId = created.get("gift").get("id").asText();
        mvc.perform(post("/api/v1/customer/payments/" + orderId + "/confirm").header("Authorization", a.bearer()).header("Idempotency-Key", "c-" + orderId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"paymentKey\":\"pk\",\"amount\":69000}")).andExpect(status().isOk());
        var done = new TossPaymentGateway.Result("pk", orderId, "DONE", "KRW", 69000, 69000);
        when(gateway.lookup(orderId)).thenReturn(null, done);
        when(gateway.confirm(orderId, "pk", 69000)).thenReturn(done);
        dispatcher.dispatchBatch();
        mvc.perform(post("/api/v1/customer/gifts/" + giftId + "/reject").header("Authorization", b.bearer())).andExpect(status().isOk());
        UUID v = jdbc.queryForObject("select voucher_id from gifts where id = ?::uuid", UUID.class, giftId);

        mvc.perform(get("/api/v1/customer/vouchers/" + v + "/refund").header("Authorization", a.bearer()))
                .andExpect(jsonPath("$.method").value("card")).andExpect(jsonPath("$.amount").value(69000));
        when(gateway.lookup(orderId)).thenReturn(done);
        when(gateway.cancel(eq(orderId), eq("pk"), any())).thenReturn(new TossPaymentGateway.Result("pk", orderId, "CANCELED", "KRW", 69000, 0));
        mvc.perform(post("/api/v1/customer/vouchers/" + v + "/refund").header("Authorization", a.bearer()).header("Idempotency-Key", "r-" + v))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("processing"));
        dispatcher.dispatchBatch();
        assertThat(jdbc.queryForObject("select status from payments where id = ?::uuid", String.class, orderId)).isEqualTo("refunded");
        assertThat(jdbc.queryForObject("select status from voucher_refunds where voucher_id = ?", String.class, v)).isEqualTo("done");
        assertThat(jdbc.queryForObject("select status from vouchers where id = ?", String.class, v)).isEqualTo("refunded");
    }

    @Test
    void 받은_금액권은_계좌로_어드민이_송금_완료하면_계좌_원문이_지워진다() throws Exception {
        UUID v = voucher(b, a, 69000, 69000, T0.minus(Duration.ofDays(2)), T0.minus(Duration.ofDays(1)), T0.plus(Duration.ofDays(364)));
        mvc.perform(post("/api/v1/customer/vouchers/" + v + "/refund").header("Authorization", b.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankName\":\"국민은행\",\"accountNumber\":\"123-456-789012\",\"accountHolder\":\"고객B\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.method").value("bank"));
        String cipher = jdbc.queryForObject("select account_cipher from voucher_refunds where voucher_id = ?", String.class, v);
        assertThat(cipher).isNotBlank().doesNotContain("123456789012");
        apply(b, booking(b), v).andExpect(status().isConflict()); // 환불 접수 중에는 쓸 수 없다

        ItSupport.User admin = ItSupport.adminWithMfa(profiles, jwtDecoder);
        String refundId = jdbc.queryForObject("select id::text from voucher_refunds where voucher_id = ?", String.class, v);
        mvc.perform(get("/api/v1/admin/voucher-refunds/" + refundId + "/account").header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.accountNumber").value("123456789012"));
        mvc.perform(post("/api/v1/admin/voucher-refunds/" + refundId + "/pay").header("Authorization", admin.bearer())).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select account_cipher from voucher_refunds where id = ?::uuid", String.class, refundId)).isNull();
        assertThat(jdbc.queryForObject("select account_last4 from voucher_refunds where id = ?::uuid", String.class, refundId)).isEqualTo("9012");
        assertThat(jdbc.queryForObject("select status from vouchers where id = ?", String.class, v)).isEqualTo("refunded");
        assertThat(jdbc.queryForObject("select count(*) from activity_logs where action like 'voucher_refund.%'", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where type = 'VoucherRefundDecided'", Integer.class)).isEqualTo(1);
    }

    @Test
    void 만료되고_5만_원_이하인_금액권은_탈퇴를_막지_않는다() {
        voucher(b, a, 69000, 40000, T0.minus(Duration.ofDays(400)), T0.minus(Duration.ofDays(399)), T0.minusSeconds(10));
        assertThat(guards.stream().map(g -> g.blockReason(b.id(), "customer")).filter(Objects::nonNull)).isEmpty();
        voucher(a, b, 69000, 60000, T0.minus(Duration.ofDays(400)), T0.minus(Duration.ofDays(399)), T0.minusSeconds(10)); // 95% 환불 대상
        assertThat(guards.stream().map(g -> g.blockReason(a.id(), "customer")).filter(Objects::nonNull)).isNotEmpty();
    }
}
