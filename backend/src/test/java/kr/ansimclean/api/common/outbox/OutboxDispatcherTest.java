package kr.ansimclean.api.common.outbox;

import kr.ansimclean.api.common.time.AppClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 아웃박스 발송 (BACKEND_PLAN.md §2.4): 성공 → sent, 실패 → 백오프, 핸들러 없음 → 실패 기록 */
// OutboxDispatcher 단위 테스트. 저장소·트랜잭션 관리자는 Mockito 목(가짜 객체)이라 DB 없이, 트랜잭션을 몇 번 열고
// 커밋·롤백했는지(verify)와 이벤트 엔티티의 상태 변화만 본다. 실제 DB 로 도는 흐름은 OutboxIT 가 본다.
class OutboxDispatcherTest {
    static final Instant NOW = Instant.parse("2026-09-15T05:00:00Z");
    static final AppClock CLOCK = new AppClock(Clock.fixed(NOW, AppClock.ZONE)); // 고정 시계 — 백오프 시각을 정확히 비교할 수 있다

    OutboxEventRepository repository = mock(OutboxEventRepository.class);
    PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    List<String> handled = new java.util.ArrayList<>(); // 성공한 핸들러가 받은 타입을 순서대로 적는 기록장
    OutboxHandler ok = handler("OkEvent", false);         // 항상 성공하는 가짜 핸들러
    OutboxHandler broken = handler("BrokenEvent", true);  // 항상 예외를 던지는 가짜 핸들러
    OutboxDispatcher dispatcher; // 스케줄러 없이 만든 디스패처 (즉시 발송 꺼짐)

    @BeforeEach
    void setUp() {
        dispatcher = new OutboxDispatcher(repository, List.of(ok, broken), CLOCK, new TransactionTemplate(txManager));
    }

    @Test
    void 이벤트가_커밋되면_스케줄러로_바로_한_번_깨운다() {
        // 5초 주기를 기다리지 않는다. 같은 트랜잭션에서 publish 가 여러 번이어도 깨우기는 한 번.
        // 스프링이 주는 '있으면 달라' 상자(ObjectProvider)를 목으로 만들어, 가짜 스케줄러가 들어 있는 것처럼 한다
        org.springframework.scheduling.TaskScheduler scheduler = mock(org.springframework.scheduling.TaskScheduler.class);
        org.springframework.beans.factory.ObjectProvider<org.springframework.scheduling.TaskScheduler> provider =
                mock(org.springframework.beans.factory.ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(scheduler);
        OutboxDispatcher woken = new OutboxDispatcher(repository, List.of(ok), CLOCK, new TransactionTemplate(txManager), provider);
        when(repository.findDueIds(any(), any())).thenReturn(List.of());

        // 커밋 알림 두 번 → 스케줄러에는 한 번만 예약된다
        woken.onQueued(new OutboxQueued());
        woken.onQueued(new OutboxQueued());

        // 가짜 스케줄러는 실제로 돌리지 않으므로, 예약된 작업(Runnable)을 잡아 직접 run() 해서 dispatch 가 도는지 본다
        org.mockito.ArgumentCaptor<Runnable> task = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, org.mockito.Mockito.times(1)).schedule(task.capture(), any(Instant.class));
        task.getValue().run();
        verify(repository).findDueIds(any(), any());
        woken.onQueued(new OutboxQueued()); // 돌고 난 뒤에는 다시 깨울 수 있다
        verify(scheduler, org.mockito.Mockito.times(2)).schedule(any(Runnable.class), any(Instant.class));
        dispatcher.onQueued(new OutboxQueued()); // 스케줄러 없이 만든 디스패처(테스트)는 무시
    }

    @Test
    void 배치가_도는_중에_들어온_깨우기는_끝난_뒤_한_번_더_돈다() {
        // 5초 주기와 즉시 발송이 겹쳐도 배치는 한 번에 하나 — 한 번에 하나만, 겹친 호출은 이어서 한 번
        java.util.concurrent.atomic.AtomicInteger batches = new java.util.concurrent.atomic.AtomicInteger(); // 배치(id 고르기)가 몇 번 돌았나
        // thenAnswer: 불릴 때마다 이 코드를 돌려 답을 만든다. 첫 배치 도중에 dispatch 를 한 번 더 불러 겹친 호출을 만든다
        when(repository.findDueIds(any(), any())).thenAnswer(inv -> {
            if (batches.incrementAndGet() == 1) dispatcher.dispatch(); // 첫 배치 중에 다시 호출됨(다른 스레드의 깨우기를 흉내)
            return List.of();
        });

        dispatcher.dispatch();

        assertThat(batches.get()).isEqualTo(2); // 겹친 호출은 즉시 돌지 않고 첫 배치 뒤에 한 번
        verify(txManager, org.mockito.Mockito.times(2)).commit(any()); // 배치마다 id 고르기 트랜잭션 하나씩 (보낼 이벤트는 없다)
    }

    @Test
    void dispatch_는_트랜잭션_안에서_배치를_처리한다() {
        // 트랜잭션 없이 돌면 lockDue 가 돌려준 행이 분리(detached)돼 markSent 가 저장되지 않고 같은 이벤트가 5초마다 다시 나간다
        when(repository.findDueIds(any(), any())).thenReturn(List.of());

        dispatcher.dispatch();

        verify(txManager).getTransaction(any());
        verify(txManager).commit(any());
        verify(repository).findDueIds(any(), any());
    }

    // 성공·실패·핸들러 없는 이벤트를 한 배치로 돌린다. 잠금(lockDue)은 목이 매번 같은 엔티티 객체를 돌려주므로
    // 디스패처가 그 객체에 적은 상태(sent·시도 횟수·다음 시도 시각·오류 문구)를 바로 읽어 확인할 수 있다
    @Test
    void 성공하면_sent_실패하면_백오프_핸들러가_없으면_failed_기록() {
        OutboxEvent okEvent = OutboxEvent.pending("OkEvent", "{}", NOW);
        OutboxEvent brokenEvent = OutboxEvent.pending("BrokenEvent", "{}", NOW);
        OutboxEvent orphan = OutboxEvent.pending("NoHandlerEvent", "{}", NOW);
        when(repository.findDueIds(any(), any())).thenReturn(List.of(okEvent.getId(), brokenEvent.getId(), orphan.getId()));
        for (OutboxEvent e : List.of(okEvent, brokenEvent, orphan)) when(repository.lockDue(eq(e.getId()), any())).thenReturn(java.util.Optional.of(e));

        dispatcher.dispatchBatch();

        // 실패 1회 → 2분 뒤 다시. 핸들러 없는 이벤트도 실패 1회로 세고 오류 문구를 남긴다 (MAX_ATTEMPTS 에서 failed 로 멈춘다)
        assertThat(handled).containsExactly("OkEvent");
        assertThat(okEvent.getStatus()).isEqualTo("sent");
        assertThat(brokenEvent.getStatus()).isEqualTo("pending");
        assertThat(brokenEvent.getAttempts()).isEqualTo(1);
        assertThat(brokenEvent.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(2)));
        assertThat(orphan.getAttempts()).isEqualTo(1);
        assertThat(orphan.getLastError()).contains("핸들러 없음");
    }

    // 생성자가 type → 핸들러 표를 만들 때 같은 타입이 두 번 나오면 IllegalStateException (스프링 기동이 멈춘다)
    @Test
    void 같은_타입을_두_핸들러가_받으면_기동을_거부한다() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new OutboxDispatcher(repository, List.of(ok, handler("OkEvent", false)), CLOCK, new TransactionTemplate(txManager)))
                .isInstanceOf(IllegalStateException.class);
    }

    /** type 하나를 맡는 가짜 핸들러. fails 면 항상 예외, 아니면 받은 타입을 handled 에 적는다 */
    private OutboxHandler handler(String type, boolean fails) {
        // new 인터페이스() { … }: 이름 없는 클래스(익명 클래스)로 그 자리에서 인터페이스를 구현한다
        return new OutboxHandler() {
            @Override
            public Set<String> types() { return Set.of(type); }

            @Override
            public void handle(String t, String payload) {
                if (fails) throw new IllegalStateException("boom");
                handled.add(t);
            }
        };
    }

    @Test
    void 이벤트마다_따로_트랜잭션이라_한_건이_실패해도_앞서_보낸_건의_sent_는_남고_실패_기록은_새_트랜잭션에() { // 감사 M-08
        OutboxEvent okEvent = OutboxEvent.pending("OkEvent", "{}", NOW);
        OutboxEvent brokenEvent = OutboxEvent.pending("BrokenEvent", "{}", NOW);
        when(repository.findDueIds(any(), any())).thenReturn(List.of(okEvent.getId(), brokenEvent.getId()));
        when(repository.lockDue(eq(okEvent.getId()), any())).thenReturn(java.util.Optional.of(okEvent));
        when(repository.lockDue(eq(brokenEvent.getId()), any())).thenReturn(java.util.Optional.of(brokenEvent));

        assertThat(dispatcher.dispatchBatch()).isEqualTo(1);

        // id 고르기 1 + ok 1 + broken(롤백) 1 + 실패 기록 1
        verify(txManager, org.mockito.Mockito.times(4)).getTransaction(any());
        verify(txManager).rollback(any());
        assertThat(okEvent.getStatus()).isEqualTo("sent");
        assertThat(brokenEvent.getAttempts()).isEqualTo(1);
    }

    // lockDue 가 빈 값 = 다른 스레드·서버가 이미 잠갔거나 처리했다 → 핸들러를 부르지 않고 보낸 수 0
    @Test
    void 다른_곳이_쥔_이벤트는_건너뛴다() {
        OutboxEvent okEvent = OutboxEvent.pending("OkEvent", "{}", NOW);
        when(repository.findDueIds(any(), any())).thenReturn(List.of(okEvent.getId()));
        when(repository.lockDue(eq(okEvent.getId()), any())).thenReturn(java.util.Optional.empty()); // SKIP LOCKED
        assertThat(dispatcher.dispatchBatch()).isZero();
        assertThat(handled).isEmpty();
    }
}
