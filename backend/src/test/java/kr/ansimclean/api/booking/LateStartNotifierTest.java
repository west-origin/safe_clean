package kr.ansimclean.api.booking;

import kr.ansimclean.api.common.events.ManagerLate;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.profile.ManagerPenaltyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 약속 시간 + 30분이 지나도 시작하지 않은 배정 예약 → 고객에게 한 번 (docs/manager-policy.md §2, 자체 점검 #25) */
// LateStartNotifier 단위 테스트. 저장소·아웃박스·패널티 설정은 Mockito 가짜(mock), 예약 엔티티는 진짜(TestBookings), 시계는 멈춰 있다.
// '보냈다' 는 아웃박스에 ManagerLate 가 기록됐는지(verify(outbox).publish)로 본다 — 실제 푸시는 이 클래스 밖이다.
class LateStartNotifierTest {
    static final Instant NOW = Instant.parse("2026-09-16T01:31:00Z"); // 9/16 10:31 서울
    static final UUID CUSTOMER = UUID.randomUUID();
    static final UUID MANAGER = UUID.randomUUID();

    BookingRepository bookings = mock(BookingRepository.class);
    OutboxService outbox = mock(OutboxService.class);
    ManagerPenaltyService penalties = mock(ManagerPenaltyService.class);
    LateStartNotifier notifier;

    /** 테스트마다 새 알림기. 지각 기준은 30분으로 정한다 */
    @BeforeEach
    void setUp() {
        // TransactionTemplate 에 가짜 트랜잭션 관리자를 넣어 run() 이 DB 없이 돈다 (여는·커밋·되돌리기가 아무 일도 하지 않는다)
        notifier = new LateStartNotifier(bookings, outbox, new AppClock(Clock.fixed(NOW, AppClock.ZONE)), new TransactionTemplate(mock(PlatformTransactionManager.class)), penalties);
        when(penalties.tardyMinutes()).thenReturn(30);
    }

    /** 잠금 조회(findForUpdate)가 이 예약을 돌려주게 연결하고 그 예약을 그대로 돌려준다 */
    Booking stub(Booking b) {
        when(bookings.findForUpdate(b.getId())).thenReturn(Optional.of(b));
        return b;
    }

    // 10:00 약속, 지금 10:31 — 후보 조회가 이 예약을 돌려주면 알림 1건 + 알린 시각 표시.
    // 조회가 같은 id 를 또 돌려줘도(두 번째 notifyDue) 표시가 있어 다시 보내지 않는다
    @Test
    void 약속_시간에서_30분이_지나도_시작하지_않았으면_고객에게_한_번_알린다() {
        Booking late = stub(TestBookings.scheduled(CUSTOMER, MANAGER, LocalDate.of(2026, 9, 16), LocalTime.of(10, 0)));
        when(bookings.findLateUnstartedIds(any(), any())).thenReturn(List.of(late.getId()));

        assertThat(notifier.notifyDue()).isEqualTo(1);
        assertThat(late.getLateNotifiedAt()).isEqualTo(NOW);
        verify(outbox).publish(new ManagerLate(late.getId(), CUSTOMER, MANAGER, 30));

        assertThat(notifier.notifyDue()).isZero(); // 조회가 늦게 반영돼 다시 잡혀도 두 번 보내지 않는다
        verify(outbox, times(1)).publish(any());
    }

    // 후보 조회는 두 id 를 돌려주지만, 잠그고 다시 본 예약이 하나는 이미 시작(in_progress), 하나는 26분만 지났다 → 0건
    @Test
    void 조회와_잠금_사이에_매니저가_시작했거나_아직_30분이_안_됐으면_보내지_않는다() {
        Booking started = stub(TestBookings.inProgress(CUSTOMER, MANAGER, LocalDate.of(2026, 9, 16), LocalTime.of(10, 0), NOW.minusSeconds(60)));
        Booking early = stub(TestBookings.scheduled(CUSTOMER, MANAGER, LocalDate.of(2026, 9, 16), LocalTime.of(10, 5))); // 26분 지났다
        when(bookings.findLateUnstartedIds(any(), any())).thenReturn(List.of(started.getId(), early.getId()));

        assertThat(notifier.notifyDue()).isZero();
        verify(outbox, never()).publish(any());
    }

    // 조회가 예외를 던지게 해도 run() 은 로그만 남기고 끝나야 한다 (예외가 새어 나오면 이 테스트가 실패한다)
    @Test
    void run_은_실패해도_던지지_않고_다음_주기에_다시_한다() {
        when(bookings.findLateUnstartedIds(any(), any())).thenThrow(new IllegalStateException("db down"));
        notifier.run();
        verify(outbox, never()).publish(any());
    }
}
