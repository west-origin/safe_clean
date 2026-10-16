package kr.ansimclean.api.payment;

import java.time.*;
import java.util.UUID;
import kr.ansimclean.api.ItSupport;
import kr.ansimclean.api.booking.BookingService;
import kr.ansimclean.api.booking.dto.CancelRequest;
import kr.ansimclean.api.common.outbox.OutboxDispatcher;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.profile.ProfileService;
import kr.ansimclean.api.payment.dto.ConfirmPaymentRequest;
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

/** 실제 Postgres/Flyway/멱등/아웃박스/예약 상태 연결. PG I/O만 대역. */
@SpringBootTest(properties = {"app.payment.client-key=test_ck_stub", "app.payment.secret-key=test_sk_stub"})
@Testcontainers
@ActiveProfiles("test")
@Import(CheckoutIT.FixedClock.class)
class CheckoutIT {
    @Container @ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");
    static final Instant NOW = Instant.parse("2026-09-15T05:00:00Z");
    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {
        @Bean @Primary AppClock paymentClock() { return new AppClock(Clock.fixed(NOW, ZoneOffset.UTC)); }
    }
    @Autowired ProfileService profiles;
    @Autowired BookingService bookings;
    @Autowired CheckoutService checkout;
    @Autowired OutboxDispatcher dispatcher;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean JwtDecoder jwt;
    @MockitoBean TossPaymentGateway gateway;

    @Test void real_db_checkout_idempotency_approval_cancellation_and_refund() {
        var user = ItSupport.signedUp(profiles, jwt, "customer", "결제테스트");
        UUID booking = UUID.randomUUID();
        jdbc.update("""
                insert into bookings(id, customer_id, category_key, frequency, service_date, service_time,
                starts_at, ends_at, road_address, sido, sigungu, base_amount, options_amount, total_amount,
                platform_fee, duration_minutes, status)
                values (?, ?, 'home', 'oneTime', '2026-09-22', '12:00', '2026-09-22T03:00:00Z',
                '2026-09-22T06:00:00Z', '서울특별시 강남구 테헤란로 123', '서울특별시', '강남구', 80000, 5000, 85000, 8500, 180, 'requested')
                """, booking, user.id());
        var first = checkout.prepare(user.id(), booking, "checkout-" + booking);
        assertThat(checkout.prepare(user.id(), booking, "checkout-other-" + booking).id()).isEqualTo(first.id());
        assertThatThrownBy(() -> checkout.get(UUID.randomUUID(), first.id())).isInstanceOf(kr.ansimclean.api.common.error.ApiException.class);
        assertThatThrownBy(() -> checkout.confirm(user.id(), first.id(), "wrong-" + booking, new ConfirmPaymentRequest("key", 1)))
                .isInstanceOf(kr.ansimclean.api.common.error.ApiException.class);
        var req = new ConfirmPaymentRequest("key-" + booking, 85000);
        checkout.confirm(user.id(), first.id(), "confirm-" + booking, req);
        checkout.confirm(user.id(), first.id(), "confirm-" + booking, req);
        var paid = new TossPaymentGateway.Result(req.paymentKey(), first.orderId(), "DONE", "KRW", 85000, 85000);
        when(gateway.confirm(first.orderId(), req.paymentKey(), 85000)).thenReturn(paid);
        dispatcher.dispatchBatch();
        assertThat(checkout.get(user.id(), first.id()).status()).isEqualTo("paid");
        assertThat(jdbc.queryForObject("select payment_status from bookings where id = ?", String.class, booking)).isEqualTo("paid");
        verify(gateway, times(1)).confirm(first.orderId(), req.paymentKey(), 85000);
        bookings.cancel(user.id(), booking, new CancelRequest("customer", false));
        when(gateway.lookup(first.orderId())).thenReturn(paid);
        when(gateway.cancel(eq(first.orderId()), eq(req.paymentKey()), any())).thenReturn(
                new TossPaymentGateway.Result(req.paymentKey(), first.orderId(), "CANCELED", "KRW", 85000, 0));
        checkout.enqueue(first.id()); dispatcher.dispatchBatch();
        var refunded = checkout.get(user.id(), first.id());
        assertThat(refunded.status()).isEqualTo("refunded");
        assertThat(refunded.refundedAmount()).isEqualTo(85000);
        assertThat(jdbc.queryForObject("select count(*) from payment_refunds where payment_id = ? and status = 'done'", Integer.class, first.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select relrowsecurity from pg_class where oid = 'payment_refunds'::regclass", Boolean.class)).isTrue();
        if (Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from pg_roles where rolname='authenticated')", Boolean.class)))
            assertThat(jdbc.queryForObject("select has_table_privilege('authenticated','payment_refunds','select')", Boolean.class)).isFalse();
    }
}
