package kr.ansimclean.api.booking;

import kr.ansimclean.api.common.events.BookingCancelled;
import kr.ansimclean.api.common.events.BookingUnassigned;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.time.AppClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 매일 03:10 (Asia/Seoul, BACKEND_PLAN.md §2.5): 방문일이 지났는데 배정되지 않은 예약 → cancelled(unassigned) + 고객 푸시,
 * 배정됐는데 시작하지 않은 예약(방문일 + 1일 경과) → cancelled(not_started) + 운영자 로그.
 * 미방문 취소는 고객·매니저 푸시(BookingCancelled)와 매니저 미방문 패널티도 남긴다. 두 경우 모두 남은 회차가 없어진 정기청소 시리즈는 닫는다.
 * 마지막으로, 시작했지만 '청소 완료' 를 누르지 않은 채 방문일 + 1일이 지난 작업은 바꾸지 않고 운영자 확인용 오류 로그만 남긴다.
 * '오늘' 은 AppClock 의 서울 날짜다.
 */
@Component
public class StaleBookingCleaner {
    // 취소 사유 값 (bookings.cancel_reason·booking_status_history.reason 에 저장). PushHandler 는 "not_started" 를 보고 미방문 푸시 문구를 고른다
    // (PushHandler.NOT_STARTED 가 같은 글자를 따로 들고 있어 바꾸면 둘 다). 고객이 이 값을 사유로 보내면 BookingService 가 "customer" 로 바꿔 저장한다
    public static final String REASON_UNASSIGNED = "unassigned";
    public static final String REASON_NOT_STARTED = "not_started";
    private static final Logger log = LoggerFactory.getLogger(StaleBookingCleaner.class);

    private final BookingRepository bookings;
    private final BookingStatusHistoryRepository history;
    private final OutboxService outbox;
    private final BookingDispatcher dispatcher; // 실제로는 @Primary 인 offer/OfferService 가 들어온다
    private final BookingService bookingService;
    private final AppClock clock;
    private final TransactionTemplate tx;
    private final kr.ansimclean.api.profile.ManagerPenaltyService penalties;

    public StaleBookingCleaner(BookingRepository bookings, BookingStatusHistoryRepository history, OutboxService outbox, BookingDispatcher dispatcher,
                               BookingService bookingService, AppClock clock, TransactionTemplate tx,
                               kr.ansimclean.api.profile.ManagerPenaltyService penalties) {
        this.penalties = penalties;
        this.bookings = bookings;
        this.history = history;
        this.outbox = outbox;
        this.dispatcher = dispatcher;
        this.bookingService = bookingService;
        this.clock = clock;
        this.tx = tx;
    }

    /** 스케줄러 진입점. 실패해도 예외를 밖으로 던지지 않고 로그만 남긴다 — 다음 날 다시 돈다 */
    @Scheduled(cron = "0 10 3 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "StaleBookingCleaner", lockAtLeastFor = "PT1M")
    public void run() {
        // run() 이 같은 클래스의 clean() 을 직접 부르면 @Transactional 프록시를 거치지 않는다(Spring 자기 호출).
        // 그래서 트랜잭션은 여기서 TransactionTemplate 으로 연다. clean() 의 @Transactional 은 지금 먹는 곳이 없다 —
        // 단위 테스트(StaleBookingCleanerTest)도 new 로 만들어 clean() 을 부르므로 스프링을 거치지 않는다. 다른 빈이 직접 부를 때만 적용된다.
        try {
            int n = tx.execute(status -> clean());
            if (n > 0) log.info("지난 예약 {}건 취소", n);
        } catch (Exception e) {
            log.error("지난 예약 정리 실패", e);
        }
    }

    /**
     * 정리 한 번. 돌려주는 값 = 취소한 예약 수(완료 안 눌린 작업 로그는 세지 않는다).
     * 한 트랜잭션이라 중간에 예외가 나면 이번 실행의 취소·이력·아웃박스 기록이 모두 되돌려진다.
     */
    @Transactional
    public int clean() {
        LocalDate today = clock.today();
        Instant now = clock.now();
        int count = 0;
        // 1) 방문일(서울 날짜)이 오늘보다 앞인데 아직 매니저가 없는 예약(requested·assigning) → 시스템 취소 + 고객 푸시
        for (Booking b : bookings.findByStatusInAndServiceDateBefore(List.of(BookingStatus.REQUESTED, BookingStatus.ASSIGNING), today)) {
            BookingStatus from = b.getStatus();
            b.cancelBySystem(REASON_UNASSIGNED, now);
            history.save(BookingStatusHistory.of(b.getId(), from, BookingStatus.CANCELLED, null, REASON_UNASSIGNED, now));
            dispatcher.onCancelled(b); // 남은 pending 오퍼를 닫는다 — 매니저 콜 목록에 취소된 예약이 보이지 않게
            outbox.publish(new BookingUnassigned(b.getId(), b.getCustomerId())); // 고객 푸시 — 알림 설정 토글과 무관하게 보낸다(PushHandler)
            if (b.getSeriesId() != null) bookingService.endSeriesIfNothingLeft(b.getSeriesId(), now); // 회차가 다 사라진 시리즈가 active 로 남아 탈퇴를 막지 않게
            count++;
        }
        // 2) 매니저가 배정됐는데(scheduled) 방문일이 어제보다 앞 = 방문일 이틀 뒤 새벽까지 시작하지 않은 예약 → 미방문 취소
        for (Booking b : bookings.findByStatusInAndServiceDateBefore(List.of(BookingStatus.SCHEDULED), today.minusDays(1))) {
            b.cancelBySystem(REASON_NOT_STARTED, now);
            history.save(BookingStatusHistory.of(b.getId(), BookingStatus.SCHEDULED, BookingStatus.CANCELLED, null, REASON_NOT_STARTED, now));
            log.warn("매니저 미방문: 예약={} 매니저={} 방문일={}", b.getId(), b.getManagerId(), b.getServiceDate());
            // 고객·매니저 둘 다 알린다(PushHandler 가 reason 으로 미방문 문구를 고른다) + 회차가 다 사라진 시리즈는 닫는다 — 위 unassigned 경로와 같게 (검수 4차: 둘 다 빠져 있어 아무도 모르고 시리즈가 active 로 남아 탈퇴가 막혔다)
            outbox.publish(new BookingCancelled(b.getId(), b.getCustomerId(), b.getManagerId(), REASON_NOT_STARTED));
            if (b.getManagerId() != null) { // 미방문 패널티 (docs/manager-policy.md, 사용자 승인 2026-09-22)
                penalties.record(b.getManagerId(), b.getId(), b.getSeriesId(), kr.ansimclean.api.profile.ManagerPenalty.NO_SHOW, REASON_NOT_STARTED, null);
            }
            if (b.getSeriesId() != null) bookingService.endSeriesIfNothingLeft(b.getSeriesId(), now);
            count++;
        }
        // 시작했는데 '청소 완료' 를 누르지 않은 채 방문일이 지난 작업 — 닫는 길이 운영자 완료뿐이라 알린다(자동으로 닫지 않는다: 매니저 확인 없이 정산이 생기지 않게).
        // 그대로 두면 고객 탈퇴가 막히고 라이브 재조정·녹화가 끝없이 이어진다 (자체 점검 #17, docs/runbook.md)
        // 3) 작업 중(in_progress·paused)인 채 방문일이 어제보다 앞 → 상태는 두고 오류 로그만 (count 에 넣지 않는다)
        for (Booking b : bookings.findByStatusInAndServiceDateBefore(BookingStatus.ACTIVE, today.minusDays(1))) {
            log.error("청소 완료가 안 눌린 작업 — 운영자 확인 필요(POST /admin/jobs/{}/complete): 매니저={} 방문일={} 상태={}", b.getId(), b.getManagerId(), b.getServiceDate(), b.getStatus().db());
        }
        return count;
    }
}
