package kr.ansimclean.api.common.config;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** 두 리비전이 같은 Postgres에서 잠금을 경쟁하는 상황. 운영 DB는 사용하지 않는다. */
@Testcontainers
class SchedulerLockIT {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");
    static JdbcTemplate jdbc;

    @BeforeAll
    static void schema() {
        var ds = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(ds);
    }

    JdbcTemplateLockProvider node(String id) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(jdbc).withTableName("public.shedlock").usingDbTime().withLockedByValue(id).build());
    }

    LockConfiguration config(String name, Duration minimum) {
        return new LockConfiguration(Instant.now(), name, Duration.ofMinutes(2), minimum);
    }

    @Test
    void second_revision_skips_while_first_revision_holds_the_lock() {
        var first = node("revision-a").lock(config("overlap", Duration.ZERO)).orElseThrow();
        try {
            assertThat(node("revision-b").lock(config("overlap", Duration.ZERO))).isEmpty();
        } finally {
            first.unlock();
        }
        var next = node("revision-b").lock(config("overlap", Duration.ZERO)).orElseThrow();
        next.unlock();
    }

    @Test
    void minimum_lease_prevents_a_second_execution_of_a_short_cron_task() {
        var first = node("revision-a").lock(config("cron", Duration.ofMinutes(1))).orElseThrow();
        first.unlock();
        assertThat(node("revision-b").lock(config("cron", Duration.ofMinutes(1)))).isEmpty();
        // 만료된 임대는 새 리비전이 인수할 수 있다. 벽시계로 1분을 기다리지 않는다.
        jdbc.update("update public.shedlock set lock_until = timezone('UTC', now()) - interval '1 second' where name = 'cron'");
        var recovered = node("revision-b").lock(config("cron", Duration.ZERO)).orElseThrow();
        recovered.unlock();
    }
}
