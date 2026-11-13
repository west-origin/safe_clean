package kr.ansimclean.api.payment;

import java.time.*;
import java.util.List;
import java.util.UUID;
import kr.ansimclean.api.ItSupport;
import kr.ansimclean.api.booking.BookingService;
import kr.ansimclean.api.booking.dto.CancelRequest;
import kr.ansimclean.api.common.outbox.OutboxDispatcher;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.payment.dto.ConfirmPaymentRequest;
import kr.ansimclean.api.profile.ProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 요금표 전환 (2026-11-13, V074): 주 N회 요금제의 한 달치 시리즈 결제 → 회차 하나 취소 → 그 회차 몫만 토스 부분 취소.
 * 실제 Postgres/Flyway(V074 제약)/멱등/아웃박스/재조정. PG I/O 만 대역. 정기 회차는 SQL 로 바로 넣는다(예약 생성 계산은 PricePlanBookingTest).
 */
@SpringBootTest(properties = {"app.payment.client-key=test_ck_stub", "app.payment.secret-key=test_sk_stub"})
@Testcontainers
@ActiveProfiles("test")
@Import(PricePlanIT.FixedClock.class)
class PricePlanIT {
    @Container @ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");
    static final Instant NOW = Instant.parse("2026-09-15T05:00:00Z");
    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {
        @Bean @Primary AppClock paymentClock() { return new AppClock(Clock.fixed(NOW, ZoneOffset.UTC)); }
    }
    @Autowired ProfileService profiles;
    @Autowired BookingService bookings;
    @Autowired CheckoutService checkout;
    @Autowired PaymentReconciler reconciler;
    @Autowired OutboxDispatcher dispatcher;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean JwtDecoder jwt;
    @MockitoBean TossPaymentGateway gateway;

    /** 가정 주 1회 (월요일) 9/21 · 9/28 · 10/5 · 10/12 — 329,000 = 82,253 + 82,249 × 3 */
    List<UUID> weeklySeries(UUID customer) {
        UUID series = UUID.randomUUID();
        jdbc.update("""
                insert into booking_series(id, customer_id, category_key, weekday, service_time, road_address, free_options, paid_options, notices,
                  status, term_start, term_end, visits_planned, created_at, price_plan, weekdays, plan_amount)
                values (?, ?, 'home', 0, '10:00', '서울특별시 강남구 테헤란로 123', '{}', '{}', '{}', 'active', '2026-09-21', '2026-10-12', 4, now(),
                  'house_weekly_1', '{0}', 329000)
                """, series, customer);
        List<UUID> visits = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            UUID id = UUID.randomUUID();
            LocalDate d = LocalDate.of(2026, 9, 21).plusWeeks(i);
            long amount = i == 0 ? 82253 : 82249;
            jdbc.update("""
                    insert into bookings(id, customer_id, series_id, category_key, home_type, frequency, service_date, service_time,
                    starts_at, ends_at, road_address, sido, sigungu, base_amount, options_amount, total_amount, platform_fee, duration_minutes, status, price_plan)
                    values (?, ?, ?, 'home', 'house', 'recurring', ?, '10:00', ?, ?, '서울특별시 강남구 테헤란로 123', '서울특별시', '강남구', ?, 0, ?, ?, 180, 'requested', 'house_weekly_1')
                    """, id, customer, series, d, AppClock.toInstant(d, LocalTime.of(10, 0)), AppClock.toInstant(d, LocalTime.of(13, 0)),
                    amount, amount, amount / 10);
            visits.add(id);
        }
        visits.add(series);
        return visits;
    }

    @Test void one_month_series_payment_and_visit_share_refund() {
        var user = ItSupport.signedUp(profiles, jwt, "customer", "요금제테스트");
        List<UUID> ids = weeklySeries(user.id());
        UUID series = ids.get(4);
        // 어느 회차에서 눌러도 같은 한 달치 주문
        var first = checkout.prepare(user.id(), ids.get(2), "checkout-a");
        assertThat(first.amount()).isEqualTo(329000);
        assertThat(first.seriesId()).isEqualTo(series);
        assertThat(checkout.prepare(user.id(), ids.get(0), "checkout-b").id()).isEqualTo(first.id());
        assertThatThrownBy(() -> checkout.get(UUID.randomUUID(), first.id())).isInstanceOf(kr.ansimclean.api.common.error.ApiException.class);

        var req = new ConfirmPaymentRequest("key-series", 329000);
        checkout.confirm(user.id(), first.id(), "confirm-series", req);
        var paid = new TossPaymentGateway.Result(req.paymentKey(), first.orderId(), "DONE", "KRW", 329000, 329000);
        when(gateway.confirm(first.orderId(), req.paymentKey(), 329000)).thenReturn(paid);
        dispatcher.dispatchBatch();
        assertThat(checkout.get(user.id(), first.id()).status()).isEqualTo("paid");
        assertThat(jdbc.queryForList("select payment_status from bookings where series_id = ?", String.class, series)).containsOnly("paid");

        // 세 번째 회차만 취소 → 재조정이 회차 몫(82,249)만 부분 취소
        bookings.cancel(user.id(), ids.get(2), new CancelRequest("customer", false));
        when(gateway.lookup(first.orderId())).thenReturn(paid);
        var partial = new TossPaymentGateway.Result(req.paymentKey(), first.orderId(), "PARTIAL_CANCELED", "KRW", 329000, 329000 - 82249);
        when(gateway.cancel(eq(first.orderId()), eq(req.paymentKey()), any(), eq(82249L))).thenReturn(partial);
        reconciler.reconcile();
        dispatcher.dispatchBatch();
        var after = checkout.get(user.id(), first.id());
        assertThat(after.status()).isEqualTo("partially_refunded");
        assertThat(after.refundedAmount()).isEqualTo(82249);
        assertThat(after.refundStatus()).isEqualTo("done");
        assertThat(jdbc.queryForObject("select payment_status from bookings where id = ?", String.class, ids.get(2))).isEqualTo("refunded");
        assertThat(jdbc.queryForObject("select payment_status from bookings where id = ?", String.class, ids.get(0))).isEqualTo("paid");
        assertThat(jdbc.queryForObject("select count(*) from payment_refunds where payment_id = ? and booking_id = ?", Integer.class, first.id(), ids.get(2))).isEqualTo(1);
        // 다시 돌려도 새 환불 행·새 PG 호출이 없다
        reconciler.reconcile(); dispatcher.dispatchBatch();
        verify(gateway, times(1)).cancel(eq(first.orderId()), eq(req.paymentKey()), any(), eq(82249L));
    }

    @Test void schema_allows_one_live_series_order_and_one_refund_per_visit() {
        var user = ItSupport.signedUp(profiles, jwt, "customer", "제약테스트");
        List<UUID> ids = weeklySeries(user.id());
        UUID series = ids.get(4);
        UUID pay = UUID.randomUUID();
        jdbc.update("insert into payments(id, series_id, provider, amount, status, created_at, updated_at) values (?, ?, 'toss', 329000, 'paid', now(), now())", pay, series);
        assertThatThrownBy(() -> jdbc.update("insert into payments(id, series_id, provider, amount, status, created_at, updated_at) values (?, ?, 'toss', 329000, 'pending', now(), now())", UUID.randomUUID(), series))
                .hasMessageContaining("payments_one_series");
        assertThatThrownBy(() -> jdbc.update("insert into payments(id, series_id, booking_id, provider, amount, status, created_at, updated_at) values (?, ?, ?, 'toss', 1, 'failed', now(), now())", UUID.randomUUID(), series, ids.get(0)))
                .hasMessageContaining("payments_one_target");
        jdbc.update("insert into payment_refunds(id, payment_id, booking_id, amount, status, created_at, updated_at) values (?, ?, ?, 82249, 'pending', now(), now())", UUID.randomUUID(), pay, ids.get(1));
        jdbc.update("insert into payment_refunds(id, payment_id, booking_id, amount, status, created_at, updated_at) values (?, ?, ?, 82249, 'pending', now(), now())", UUID.randomUUID(), pay, ids.get(2));
        assertThatThrownBy(() -> jdbc.update("insert into payment_refunds(id, payment_id, booking_id, amount, status, created_at, updated_at) values (?, ?, ?, 82249, 'pending', now(), now())", UUID.randomUUID(), pay, ids.get(2)))
                .hasMessageContaining("payment_refunds_one_visit");
        assertThat(jdbc.queryForObject("select value->'house'->'weekly'->>'2' from app_settings where key = 'price_plans'", String.class)).isEqualTo("599000");
    }
}
