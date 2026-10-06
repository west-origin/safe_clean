package kr.ansimclean.api.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 주기 작업(OutboxDispatcher 5초·OfferExpirer 30초·LiveReconciler 60초 …)을 켠다.
 * test 프로필에서는 끈다 — 통합 테스트가 시각을 옮겨 놓고 같은 메서드를 직접 부르며 횟수를 단언하므로,
 * 백그라운드 스케줄이 그 사이에 한 번 돌면 결과가 달라진다. (TaskScheduler 빈도 같이 없어져 OutboxQueued 즉시 발송도 꺼진다)
 * {@code @EnableScheduling}: 이 설정이 켜져 있어야 각 클래스의 {@code @Scheduled} 메서드를 스프링이 정해진 주기·시각에 부른다.
 * 스케줄러가 같이 쓰는 스레드 수는 application.yml 의 spring.task.scheduling.pool.size 다 — 하나면 오래 걸리는 작업이 다른 주기 작업을 밀어낸다.
 * {@code proxyBeanMethods = false}: 이 클래스에는 {@code @Bean} 메서드가 없어 스프링이 감싸는 프록시 클래스를 만들 필요가 없다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@Profile("!test")
public class SchedulingConfig {
}
