package kr.ansimclean.api.live;

import kr.ansimclean.api.catalog.AppSettingsService;
import kr.ansimclean.api.common.config.AppProperties;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.supabase.SupabaseStorageClient;
import kr.ansimclean.api.common.time.AppClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;

/** 매일 04:00 (Asia/Seoul): 완료 후 recording_retention_days(90) 가 지난 녹화본을 Storage 에서 지운다 (§2.5·§8.2, 약관 recording 과 일치) */
// 같은 작업에서 웹훅 중복 방어 행(livekit_events)도 7일이 지나면 지운다.
// 보존 일수는 app_settings 의 recording_retention_days 값(기본 데이터 90)을 매번 읽는다.
@Component
public class RecordingRetention {
    public static final String SETTING_RETENTION_DAYS = "recording_retention_days"; // app_settings 의 키 (녹화본 보존 일수)
    private static final Logger log = LoggerFactory.getLogger(RecordingRetention.class);

    private final LiveSessionRepository sessions;
    private final SupabaseStorageClient storage;
    private final AppSettingsService settings;
    private final AppProperties props;
    private final AppClock clock;
    private final TransactionTemplate tx; // 코드로 트랜잭션을 여는 도구 (run() 참고)

    private final LivekitEventRepository webhookEvents;

    public RecordingRetention(LiveSessionRepository sessions, LivekitEventRepository webhookEvents, SupabaseStorageClient storage, AppSettingsService settings, AppProperties props, AppClock clock, TransactionTemplate tx) {
        this.sessions = sessions;
        this.webhookEvents = webhookEvents;
        this.storage = storage;
        this.settings = settings;
        this.props = props;
        this.clock = clock;
        this.tx = tx;
    }

    // 실패해도 예외를 밖으로 던지지 않고 로그만 남긴다. 그때는 묶음 전체(이벤트 행 삭제 포함)가 되돌려지고 다음 날 다시 돈다 —
    // 이미 지운 Storage 파일은 되돌릴 수 없지만, 다시 지울 때 없는 파일(404)을 성공으로 쳐서 그때 세션이 정리된다
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "RecordingRetention", lockAtLeastFor = "PT1M")
    public void run() {
        // run() 이 같은 클래스의 purge() 을 직접 부르면 @Transactional 프록시를 거치지 않는다(Spring 자기 호출).
        // 그래서 트랜잭션은 여기서 TransactionTemplate 으로 연다. purge() 의 @Transactional 은 빈을 통해 직접 부르는 테스트용.
        // 트랜잭션 없이 돌면 Storage 파일은 지워지는데 recordingDeleted() 가 저장되지 않아 ready 로 남는다(지워진 파일의 URL 발급·매일 재삭제).
        try {
            int n = tx.execute(status -> purge());
            if (n > 0) log.info("녹화본 {}건 삭제(보존 기간 경과)", n);
        } catch (Exception e) {
            log.error("녹화본 정리 실패", e);
        }
    }

    /** 웹훅 중복 방어 행 보존 기간 — 재조정기·재전송 창(수 시간)보다 넉넉히 */
    static final Duration EVENT_RETENTION = Duration.ofDays(7);

    /**
     * 정리 한 번. ① 7일 지난 livekit_events 행 삭제 ② 녹화가 ready 이고 세션이 끝난 지 보존 일수가 지난 것마다
     * Storage 파일을 지우고 세션의 녹화 정보를 비운다(recordingDeleted → none). @return 지운 녹화본 수.
     * 한 건의 Storage 삭제가 실패하면 그 건은 ready 로 남겨 두고 다음 건으로 넘어간다 — 내일 다시 시도된다
     * (수리 사진 정리 booking/RepairPhotoService.deleteUnattached 도 같은 방식이다)
     */
    @Transactional
    public int purge() {
        long events = webhookEvents.deleteByReceivedAtBefore(clock.now().minus(EVENT_RETENTION));
        if (events > 0) log.info("livekit_events {}건 정리", events);
        int days = settings.integer(SETTING_RETENTION_DAYS);
        int count = 0;
        // 기준은 세션이 끝난 시각(ended_at). recordingDeleted() 의 변경은 save 없이 커밋 때 저장된다 (CODE_GUIDE.md 3-3)
        for (LiveSession s : sessions.findByRecordingStatusAndEndedAtBefore(LiveSession.REC_READY, clock.now().minus(Duration.ofDays(days)))) {
            try {
                storage.delete(props.storage().recordingsBucket(), s.getRecordingPath());
                s.recordingDeleted();
                count++;
            } catch (ApiException e) {
                log.warn("녹화본 삭제 실패 {} — 내일 다시", s.getRecordingPath());
            }
        }
        return count;
    }
}
