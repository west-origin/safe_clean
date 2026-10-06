package kr.ansimclean.api.booking;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** 자동 재매칭 실행 시각(서울 매시 05분, 08~20시)과 '약속 전에 볼 수 있는가' — 안내 문구가 이걸로 약속한다 */
// static 메서드 nextRun·retryPossible 을 서울 시각 예시로 부른다. 경계(딱 그 시각, 20:05 뒤, 밤)를 특히 본다.
class MatchingRetryWindowTest {
    /** "2026-09-27T07:00:00" 같은 서울 벽시계 글자 → Instant (+09:00 을 붙여 읽는다) */
    static Instant kst(String local) {
        return Instant.parse(local + "+09:00");
    }

    // nextRun(지금) = 지금 다음의 '서울 매시 05분(08~20시)' 실행 시각
    @Test
    void 다음_실행_시각() {
        assertThat(MatchingRetryWindow.nextRun(kst("2026-09-27T07:00:00"))).isEqualTo(kst("2026-09-27T08:05:00"));
        assertThat(MatchingRetryWindow.nextRun(kst("2026-09-27T08:04:59"))).isEqualTo(kst("2026-09-27T08:05:00"));
        assertThat(MatchingRetryWindow.nextRun(kst("2026-09-27T08:05:00"))).isEqualTo(kst("2026-09-27T09:05:00")); // 같은 시각은 이미 돌았다
        assertThat(MatchingRetryWindow.nextRun(kst("2026-09-27T20:04:00"))).isEqualTo(kst("2026-09-27T20:05:00"));
        assertThat(MatchingRetryWindow.nextRun(kst("2026-09-27T20:05:00"))).isEqualTo(kst("2026-09-28T08:05:00")); // 밤에는 돌지 않는다
        assertThat(MatchingRetryWindow.nextRun(kst("2026-09-27T23:30:00"))).isEqualTo(kst("2026-09-28T08:05:00"));
    }

    // retryPossible(지금, 약속) = 다음 실행 시각 + 3시간이 약속보다 앞이어야 true. 딱 같으면 false
    @Test
    void 저녁에_실패한_다음_날_오전_예약은_자동_재매칭이_볼_수_없다() { // 자체 점검 후속 검토
        Instant evening = kst("2026-09-27T21:00:00");
        assertThat(MatchingRetryWindow.retryPossible(evening, kst("2026-09-28T10:00:00"))).isFalse(); // 08:05 + 3h = 11:05
        assertThat(MatchingRetryWindow.retryPossible(evening, kst("2026-09-28T12:00:00"))).isTrue();
        assertThat(MatchingRetryWindow.retryPossible(kst("2026-09-27T14:10:00"), kst("2026-09-27T18:05:01"))).isTrue(); // 다음 실행 15:05 + 3h = 18:05
        assertThat(MatchingRetryWindow.retryPossible(kst("2026-09-27T14:10:00"), kst("2026-09-27T18:05:00"))).isFalse();
        assertThat(MatchingRetryWindow.nextRun(kst("2026-09-27T14:00:00"))).isEqualTo(kst("2026-09-27T14:05:00"));
    }

    // 밤 = 자동 재매칭이 돌지 않는 시간: 08시 전, 21시부터. 마지막 실행(20:05) 뒤 20시대는 아직 낮이다
    @Test
    void 밤은_8시_전과_21시부터다() { // 문제 목록 2번 (사용자 결정 ③)
        assertThat(MatchingRetryWindow.isNight(kst("2026-09-27T07:59:59"))).isTrue();
        assertThat(MatchingRetryWindow.isNight(kst("2026-09-27T08:00:00"))).isFalse();
        assertThat(MatchingRetryWindow.isNight(kst("2026-09-27T14:00:00"))).isFalse();
        assertThat(MatchingRetryWindow.isNight(kst("2026-09-27T20:59:59"))).isFalse();
        assertThat(MatchingRetryWindow.isNight(kst("2026-09-27T21:00:00"))).isTrue();
        assertThat(MatchingRetryWindow.isNight(kst("2026-09-28T00:30:00"))).isTrue();
        // 밤의 끝과 아침 첫 실행이 같은 상수에서 나온다 — 미룬 콜은 밤이 끝난 뒤 첫 실행(08:05)에 나간다
        assertThat(MatchingRetryWindow.nextRun(kst("2026-09-28T07:59:59"))).isEqualTo(kst("2026-09-28T08:05:00"));
    }

    // 밤에 생긴 새 콜은 아침 08:05 까지 미룬다 — 미뤄도 약속까지 3시간 이상 남을 때만. 딱 3시간이면 미루고, 그보다 적으면(급한 예약) 지금 보낸다. 낮에는 미루지 않는다
    @Test
    void 밤에는_아침에도_3시간_넘게_남는_예약만_미룬다() { // 문제 목록 2번
        Instant night = kst("2026-09-27T22:30:00");
        assertThat(MatchingRetryWindow.holdUntilMorning(night, kst("2026-09-28T11:05:00"))).isTrue(); // 08:05 + 3시간 = 11:05
        assertThat(MatchingRetryWindow.holdUntilMorning(night, kst("2026-09-28T11:04:59"))).isFalse();
        assertThat(MatchingRetryWindow.holdUntilMorning(night, kst("2026-09-28T10:00:00"))).isFalse(); // 밤 22:30 에 만든 다음 날 10시 예약은 지금 보낸다
        assertThat(MatchingRetryWindow.holdUntilMorning(night, kst("2026-09-30T10:00:00"))).isTrue();
        assertThat(MatchingRetryWindow.holdUntilMorning(kst("2026-09-28T03:00:00"), kst("2026-09-28T14:00:00"))).isTrue(); // 새벽이면 그날 08:05 까지
        assertThat(MatchingRetryWindow.holdUntilMorning(kst("2026-09-27T14:00:00"), kst("2026-09-30T10:00:00"))).isFalse(); // 낮
    }
}
