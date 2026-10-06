package kr.ansimclean.api.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 앱의 시계. 시간대는 항상 Asia/Seoul. 테스트는 new AppClock(Clock.fixed(...)) 로 고정한다.
 * 저장은 Instant(UTC), "오늘"·"내일" 같은 벽시계 판단은 여기서만 한다.
 * 서버에서는 ClockConfig 가 system() 으로 만든 빈 하나를 서비스·스케줄러에 넣어 준다.
 * 용어: Instant = 시간대 없는 절대 시각(UTC), ZonedDateTime = 시간대가 붙은 날짜+시각, LocalDate/LocalTime = 시간대 없는 날짜/시각.
 * 정적(static) 메서드 toInstant·toSeoul 은 '지금' 이 필요 없는 변환이라 빈 없이 AppClock.toSeoul(…) 처럼 바로 부른다.
 */
public class AppClock {
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul"); // 날짜 경계·표시에 쓰는 유일한 시간대

    private final Clock clock;

    /** 주어진 시계를 서울 시간대로 감싼다. 테스트는 고정 시계(Clock.fixed)를 넣어 '지금' 을 정한다 */
    public AppClock(Clock clock) {
        this.clock = clock.withZone(ZONE);
    }

    /** 실제 시스템 시계 (ClockConfig 의 빈이 이것을 쓴다) */
    public static AppClock system() {
        return new AppClock(Clock.system(ZONE));
    }

    /** 지금 시각 (UTC 기준 Instant). DB 에 저장하는 시각은 이 값을 쓴다 */
    public Instant now() {
        return clock.instant();
    }

    /** 지금 서울 날짜+시각 — 근무시간·이번 달 같은 벽시계 판단용 */
    public ZonedDateTime nowSeoul() {
        return now().atZone(ZONE);
    }

    /** 서울 기준 오늘 날짜 (UTC 자정이 아니라 서울 자정에 날짜가 바뀐다) */
    public LocalDate today() {
        return nowSeoul().toLocalDate();
    }

    /** 서울 벽시계(날짜 + 시각) → Instant */
    public static Instant toInstant(LocalDate date, LocalTime time) {
        return LocalDateTime.of(date, time).atZone(ZONE).toInstant();
    }

    /** Instant → 서울 날짜+시각 (표시용 문자열은 KoreanFormat 이 이 결과로 만든다) */
    public static ZonedDateTime toSeoul(Instant instant) {
        return instant.atZone(ZONE);
    }
}
