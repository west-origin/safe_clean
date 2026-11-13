package kr.ansimclean.api.gift;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 1분마다: 취소된 예약에 쓰인 금액권을 돌려준다 (VoucherService.restoreCancelled, 한 건씩 각자 트랜잭션).
 * 예약 취소 경로(고객·시스템·정기 해지 …)마다 부르지 않고 상태로 찾는다 — 결제 환불(PaymentReconciler)과 같은 방식
 */
@Component
public class VoucherRestorer {
    private static final Logger log = LoggerFactory.getLogger(VoucherRestorer.class);
    private final VoucherService service;

    public VoucherRestorer(VoucherService service) {
        this.service = service;
    }

    @Scheduled(fixedDelay = 60000)
    @SchedulerLock(name = "VoucherRestorer", lockAtLeastFor = "PT50S")
    public void run() {
        for (var id : service.cancelledUseIds()) {
            try { service.restoreCancelled(id); } catch (Exception e) { log.warn("금액권 복원 실패 useId={}", id); }
        }
    }
}
