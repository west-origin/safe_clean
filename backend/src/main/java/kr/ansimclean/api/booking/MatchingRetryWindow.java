package kr.ansimclean.api.booking;

import kr.ansimclean.api.common.time.AppClock;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;

/**
 * 자동 재매칭(offer/MatchingRetry)이 도는 시각과 조건 — 매칭 실패 안내가 '다시 찾아볼게요' 라고 말해도 되는지 여기서 판단한다 (자체 점검 후속 검토).
 * 서울 매시 {@value #MINUTE}분, {@value #FIRST_HOUR}~{@value #LAST_HOUR}시. 약속 시각이 그 실행보다 MIN_LEAD(3시간) 넘게 남아야 보낸다.
 * MatchingRetry 의 cron("0 5 8-20 * * *")과 반드시 같아야 한다 — MatchingRetryTest 가 확인한다
 * 예: OfferService.matchingFailed(매칭 실패 푸시 문구), BookingService.cleanerLabel(예약 카드·상세의 매니저 칸 문구).
 * 자동 재매칭이 실제로 거르는 기준(OfferService.RETRY_MIN_LEAD)도 MIN_LEAD 를 그대로 가져다 써서, 안내와 실제 동작이 어긋나지 않는다.
 * '밤' 도 여기서 정한다 (문제 목록 2번, 사용자 결정 ③ 2026-09-28): 자동 재매칭이 돌지 않는 시간(FIRST_HOUR 시 전, LAST_HOUR 시 다음 시간부터)에는
 * 새 콜을 보내지 않고 아침 첫 실행(nextRun)까지 미룬다 — 미루는 곳은 OfferService, 아침에 보내는 곳은 MatchingRetry. 경계는 MatchingRetryWindowTest 가 묶는다.
 * 상수와 static 메서드만 있어 객체를 만들지 않는다.
 */
public final class MatchingRetryWindow {
    public static final int FIRST_HOUR = 8; // 첫 실행 시(서울)
    public static final int LAST_HOUR = 20; // 마지막 실행 시(서울)
    public static final int MINUTE = 5; // 매시 몇 분에 도는가
    public static final Duration MIN_LEAD = Duration.ofHours(3);

    private MatchingRetryWindow() {}

    /** now 다음(같은 시각은 제외) 자동 재매칭 실행 시각 */
    public static Instant nextRun(Instant now) {
        // 서울 벽시계로 바꿔 '이번 시의 05분 00초' 를 만든다
        ZonedDateTime t = now.atZone(AppClock.ZONE).withMinute(MINUTE).withSecond(0).withNano(0);
        if (!t.toInstant().isAfter(now)) t = t.plusHours(1); // 이미 지났거나 딱 그 시각이면(이번 실행은 이미 돌았다) 다음 시
        if (t.getHour() < FIRST_HOUR) t = t.withHour(FIRST_HOUR); // 새벽이면 그날 08:05
        if (t.getHour() > LAST_HOUR) t = t.plusDays(1).withHour(FIRST_HOUR); // 20:05 뒤면 다음 날 08:05
        return t.toInstant();
    }

    /**
     * 밤인가 — 서울 {@value #FIRST_HOUR}시 전(00:00~07:59) 또는 {@value #LAST_HOUR}시 다음 시간부터(21:00~23:59).
     * 마지막 실행({@value #LAST_HOUR}:05) 뒤 그 시의 나머지(20:05~20:59)는 아직 낮으로 본다 — 매니저 대부분이 깨어 있고, 21시부터 미룬다
     */
    public static boolean isNight(Instant now) {
        int hour = now.atZone(AppClock.ZONE).getHour();
        return hour < FIRST_HOUR || hour > LAST_HOUR;
    }

    /**
     * 지금 생긴 새 콜을 아침 첫 실행(nextRun)까지 미룰 것인가: 밤이고, 미뤄도 약속 시각까지 MIN_LEAD(3시간) 이상 남을 때 true.
     * 미루면 3시간보다 적게 남는 예약(예: 밤 22시에 만든 다음 날 10시 예약 — 08:05 + 3시간 = 11:05)은 급한 예약이라 false = 지금 보낸다.
     * 딱 3시간 남으면 미룬다(적게 남는 것이 아니다). 부르는 곳: OfferService (새 콜을 보내는 자동 경로 전부 — 운영자 재매칭은 빼고)
     */
    public static boolean holdUntilMorning(Instant now, Instant startsAt) {
        return isNight(now) && !nextRun(now).plus(MIN_LEAD).isAfter(startsAt);
    }

    /** 약속 시각 전에 자동 재매칭이 한 번이라도 이 예약을 볼 수 있는가 */
    // 다음 실행 시각 + MIN_LEAD(3시간)가 약속 시각보다 앞서야 true. 딱 같으면 false (MatchingRetryWindowTest 가 경계를 확인한다)
    public static boolean retryPossible(Instant now, Instant startsAt) {
        return nextRun(now).plus(MIN_LEAD).isBefore(startsAt);
    }
}
