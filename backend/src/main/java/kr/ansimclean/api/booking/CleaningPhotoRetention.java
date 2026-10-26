package kr.ansimclean.api.booking;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 04:20 (Asia/Seoul): 청소가 끝난 지 보존 일수(app_settings recording_retention_days, 기본 90 — 녹화본과 같다)가 지난
 * 예약의 전·후 사진을 Storage 와 cleaning_photos 에서 지운다. 실제 일은 CleaningPhotoService.deleteExpired 가 한다.
 * Storage 삭제는 외부 호출이라 트랜잭션 없이 한 예약씩. 실패한 예약만 건너뛰고 다음 날 다시.
 * 한 번에 최대 CleaningPhotoService.RETENTION_BATCH 장까지라 더 남으면 다음 날 이어서 지운다.
 */
@Component
public class CleaningPhotoRetention {
    private static final Logger log = LoggerFactory.getLogger(CleaningPhotoRetention.class);

    private final CleaningPhotoService photos;

    public CleaningPhotoRetention(CleaningPhotoService photos) {
        this.photos = photos;
    }

    /** 정리 한 번. 실패해도 예외를 밖으로 던지지 않고 로그만 남긴다 — 다음 날 다시 돈다 */
    // 여러 서버가 떠 있어도 한 대만 돌도록 @SchedulerLock (모든 @Scheduled 에 필요 — SchedulingConfigTest 가 검사한다)
    @Scheduled(cron = "0 20 4 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "CleaningPhotoRetention", lockAtLeastFor = "PT1M")
    public void run() {
        try {
            int n = photos.deleteExpired();
            if (n > 0) log.info("보존 기간이 지난 청소 전·후 사진 {}장 삭제", n);
        } catch (Exception e) {
            log.error("청소 전·후 사진 정리 실패", e);
        }
    }
}
