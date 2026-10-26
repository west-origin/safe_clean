package kr.ansimclean.api.booking;

import kr.ansimclean.api.common.events.ManagerLate;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.time.AppClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * 1분마다: 배정된 매니저가 약속 시간 + 지각 기준(tardyMinutes, 기본 30분)이 지나도 청소를 시작하지 않았으면 고객에게 한 번 알린다
 * (docs/manager-policy.md §2 "고객에게 자동으로 알림이 갑니다", 사용자 결정 2026-09-27 — 자체 점검 #25). 매니저 점수(0.5점)는 시작할 때 JobService 가 매긴다.
 * 기다리는 동안 알려야 쓸모가 있어서 시작 때가 아니라 기준 시각이 지났을 때 보낸다. 약속 시간이 LOOKBACK 보다 오래 지난 예약은 보지 않는다(배포 직후 옛 예약에 몰아 보내지 않게). 끝내 시작하지 않은 예약은 방문일 이틀 뒤 03:10 StaleBookingCleaner 가 미방문으로 취소한다.
 * 흐름: 후보 id 목록(잠금 없이) → 한 건씩 잠그고 다시 확인 → 예약에 '알림 보냄' 표시(late_notified_at) + 아웃박스에 ManagerLate 기록.
 * 실제 푸시는 커밋 뒤 아웃박스가 보내고, PushHandler 가 알림 설정 토글과 상관없이 고객에게 보낸다.
 * 지각 패널티(JobService.recordTardy)와 기준이 같다: 여기는 약속 + tardyMinutes 가 되도록 시작하지 않은 예약에 알리고,
 * 패널티는 약속 + tardyMinutes 보다 뒤에 시작하면 매긴다 — 안내를 받은 예약은 시작할 때 모두 지각으로 기록된다 (문제 목록 26번).
 */
@Component
public class LateStartNotifier {
    static final Duration LOOKBACK = Duration.ofHours(12); // 약속 시간이 이보다 오래 지난 예약은 건너뛴다 (위 설명)
    private static final Logger log = LoggerFactory.getLogger(LateStartNotifier.class);

    private final BookingRepository bookings;
    private final OutboxService outbox;
    private final AppClock clock;
    private final TransactionTemplate tx;
    private final kr.ansimclean.api.profile.ManagerPenaltyService penalties; // 여기서는 지각 기준 분(tardyMinutes, 운영 설정값)만 읽는다

    public LateStartNotifier(BookingRepository bookings, OutboxService outbox, AppClock clock, TransactionTemplate tx, kr.ansimclean.api.profile.ManagerPenaltyService penalties) {
        this.bookings = bookings;
        this.outbox = outbox;
        this.clock = clock;
        this.tx = tx;
        this.penalties = penalties;
    }

    /** 스케줄러 진입점. 트랜잭션 하나로 notifyDue 를 돌리고, 실패하면 로그만 남긴다(예외를 밖으로 던지지 않는다) */
    @Scheduled(fixedDelay = 60000)
    @SchedulerLock(name = "LateStartNotifier", lockAtLeastFor = "PT50S")
    public void run() {
        // 자기 호출은 @Transactional 프록시를 거치지 않는다 — 트랜잭션은 여기서 연다 (StaleBookingCleaner 와 같다)
        // 한 번의 실행 전체가 한 묶음이다 — 한 건에서 예외가 나면 이번에 표시·기록한 다른 건도 되돌려지고 다음 주기(1분 뒤)에 다시 한다
        try {
            int n = tx.execute(status -> notifyDue());
            if (n > 0) log.info("매니저 지각 안내 {}건", n);
        } catch (Exception e) {
            log.error("매니저 지각 안내 실패", e); // 다음 주기에 다시
        }
    }

    /** 보낸 건수. 잠근 뒤 다시 확인한다 — 그 사이 매니저가 시작했으면 보내지 않는다 */
    // run() 의 자기 호출에는 이 @Transactional 이 먹지 않아 위 tx 가 묶음을 연다. 이 설정은 주입받은 빈으로 직접 부를 때(예: JobFlowIT) 쓰인다
    @Transactional
    public int notifyDue() {
        Instant now = clock.now();
        int minutes = penalties.tardyMinutes();
        Instant due = now.minus(Duration.ofMinutes(minutes)); // 약속 시간이 이 시각 이하면 기준(minutes)을 넘겼다
        int count = 0;
        // 쿼리는 약속 시간 < to 로 찾으므로 1ms 를 더해 약속 시간이 due 와 딱 같은 예약도 넣는다
        for (UUID id : bookings.findLateUnstartedIds(now.minus(LOOKBACK), due.plusMillis(1))) { // 잠금 순서 = id 오름차순 (OfferExpirer 와 같다)
            Booking b = bookings.findForUpdate(id).orElse(null);
            // markLateNotified: 아직 scheduled 이고 처음 알리는 것일 때만 true + late_notified_at 을 채운다 (커밋 때 UPDATE 된다)
            if (b == null || !b.markLateNotified(now, minutes)) continue;
            outbox.publish(new ManagerLate(b.getId(), b.getCustomerId(), b.getManagerId(), minutes));
            log.warn("매니저 지각: 예약={} 매니저={} 약속={}", b.getId(), b.getManagerId(), b.getStartsAt());
            count++;
        }
        return count;
    }
}
