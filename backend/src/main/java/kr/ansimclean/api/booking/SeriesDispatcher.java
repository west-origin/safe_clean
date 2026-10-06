package kr.ansimclean.api.booking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 09:00 (Asia/Seoul): 정기청소 회차 매칭·종료·종료 안내 (BACKEND_PLAN.md §2.5).
 * 예전 03:00 에는 매니저가 자는 새벽에 콜·푸시가 갔다 — 밤에는 새 콜을 아침으로 미루는 규칙(MatchingRetryWindow)과 맞춰 낮으로 옮겼다 (문제 목록 2번, SeriesDispatcherTest 가 묶는다).
 * 계획은 common/scheduling 에 두지만 common 은 기능 패키지를 볼 수 없으므로(ArchitectureTest) 기능 패키지 안에 둔다.
 * 실제 일은 BookingSeriesService 가 한다. 그 클래스 전체에 트랜잭션이 걸려 있고 여기는 묶음이 없어, 아래 호출은 각각 따로 커밋된다.
 */
@Component
public class SeriesDispatcher {
    private static final Logger log = LoggerFactory.getLogger(SeriesDispatcher.class);

    private final BookingSeriesService series;

    public SeriesDispatcher(BookingSeriesService series) {
        this.series = series;
    }

    /**
     * 하루 한 번: 회차 매칭 → 끝난 시리즈 닫기 → 종료 안내. 앞 단계에서 예외가 나면 뒤 단계도 그날은 건너뛴다.
     * 이미 끝난 앞 단계는 커밋돼 남고, 예외가 난 단계는 그 단계 전체가 되돌려진다(예: 매칭 중 한 회차가 실패하면 그날 매칭 전부).
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void run() {
        try {
            // dispatchDue: 방문이 dispatch_lead_days 일 안으로 다가온 requested 회차를 매칭에 넣는다.
            // 직전 회차 매니저가 있으면 그에게 먼저 — 그 시리즈를 취소·미방문한 매니저는 빼고 (BookingSeriesService.previousManager)
            int dispatched = series.dispatchDue();
            // endExpired: 기간(term_end)이 지난 active 시리즈를 ended 로 / notifyEnding: 종료일이 7일 안으로 들어온 시리즈에 고객 푸시 한 번
            int ended = series.endExpired();
            int notified = series.notifyEnding();
            log.info("정기청소 스케줄러: 매칭 {}건, 종료 {}건, 종료 안내 {}건", dispatched, ended, notified);
        } catch (Exception e) {
            log.error("정기청소 스케줄러 실패", e); // 내일 다시 돈다
        }
    }
}
