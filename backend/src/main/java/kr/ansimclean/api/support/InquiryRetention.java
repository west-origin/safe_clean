package kr.ansimclean.api.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 04:30 (Asia/Seoul): 탈퇴한 사용자의 1:1 문의 중 보관 기간(탈퇴 + 3년)이 지난 것을 지운다
 * (전자상거래법 소비자 불만·분쟁처리 기록, 사용자 결정 2026-09-27 — 자체 점검 #31. 예전에는 기간 없이 남았다)
 * 지울 시각(purge_after)은 탈퇴 때 AuthService.deleteAccount 가 SupportService.retainAfterDeletion 으로 적어 둔다.
 * 실제 삭제는 SupportService.purgeExpiredInquiries → InquiryRepository.deletePurgeable
 * (purge_after 가 지금보다 이른 행).
 * 이 정리가 못 지우는 문의(보관 기한이 안 적힌 것)는 탈퇴 5년 파기(DeletedAccountPurger)가 지운다. 지금은 문의 저장이
 * holdLive 로 탈퇴와 줄을 서서(SupportService.submit) 탈퇴 뒤에 기한 없이 남는 문의가 생기지 않아야 한다 — 파기는 대비책이다.
 */
@Component
public class InquiryRetention {
    private static final Logger log = LoggerFactory.getLogger(InquiryRetention.class);

    private final SupportService support;

    public InquiryRetention(SupportService support) {
        this.support = support;
    }

    // 예외를 여기서 잡아 로그만 남긴다 — 지우지 못한 행은 purge_after 가 그대로라 다음 날 실행이 다시 지운다
    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "InquiryRetention", lockAtLeastFor = "PT1M")
    public void run() {
        try {
            int n = support.purgeExpiredInquiries();
            if (n > 0) log.info("보관 기간이 지난 1:1 문의 {}건 삭제", n);
        } catch (Exception e) {
            log.error("1:1 문의 보관 기간 정리 실패", e); // 다음 날 다시
        }
    }
}
