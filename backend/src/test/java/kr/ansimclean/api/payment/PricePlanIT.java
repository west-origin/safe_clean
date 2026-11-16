package kr.ansimclean.api.payment;

import java.time.*;
import java.util.List;
import java.util.UUID;
import kr.ansimclean.api.ItSupport;
import kr.ansimclean.api.booking.BookingService;
import kr.ansimclean.api.booking.dto.CancelRequest;
import kr.ansimclean.api.booking.dto.CreateBookingRequest;
import kr.ansimclean.api.gift.VoucherRestorer;
import kr.ansimclean.api.gift.VoucherService;
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
    // 움직이는 시계: 환불 대상 조건이 '결제 뒤에 취소된 회차'(cancelled_at > 결제 created_at)라 멈춘 시계에서는 같은 시각이 돼 빠진다
    static final kr.ansimclean.api.MutableClock CLOCK = new kr.ansimclean.api.MutableClock(NOW, ZoneOffset.UTC);
    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {
        @Bean @Primary AppClock paymentClock() { return new AppClock(CLOCK); }
    }
    @Autowired ProfileService profiles;
    @Autowired BookingService bookings;
    @Autowired CheckoutService checkout;
    @Autowired PaymentReconciler reconciler;
    @Autowired OutboxDispatcher dispatcher;
    @Autowired JdbcTemplate jdbc;
    @Autowired VoucherService vouchers;
    @Autowired VoucherRestorer restorer;
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
                    """, id, customer, series, d, java.sql.Timestamp.from(AppClock.toInstant(d, LocalTime.of(10, 0))), java.sql.Timestamp.from(AppClock.toInstant(d, LocalTime.of(13, 0))), // JDBC 는 Instant 를 바로 못 받는다
                    amount, amount, amount / 10);
            visits.add(id);
        }
        visits.add(series);
        return visits;
    }

    @org.junit.jupiter.api.BeforeEach
    void resetClock() { CLOCK.set(NOW); } // 테스트 순서와 무관하게 같은 시각에서 시작

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
        CLOCK.set(NOW.plusSeconds(60)); // 결제 1분 뒤 취소
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

    /** 결제가 확정된 가정 정기 시리즈 (혜택 원천) */
    UUID paidSeries(UUID customer) {
        List<UUID> ids = weeklySeries(customer);
        jdbc.update("insert into payments(id, series_id, provider, amount, status, created_at, updated_at, provider_tx_id) values (?, ?, 'toss', 329000, 'paid', now(), now(), ?)",
                UUID.randomUUID(), ids.get(4), UUID.randomUUID().toString());
        return ids.get(4);
    }

    CreateBookingRequest hood(String size) {
        return new CreateBookingRequest("appliance", "oneTime", "2026-09-25", "10:00", "서울특별시 강남구 테헤란로 123", null, List.of(), List.of("panel"), List.of(),
                null, null, null, false, null, null, null, null, size);
    }

    // 첫 결제 혜택 (V075, 사용자 결정 2026-11-14): 가정 정기 결제 확정 뒤 첫 분해청소는 추가 요금까지 무료, 고객당·집당 1회, 취소하면 돌아온다
    @Test void first_house_plan_payment_makes_the_next_hood_booking_free_once_per_customer_and_home() {
        var user = ItSupport.signedUp(profiles, jwt, "customer", "혜택테스트");
        var noPayment = bookings.create(user.id(), "hood-0", hood("600"));
        assertThat(noPayment.discountAmount()).isZero(); // 아직 결제한 가정 정기가 없다
        bookings.cancel(user.id(), noPayment.id(), new CancelRequest("customer", false));

        paidSeries(user.id());
        var free = bookings.create(user.id(), "hood-1", hood("900"));
        assertThat(free.discountAmount()).isEqualTo(170000); // 150,000 + 콘센트 가림판넬 20,000
        assertThat(free.payLabel()).isEqualTo("0원");
        assertThat(jdbc.queryForObject("select payment_status from bookings where id = ?", String.class, free.id())).isEqualTo("paid");
        assertThatThrownBy(() -> checkout.prepare(user.id(), free.id(), "checkout-free")).hasMessage(CheckoutService.BENEFIT_COVERED);
        assertThat(bookings.create(user.id(), "hood-2", hood("600")).discountAmount()).isZero(); // 고객당 1회

        bookings.cancel(user.id(), free.id(), new CancelRequest("customer", false));
        assertThat(bookings.create(user.id(), "hood-3", hood("600")).discountAmount()).isEqualTo(150000); // 취소하면 혜택이 돌아온다

        // 다른 계정이 같은 집(같은 주소·상세)의 가정 정기를 결제해도 혜택은 집당 1회
        var other = ItSupport.signedUp(profiles, jwt, "customer", "같은집");
        paidSeries(other.id());
        assertThat(bookings.create(other.id(), "hood-4", hood("600")).discountAmount()).isZero();
    }

    // 정기 요금제 금액권 (사용자 결정 2026-11-14): 회차 금액 비율로 나눠 쓰고, 회차 취소는 금액권 몫은 금액권으로·카드 몫은 부분 취소로
    @Test void a_voucher_on_a_series_is_split_by_visit_and_a_cancelled_visit_returns_both_parts() {
        var user = ItSupport.signedUp(profiles, jwt, "customer", "정기금액권");
        List<UUID> ids = weeklySeries(user.id());
        UUID voucher = jdbc.queryForObject("""
                insert into vouchers(owner_id, purchaser_id, catalog_key, label, face_amount, balance, status, purchased_at, received_at, valid_until)
                values (?, ?, 'studio_once', '원룸 1회 청소', 69000, 69000, 'held', now(), now(), now() + interval '300 days') returning id
                """, UUID.class, user.id(), user.id());
        var applied = vouchers.apply(user.id(), ids.get(2), voucher);
        assertThat(applied.appliedAmount()).isEqualTo(69000);
        assertThat(applied.remainingDue()).isEqualTo(329000 - 69000);
        assertThat(jdbc.queryForList("select amount from voucher_uses where voucher_id = ? and status = 'applied' order by amount desc", Long.class, voucher))
                .containsExactly(17251L, 17250L, 17250L, 17249L);

        var order = checkout.prepare(user.id(), ids.get(0), "checkout-v");
        assertThat(order.amount()).isEqualTo(329000 - 69000);
        var req = new ConfirmPaymentRequest("key-v", 329000 - 69000);
        checkout.confirm(user.id(), order.id(), "confirm-v", req);
        var paid = new TossPaymentGateway.Result(req.paymentKey(), order.orderId(), "DONE", "KRW", 329000 - 69000, 329000 - 69000);
        when(gateway.confirm(order.orderId(), req.paymentKey(), 329000 - 69000)).thenReturn(paid);
        dispatcher.dispatchBatch();

        CLOCK.set(NOW.plusSeconds(60)); // 결제 1분 뒤 취소
        bookings.cancel(user.id(), ids.get(2), new CancelRequest("customer", false)); // 셋째 회차 82,249 = 금액권 17,250 + 카드 64,999
        restorer.run();
        assertThat(jdbc.queryForObject("select balance from vouchers where id = ?", Long.class, voucher)).isEqualTo(17250);
        when(gateway.lookup(order.orderId())).thenReturn(paid);
        when(gateway.cancel(eq(order.orderId()), eq(req.paymentKey()), any(), eq(64999L)))
                .thenReturn(new TossPaymentGateway.Result(req.paymentKey(), order.orderId(), "PARTIAL_CANCELED", "KRW", 329000 - 69000, 329000 - 69000 - 64999));
        reconciler.reconcile();
        dispatcher.dispatchBatch();
        assertThat(checkout.get(user.id(), order.id()).refundedAmount()).isEqualTo(64999);
        verify(gateway, times(1)).cancel(eq(order.orderId()), eq(req.paymentKey()), any(), eq(64999L));
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
