package kr.ansimclean.api.booking;

import kr.ansimclean.api.common.time.AppClock;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 정기청소 회차 매칭 스케줄러는 낮에 돈다 — 밤에 돌면 그 회차의 콜이 모두 아침으로 미뤄진다 (문제 목록 2번, 사용자 결정 ③) */
// 리플렉션으로 run() 의 cron 을 꺼내 '매일 09:00' 인지, 그 시각이 MatchingRetryWindow 의 밤이 아닌지 본다.
// 예전 03:00 에는 매니저가 자는 새벽에 콜·푸시가 갔다
class SeriesDispatcherTest {
    @Test
    void 매일_09시에_돌고_그_시각은_밤이_아니다() throws Exception {
        String cron = SeriesDispatcher.class.getMethod("run").getAnnotation(org.springframework.scheduling.annotation.Scheduled.class).cron();
        assertThat(cron).isEqualTo("0 0 9 * * *");
        Instant runAt = AppClock.toInstant(LocalDate.of(2026, 9, 28), LocalTime.of(9, 0));
        assertThat(MatchingRetryWindow.isNight(runAt)).isFalse();
    }
}
