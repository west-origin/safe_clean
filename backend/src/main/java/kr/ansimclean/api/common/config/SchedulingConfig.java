package kr.ansimclean.api.common.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import net.javacrumbs.shedlock.support.KeepAliveLockProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 주기 작업(OutboxDispatcher 5초·OfferExpirer 30초·LiveReconciler 60초 …)을 켠다.
 * test 프로필에서는 끈다 — 통합 테스트가 시각을 옮겨 놓고 같은 메서드를 직접 부르며 횟수를 단언하므로,
 * 백그라운드 스케줄이 그 사이에 한 번 돌면 결과가 달라진다. (TaskScheduler 빈도 같이 없어져 OutboxQueued 즉시 발송도 꺼진다)
 * {@code @EnableScheduling}: 이 설정이 켜져 있어야 각 클래스의 {@code @Scheduled} 메서드를 스프링이 정해진 주기·시각에 부른다.
 * 스케줄러가 같이 쓰는 스레드 수는 application.yml 의 spring.task.scheduling.pool.size 다 — 하나면 오래 걸리는 작업이 다른 주기 작업을 밀어낸다.
 * ShedLock은 DB 시계로 인스턴스 사이 실행을 조율한다. 긴 정리 작업의 잠금은 별도 스레드가 갱신한다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT2M")
@Profile("!test")
public class SchedulingConfig {
    @Bean(destroyMethod = "close")
    SchedulerLockHeartbeat schedulerLockHeartbeat() {
        return new SchedulerLockHeartbeat();
    }

    // ScheduledExecutorService 자체를 빈으로 노출하면 Boot의 3스레드 TaskScheduler 자동 구성이 꺼진다.
    static final class SchedulerLockHeartbeat implements AutoCloseable {
        final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "scheduler-lock-heartbeat");
            thread.setDaemon(true);
            return thread;
        });

        @Override
        public void close() { executor.shutdownNow(); }
    }

    @Bean
    LockProvider lockProvider(DataSource dataSource, SchedulerLockHeartbeat schedulerLockHeartbeat) {
        JdbcTemplateLockProvider jdbc = new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .withTableName("public.shedlock")
                        .usingDbTime()
                        .build());
        return new KeepAliveLockProvider(jdbc, schedulerLockHeartbeat.executor);
    }
}
