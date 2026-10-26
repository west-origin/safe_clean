package kr.ansimclean.api.common.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import net.javacrumbs.shedlock.support.KeepAliveLockProvider;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import javax.sql.DataSource;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SchedulingConfigTest {
    @Test
    void heartbeat_does_not_replace_the_application_task_scheduler() {
        new ApplicationContextRunner()
                .withUserConfiguration(SchedulingConfig.class, TaskSchedulingAutoConfiguration.class)
                .withBean(DataSource.class, () -> mock(DataSource.class))
                .withPropertyValues("spring.task.scheduling.pool.size=3")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ThreadPoolTaskScheduler.class);
                    assertThat(context.getBean(ThreadPoolTaskScheduler.class).getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(3);
                    assertThat(context.getBean(LockProvider.class)).isInstanceOf(KeepAliveLockProvider.class);
                });
    }

    @Test
    void every_scheduled_entry_point_has_a_distinct_cluster_lock() {
        var names = new HashSet<String>();
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("kr.ansimclean.api");
        int checked = 0;
        for (var type : classes) for (var method : type.getMethods()) {
            if (!method.isAnnotatedWith(Scheduled.class)) continue;
            assertThat(method.isAnnotatedWith(SchedulerLock.class)).as(method.getFullName()).isTrue();
            var lock = method.getAnnotationOfType(SchedulerLock.class);
            assertThat(names.add(lock.name())).as("unique lock: %s", lock.name()).isTrue();
            assertThat(lock.lockAtLeastFor()).isNotBlank();
            checked++;
        }
        assertThat(checked).isPositive();
    }
}
