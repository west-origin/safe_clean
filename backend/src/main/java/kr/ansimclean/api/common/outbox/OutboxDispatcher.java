package kr.ansimclean.api.common.outbox;

import kr.ansimclean.api.common.time.AppClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 보낼 차례가 된 이벤트를 핸들러에 넘긴다 (5초마다 + 이벤트가 커밋되면 즉시). 성공 → sent, 실패 → 백오프 후 재시도, 6회 실패 → failed.
 * 핸들러가 없는 타입은 실패로 남겨 두고 로그로 드러낸다(조용히 버리지 않는다).
 * 흐름: 업무 코드가 OutboxService.publish 로 outbox_events 에 행을 적는다 → 커밋되면 onQueued 가 dispatch 를 곧바로 예약하고,
 * 그 사이 놓친 것은 5초 주기 dispatch 가 줍는다 → 이벤트 type 에 맞는 OutboxHandler 를 부른다 (예: PushHandler).
 * 재시도 간격(지수 백오프)과 횟수 한도는 OutboxEvent.markFailed·MAX_ATTEMPTS 가 정한다.
 * 핸들러는 멱등이어야 한다 — 핸들러가 성공한 뒤 커밋이 실패하면 sent 가 남지 않아 같은 이벤트가 다시 온다.
 */
@Component
public class OutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
    private static final Limit BATCH = Limit.of(50); // 한 번에 고르는 최대 건수. 남은 것은 다음 주기나 다음 깨우기 때 처리된다

    private final OutboxEventRepository repository;
    private final Map<String, OutboxHandler> handlers; // 이벤트 type → 그 타입을 맡은 핸들러 (생성자에서 한 번 만든다)
    private final AppClock clock;
    // 이벤트마다 트랜잭션을 따로 열려고 쓴다 — 같은 클래스 안 호출이라 @Transactional 로는 안 된다 (CODE_GUIDE.md 3-3)
    private final TransactionTemplate tx;
    private final TaskScheduler scheduler; // 커밋 직후 dispatch 를 다른 스레드에서 돌린다. 빈이 없으면 null (test 프로필)
    private final AtomicBoolean wakePending = new AtomicBoolean(); // 한 배치 안에서 여러 publish → 깨우기 한 번
    private final AtomicBoolean running = new AtomicBoolean();     // 5초 주기와 즉시 발송이 겹쳐도 배치는 하나만 (같은 이벤트를 두 스레드가 다투지 않게)
    private final AtomicBoolean runAgain = new AtomicBoolean();    // 실행 중 들어온 깨우기는 끝난 뒤 한 번 더

    /** 스케줄러 없이 만든다 — 단위 테스트(OutboxDispatcherTest)가 쓴다. 즉시 발송(onQueued)은 꺼지고 테스트가 dispatchBatch 를 직접 부른다 */
    public OutboxDispatcher(OutboxEventRepository repository, List<OutboxHandler> handlers, AppClock clock, TransactionTemplate tx) {
        this(repository, handlers, clock, tx, null);
    }

    /**
     * 스프링이 쓰는 생성자 (@Autowired: 생성자가 둘이라 이쪽을 쓰라고 알려 준다).
     * handlers: OutboxHandler 를 구현한 빈 전부를 스프링이 목록으로 넣어 준다. type → 핸들러 표로 바꾸고, 한 타입을 둘이 맡으면 기동을 멈춘다.
     * ObjectProvider: TaskScheduler 빈이 없어도(test 프로필은 SchedulingConfig 가 꺼진다) 주입이 실패하지 않게 '있으면 달라' 로 받는다.
     */
    @Autowired
    public OutboxDispatcher(OutboxEventRepository repository, List<OutboxHandler> handlers, AppClock clock, TransactionTemplate tx,
                            ObjectProvider<TaskScheduler> scheduler) {
        this.scheduler = scheduler == null ? null : scheduler.getIfAvailable();
        this.repository = repository;
        Map<String, OutboxHandler> byType = new HashMap<>();
        for (OutboxHandler h : handlers) {
            for (String type : h.types()) {
                // put 이 이전 값을 돌려주면 이미 다른 핸들러가 맡은 타입이다
                if (byType.put(type, h) != null) throw new IllegalStateException("아웃박스 핸들러가 겹칩니다: " + type);
            }
        }
        this.handlers = Map.copyOf(byType); // 바꿀 수 없는 복사본
        this.clock = clock;
        this.tx = tx;
    }

    /** 업무 트랜잭션이 커밋된 직후 — 요청 스레드를 붙잡지 않도록 스케줄러 스레드로 넘긴다. 스케줄러가 없으면(테스트) 5초 주기에 맡긴다 */
    // @TransactionalEventListener: OutboxQueued 를 올린 트랜잭션이 커밋된 뒤에만 불린다 (롤백되면 불리지 않는다)
    @TransactionalEventListener
    public void onQueued(OutboxQueued ignored) {
        // compareAndSet(false, true) 가 실패하면 이미 깨우기가 예약돼 있다 → 또 예약하지 않는다
        if (scheduler == null || !wakePending.compareAndSet(false, true)) return;
        scheduler.schedule(() -> {
            wakePending.set(false); // 돌기 전에 내린다 — 도는 동안 새로 커밋된 이벤트가 다시 깨울 수 있게
            dispatch();
        }, clock.now()); // 실행 시각 = 지금 → 바로 돈다
    }

    /**
     * 보낼 차례가 된 이벤트를 처리한다. 5초 주기(CODE_GUIDE.md 3-9, test 프로필은 꺼짐) + onQueued 의 즉시 깨우기가 부른다.
     * 이미 다른 스레드가 돌고 있으면 기다리지 않고 runAgain 표시만 남긴다 → 돌던 쪽이 끝난 뒤 한 번 더 돈다.
     */
    @Scheduled(fixedDelay = 5000)
    @SchedulerLock(name = "OutboxDispatcher", lockAtLeastFor = "PT4S")
    public void dispatch() {
        if (!running.compareAndSet(false, true)) {
            runAgain.set(true);
            return;
        }
        try {
            // runAgain 을 내리고 배치를 돈다 → 도는 동안 누가 깨웠으면(runAgain=true) 한 번 더
            do {
                runAgain.set(false);
                try {
                    dispatchBatch();
                } catch (Exception e) {
                    log.error("아웃박스 처리 실패", e); // 다음 주기에 다시 시도한다
                }
            } while (runAgain.getAndSet(false));
        } finally {
            running.set(false);
        }
    }

    /**
     * 보낼 id 를 짧은 트랜잭션으로 고르고, **이벤트마다 별도 트랜잭션**(잠금 → 핸들러 → 결과 기록)으로 처리한다 (감사 M-08).
     * 한 트랜잭션에 50건을 묶으면 외부 호출(푸시·LiveKit·Storage)이 느릴 때 DB 연결·행 잠금을 배치 내내 쥐었고,
     * 한 핸들러가 트랜잭션을 rollback-only 로 만들면 이미 보낸 건의 sent 기록까지 사라져 다시 발송됐다.
     * 트랜잭션은 TransactionTemplate 으로 연다(같은 클래스 안의 @Transactional 은 프록시를 거치지 않는다).
     * 반환값: 이번에 보낸(sent) 건수. 테스트(예: OutboxIT)가 스케줄러 대신 이 메서드를 직접 부른다.
     */
    public int dispatchBatch() {
        List<UUID> ids = tx.execute(status -> repository.findDueIds(clock.now(), BATCH));
        int done = 0;
        // tx.execute 는 null 을 돌려줄 수 있다고 선언돼 있어 빈 목록으로 바꿔 돈다
        for (UUID id : ids == null ? List.<UUID>of() : ids) if (dispatchOne(id)) done++;
        return done;
    }

    /** 한 건. 핸들러가 실패하면 그 트랜잭션(핸들러의 DB 변경 포함)은 되돌리고, 실패 기록만 새 트랜잭션에 남긴다 */
    // 반환값: 보냈으면 true. 잠그지 못했거나 핸들러가 없거나 실패했으면 false
    boolean dispatchOne(UUID id) {
        try {
            return Boolean.TRUE.equals(tx.execute(status -> {
                // 고를 때는 잠그지 않았으므로 여기서 행을 잠그며 다시 읽는다 (잠금은 이 트랜잭션이 끝날 때 풀린다)
                OutboxEvent event = repository.lockDue(id, clock.now()).orElse(null);
                if (event == null) return false; // 다른 스레드·인스턴스가 쥐었거나 이미 처리됨
                OutboxHandler handler = handlers.get(event.getType());
                if (handler == null) {
                    // 시도 1회로 센다 — 백오프로 몇 번 더 돌다가 MAX_ATTEMPTS 에서 failed 로 멈춘다
                    event.markFailed("핸들러 없음: " + event.getType(), clock.now());
                    log.error("아웃박스 이벤트를 처리할 핸들러가 없습니다: {}", event.getType());
                    return false;
                }
                // 핸들러가 던지면 아래 catch 로 간다 — 이 트랜잭션(핸들러의 DB 변경 포함)은 통째로 되돌려진다
                handler.handle(event.getType(), event.getPayload());
                event.markSent(clock.now());
                return true;
            }));
        } catch (Exception e) {
            // 새 트랜잭션에서 다시 잠그고 실패 횟수·다음 시도 시각만 적는다. 그 사이 다른 쪽이 쥐었으면 기록하지 않는다
            tx.executeWithoutResult(status -> repository.lockDue(id, clock.now()).ifPresent(event -> {
                event.markFailed(e.toString(), clock.now());
                log.warn("아웃박스 이벤트 실패 {} (시도 {}회): {}", event.getType(), event.getAttempts(), e.toString());
            }));
            return false;
        }
    }
}
