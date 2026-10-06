package kr.ansimclean.api.common.outbox;

import kr.ansimclean.api.MutableClock;
import kr.ansimclean.api.common.time.AppClock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** 통합 (Docker 필요, B4 완료 기준 OutboxIT): 핸들러 실패 → 백오프 재시도 → sent, 6회 실패 → failed (BACKEND_PLAN.md §2.4) */
// 통합 테스트(CODE_GUIDE.md 3-12): 아웃박스 행 저장 → 디스패처 → 핸들러 → 상태 기록이 실제 Postgres 에서 도는지 본다.
// test 프로필은 스케줄러가 꺼져 있어(SchedulingConfig) 5초 주기·즉시 발송이 돌지 않는다 — 테스트가 dispatchBatch 를 직접 부른다.
// @Import: 아래 두 테스트 설정(옮길 수 있는 시계, 가짜 핸들러 두 개)을 이 테스트의 스프링에 더한다.
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import({ OutboxIT.FixedClock.class, OutboxIT.Handlers.class })
class OutboxIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    // MutableClock: 테스트가 '지금' 을 앞으로 옮길 수 있는 시계. 백오프 시각이 지나도록 시간을 건너뛸 때 쓴다
    static final MutableClock CLOCK = new MutableClock(Instant.parse("2026-09-15T05:00:00Z"), AppClock.ZONE);
    static final AtomicInteger FLAKY_CALLS = new AtomicInteger();  // FlakyEvent 핸들러가 불린 횟수
    static final AtomicInteger BROKEN_CALLS = new AtomicInteger(); // BrokenEvent 핸들러가 불린 횟수

    /** 서버의 AppClock 을 위 CLOCK 을 쓰는 것으로 바꾼다 (@Primary: 같은 타입 빈이 여럿이면 이것을 쓴다) */
    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {
        @Bean
        @Primary
        AppClock testClock() {
            return new AppClock(CLOCK);
        }
    }

    /** 시험용 핸들러 두 개를 빈으로 등록한다 — 디스패처는 OutboxHandler 빈 전부를 받으므로 진짜 핸들러들과 함께 들어간다 */
    @TestConfiguration(proxyBeanMethods = false)
    static class Handlers {
        /** 두 번 실패한 뒤 성공 */
        @Bean
        OutboxHandler flaky() {
            return new OutboxHandler() {
                @Override
                public Set<String> types() {
                    return Set.of("FlakyEvent");
                }

                @Override
                public void handle(String type, String payloadJson) {
                    if (FLAKY_CALLS.incrementAndGet() <= 2) throw new IllegalStateException("일시 장애");
                }
            };
        }

        /** 항상 실패 */
        @Bean
        OutboxHandler broken() {
            return new OutboxHandler() {
                @Override
                public Set<String> types() {
                    return Set.of("BrokenEvent");
                }

                @Override
                public void handle(String type, String payloadJson) {
                    BROKEN_CALLS.incrementAndGet();
                    throw new IllegalStateException("영구 장애");
                }
            };
        }
    }

    @Autowired
    OutboxService outbox;
    @Autowired
    OutboxDispatcher dispatcher;
    @Autowired
    OutboxEventRepository events;
    @Autowired
    TransactionTemplate tx;
    // JwtDecoder 빈 자리에 목을 넣는다 — 이 테스트는 토큰을 쓰지 않는다
    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void 실패한_이벤트는_백오프_뒤_다시_시도되고_상한을_넘으면_failed() {
        // 업무 코드처럼 트랜잭션 안에서 두 이벤트를 적고 커밋한다
        tx.executeWithoutResult(s -> {
            outbox.publish("FlakyEvent", Map.of("n", 1));
            outbox.publish("BrokenEvent", Map.of("n", 2));
        });
        OutboxEvent flaky = events.findAll().stream().filter(e -> e.getType().equals("FlakyEvent")).findFirst().orElseThrow();
        OutboxEvent broken = events.findAll().stream().filter(e -> e.getType().equals("BrokenEvent")).findFirst().orElseThrow();

        // 첫 배치: Flaky 는 첫 실패 → 시도 1회, 아직 pending, 다음 시도는 지금보다 뒤
        dispatcher.dispatchBatch();
        assertThat(events.findById(flaky.getId()).orElseThrow().getAttempts()).isEqualTo(1);
        assertThat(events.findById(flaky.getId()).orElseThrow().getStatus()).isEqualTo("pending");
        assertThat(events.findById(flaky.getId()).orElseThrow().getNextAttemptAt()).isAfter(CLOCK.instant());

        dispatcher.dispatchBatch(); // 아직 next_attempt_at 전 → 건드리지 않는다
        assertThat(FLAKY_CALLS.get()).isEqualTo(1);

        for (int i = 0; i < 8; i++) { // 백오프(2^n 분)를 충분히 넘기며 반복
            CLOCK.set(CLOCK.instant().plus(Duration.ofMinutes(70)));
            dispatcher.dispatchBatch();
        }
        // Flaky: 두 번 실패 뒤 세 번째에 성공 → sent
        assertThat(events.findById(flaky.getId()).orElseThrow().getStatus()).isEqualTo("sent");
        assertThat(FLAKY_CALLS.get()).isEqualTo(3);

        // Broken: 상한(MAX_ATTEMPTS)만큼 불리고 failed 로 멈췄다. 그 뒤 반복에서는 더 불리지 않는다
        OutboxEvent dead = events.findById(broken.getId()).orElseThrow();
        assertThat(dead.getStatus()).isEqualTo("failed");
        assertThat(dead.getAttempts()).isEqualTo(OutboxEvent.MAX_ATTEMPTS);
        assertThat(BROKEN_CALLS.get()).isEqualTo(OutboxEvent.MAX_ATTEMPTS);
        assertThat(dead.getLastError()).contains("영구 장애");
    }
}
