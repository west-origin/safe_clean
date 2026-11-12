package kr.ansimclean.api.common.activity;

import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.Roles;
import kr.ansimclean.api.common.time.AppClock;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 활동 이력 한 줄의 모양 (rbac.md §11): 행위자·역할·행위·대상·변경 전후(JSON)·사유·시각 */
class ActivityLogServiceTest {
    static final Instant NOW = Instant.parse("2026-11-12T03:00:00Z");
    ActivityLogRepository repo = mock(ActivityLogRepository.class);
    ActivityLogService service = new ActivityLogService(repo, new AppClock(Clock.fixed(NOW, AppClock.ZONE)));

    @Test
    void 사람이_한_작업은_행위자와_역할과_변경_전후를_JSON_으로_남긴다() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        UUID admin = UUID.randomUUID();
        ActivityLog l = service.record(new CurrentUser(admin, Roles.ADMIN, true, null, true), ActivityLogService.NOTICE_UPDATE, "notice", "n1",
                Map.of("title", "옛 제목"), Map.of("title", "새 제목"), null);
        assertThat(l.getActorId()).isEqualTo(admin);
        assertThat(l.getActorRole()).isEqualTo("admin");
        assertThat(l.getAction()).isEqualTo("notice.update");
        assertThat(l.getTargetType()).isEqualTo("notice");
        assertThat(l.getTargetId()).isEqualTo("n1");
        assertThat(l.getBeforeData()).isEqualTo("{\"title\":\"옛 제목\"}");
        assertThat(l.getAfterData()).isEqualTo("{\"title\":\"새 제목\"}");
        assertThat(l.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void 시스템_작업은_행위자가_비고_없는_값은_null_이다() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        ActivityLog l = service.recordSystem(ActivityLogService.NOTICE_DELETE, "notice", "n1", Map.of("title", "t"), null, "정리");
        assertThat(l.getActorId()).isNull();
        assertThat(l.getActorRole()).isNull();
        assertThat(l.getAfterData()).isNull();
        assertThat(l.getReason()).isEqualTo("정리");
    }

    // 업무와 같은 트랜잭션에서만 쓰게 강제한다 — 트랜잭션 밖에서 부르면 스프링이 예외를 던진다 (IllegalTransactionStateException)
    @Test
    void 기록은_부르는_쪽_트랜잭션_안에서만_된다() throws Exception {
        for (String m : new String[] { "record", "recordSystem" }) {
            var method = java.util.Arrays.stream(ActivityLogService.class.getMethods()).filter(x -> x.getName().equals(m)).findFirst().orElseThrow();
            assertThat(method.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.MANDATORY);
        }
    }
}
