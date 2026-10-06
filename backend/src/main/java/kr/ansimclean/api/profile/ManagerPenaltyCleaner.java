package kr.ansimclean.api.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 매일 03:20 (Asia/Seoul): 12개월 지난 패널티 만료 + 무사고 3개월 회복 (docs/manager-policy.md).
 * 만료·회복을 TransactionTemplate 으로 따로따로 묶는다 (검수 1차에서 스케줄러가 같은 클래스의 @Transactional 메서드를 직접 불러
 * 트랜잭션 없이 돌던 문제를 막은 것과 같은 방식). 다만 여기서 부르는 expireOld·recover 는 다른 빈(ManagerPenaltyService,
 * 클래스에 @Transactional)이라 그것만으로도 트랜잭션이 열린다 — tx.execute 가 바깥 묶음을 먼저 열고 서비스 쪽은 거기에 합쳐진다
 * (CODE_GUIDE.md 3-3). 어느 쪽이든 만료와 회복은 따로 커밋된다.
 * 실제 규칙(무엇이 만료이고 언제 회복하는가)은 ManagerPenaltyService.expireOld·recover 에 있고, 여기는 시각 맞춰 부르기만 한다.
 * 12개월·3개월은 기본값이고 실제 값은 app_settings.manager_penalty(expireMonths·recoveryMonths)에서 온다.
 */
@Component
public class ManagerPenaltyCleaner {
    private static final Logger log = LoggerFactory.getLogger(ManagerPenaltyCleaner.class);

    private final ManagerPenaltyService penalties;
    private final TransactionTemplate tx;

    public ManagerPenaltyCleaner(ManagerPenaltyService penalties, TransactionTemplate tx) {
        this.penalties = penalties;
        this.tx = tx;
    }

    /**
     * 스케줄러 입구. 만료 → 회복 순으로 트랜잭션을 하나씩 따로 연다 (회복이 실패해도 만료는 이미 커밋돼 남는다).
     * 만료에서 예외가 나면 회복은 이번에는 건너뛰고, 어느 쪽이든 로그만 남긴 뒤 다음 날 다시 돈다
     */
    @Scheduled(cron = "0 20 3 * * *", zone = "Asia/Seoul")
    public void run() {
        try {
            int expired = tx.execute(status -> penalties.expireOld()); // 만료로 바꾼 행 수 (recover 는 회복 행을 적은 매니저 수)
            int recovered = tx.execute(status -> penalties.recover());
            if (expired > 0 || recovered > 0) log.info("패널티 정리: 만료 {}건, 회복 {}건", expired, recovered);
        } catch (Exception e) {
            log.error("패널티 정리 실패", e); // 내일 다시 돈다
        }
    }
}
