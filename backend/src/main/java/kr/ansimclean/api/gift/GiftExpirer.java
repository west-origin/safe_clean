package kr.ansimclean.api.gift;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 1분마다: 받는 기간이 지난 선물을 보낸 사람에게 돌려주고(만료), 결제창을 닫고 1시간이 지난 구매 선물을 정리한다.
 * 한 건씩 GiftService 의 트랜잭션으로 — 한 건이 실패해도 나머지는 계속한다. 여러 서버여도 ShedLock 으로 한 곳만 돈다
 */
@Component
public class GiftExpirer {
    private static final Logger log = LoggerFactory.getLogger(GiftExpirer.class);
    private final GiftService service;

    public GiftExpirer(GiftService service) {
        this.service = service;
    }

    @Scheduled(fixedDelay = 60000)
    @SchedulerLock(name = "GiftExpirer", lockAtLeastFor = "PT50S")
    public void run() {
        for (var id : service.dueGiftIds()) {
            try { service.expire(id); } catch (Exception e) { log.warn("선물 만료 처리 실패 giftId={}", id); }
        }
        for (var id : service.abandonedGiftIds()) {
            try { service.abandonIfUnpaid(id); } catch (Exception e) { log.warn("결제 대기 선물 정리 실패 giftId={}", id); }
        }
    }
}
