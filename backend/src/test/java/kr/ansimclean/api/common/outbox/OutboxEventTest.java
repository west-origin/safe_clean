package kr.ansimclean.api.common.outbox;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** 재시도·백오프 규칙 (BACKEND_PLAN.md §2.4) */
// OutboxEvent 엔티티의 상태 메서드(markSent·markFailed)만 본다. DB·목 없이 객체를 만들어 바로 확인한다.
class OutboxEventTest {
    static final Instant NOW = Instant.parse("2026-09-15T05:00:00Z"); // 모든 실패를 같은 '지금' 으로 적어 다음 시도 시각을 정확히 비교한다

    @Test
    void 성공하면_sent() {
        OutboxEvent e = OutboxEvent.pending("BookingAssigned", "{}", NOW);
        assertThat(e.getStatus()).isEqualTo("pending");
        e.markSent(NOW);
        assertThat(e.getStatus()).isEqualTo("sent");
    }

    // 1회 실패 → 2분 뒤, 2회 → 4분 뒤. MAX_ATTEMPTS 번째 실패에서 status 가 failed 가 되어 더 시도하지 않는다
    @Test
    void 실패하면_2의_거듭제곱_분_뒤에_다시_시도하고_6회째에_failed() {
        OutboxEvent e = OutboxEvent.pending("PushSend", "{}", NOW);
        e.markFailed("timeout", NOW);
        assertThat(e.getStatus()).isEqualTo("pending");
        assertThat(e.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(2)));

        e.markFailed("timeout", NOW);
        assertThat(e.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(4)));

        for (int i = 2; i < OutboxEvent.MAX_ATTEMPTS; i++) e.markFailed("timeout", NOW); // 이미 2회 실패했으므로 4회 더
        assertThat(e.getAttempts()).isEqualTo(OutboxEvent.MAX_ATTEMPTS);
        assertThat(e.getStatus()).isEqualTo("failed");
    }

    // 900자짜리 오류 문구를 적으면 last_error 에는 앞 500자만 남는다
    @Test
    void 오류_문구는_500자까지만_남긴다() {
        OutboxEvent e = OutboxEvent.pending("PushSend", "{}", NOW);
        e.markFailed("x".repeat(900), NOW);
        assertThat(e.getLastError()).hasSize(500);
    }
}
