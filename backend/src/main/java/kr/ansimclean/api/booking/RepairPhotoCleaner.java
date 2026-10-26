package kr.ansimclean.api.booking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 03:40 (Asia/Seoul): 올린 뒤 24시간 동안 예약에 붙지 않은 수리 사진을 지운다 (감사 M-04 — 예약을 그만두면 집 사진이 영원히 남았다).
 * Storage 삭제는 외부 호출이라 트랜잭션 없이 한 건씩(원장 삭제는 레포지토리 트랜잭션). 실패한 장만 건너뛰고 다음 날 다시.
 * 실제 일은 RepairPhotoService.deleteUnattached 가 한다. 한 번에 최대 CLEANUP_BATCH 장까지라 더 남으면 다음 날 이어서 지운다.
 */
@Component
public class RepairPhotoCleaner {
    private static final Logger log = LoggerFactory.getLogger(RepairPhotoCleaner.class);

    private final RepairPhotoService photos;

    public RepairPhotoCleaner(RepairPhotoService photos) {
        this.photos = photos;
    }

    /** 정리 한 번. 실패해도 예외를 밖으로 던지지 않고 로그만 남긴다 — 다음 날 다시 돈다 */
    // 한 장의 삭제 실패는 deleteUnattached 안에서 경고 로그로 끝나고 남은 장은 계속 지운다. 여기서 잡는 것은 후보 목록을 읽지 못한 경우 등 정리 전체의 실패다
    @Scheduled(cron = "0 40 3 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "RepairPhotoCleaner", lockAtLeastFor = "PT1M")
    public void run() {
        try {
            int n = photos.deleteUnattached();
            if (n > 0) log.info("예약에 붙지 않은 수리 사진 {}장 삭제", n);
        } catch (Exception e) {
            log.error("수리 사진 정리 실패", e);
        }
    }
}
