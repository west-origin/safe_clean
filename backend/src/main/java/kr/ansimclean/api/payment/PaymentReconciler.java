package kr.ansimclean.api.payment;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/** 웹훅 유실·승인 응답 유실·취소 이벤트 누락은 상태 기반으로 복구한다. */
@Component
public class PaymentReconciler {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PaymentReconciler.class);
    private final CheckoutService service;
    public PaymentReconciler(CheckoutService service) { this.service = service; }
    @Scheduled(fixedDelay = 60000)
    @SchedulerLock(name = "PaymentReconciler", lockAtLeastFor = "PT50S")
    public void reconcile() {
        for (var id : service.due()) {
            try { service.enqueue(id); }
            catch (Exception e) { log.warn("결제 재조정 예약 실패 id={}", id); }
        }
    }
}
