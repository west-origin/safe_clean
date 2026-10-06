package kr.ansimclean.api.booking;

import kr.ansimclean.api.common.events.BookingCancelled;
import kr.ansimclean.api.common.events.BookingUnassigned;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.time.AppClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 매일 03:10 지난 예약 정리 (BACKEND_PLAN.md §2.5): 미배정 → cancelled(unassigned)+푸시, 미방문 → cancelled(not_started)+고객·매니저 푸시·패널티+로그 */
// StaleBookingCleaner 단위 테스트. 레포지토리·아웃박스·디스패처·BookingService·패널티·트랜잭션 관리자는 Mockito 목(가짜 객체)이고,
// 예약은 TestBookings 로 만든 진짜 엔티티라 취소 뒤의 상태·사유를 직접 확인한다. 시계는 서울 9/16 03:10 으로 고정한다.
class StaleBookingCleanerTest {
    static final Instant NOW = Instant.parse("2026-09-15T18:10:00Z"); // 수 03:10 서울 → today = 9/16
    static final AppClock CLOCK = new AppClock(Clock.fixed(NOW, AppClock.ZONE));
    static final UUID CUSTOMER = UUID.randomUUID();
    static final UUID MANAGER = UUID.randomUUID();

    BookingRepository bookings = mock(BookingRepository.class);
    BookingStatusHistoryRepository history = mock(BookingStatusHistoryRepository.class);
    OutboxService outbox = mock(OutboxService.class);
    BookingDispatcher dispatcher = mock(BookingDispatcher.class);
    BookingService bookingService = mock(BookingService.class);
    // 트랜잭션 관리자도 목이다 — 진짜 DB 없이, TransactionTemplate 이 트랜잭션을 열고(getTransaction) 커밋(commit)했는지만 본다
    PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    StaleBookingCleaner cleaner;

    kr.ansimclean.api.profile.ManagerPenaltyService penalties = mock(kr.ansimclean.api.profile.ManagerPenaltyService.class);

    // @BeforeEach: 테스트 메서드마다 그 앞에서 한 번 돈다
    @BeforeEach
    void setUp() {
        cleaner = new StaleBookingCleaner(bookings, history, outbox, dispatcher, bookingService, CLOCK, new TransactionTemplate(txManager), penalties);
        // 기본값: 어떤 상태·날짜로 찾아도 빈 목록. 각 테스트가 필요한 조회만 eq(…) 로 다시 정한다(더 뒤에 정한 답이 우선한다)
        when(bookings.findByStatusInAndServiceDateBefore(any(), any())).thenReturn(List.of());
    }

    @Test
    void run_은_트랜잭션_안에서_clean_한다() {
        // 같은 클래스의 clean() 을 직접 부르면 @Transactional 이 붙지 않아 취소가 저장되지 않는다
        // (이력만 매일 쌓이고, 트랜잭션이 꼭 있어야 하는 outbox.publish 에서 예외가 나 멈춘다)
        cleaner.run();

        // 트랜잭션을 열고 커밋했으며, 그 안에서 '오늘(9/16) 이전 방문일의 미배정 예약' 조회가 돌았다
        verify(txManager).getTransaction(any());
        verify(txManager).commit(any());
        verify(bookings).findByStatusInAndServiceDateBefore(eq(List.of(BookingStatus.REQUESTED, BookingStatus.ASSIGNING)), eq(LocalDate.of(2026, 9, 16)));
    }

    @Test
    void 방문일이_지난_미배정_예약은_unassigned_로_취소하고_고객에게_알린다() {
        // 방문일 9/15(어제)인데 아직 매칭 중인 예약 → 취소·이력 저장·남은 오퍼 정리·고객 푸시 이벤트
        Booking b = TestBookings.assigning(CUSTOMER, LocalDate.of(2026, 9, 15), LocalTime.of(10, 0));
        when(bookings.findByStatusInAndServiceDateBefore(eq(List.of(BookingStatus.REQUESTED, BookingStatus.ASSIGNING)), eq(LocalDate.of(2026, 9, 16))))
                .thenReturn(List.of(b));

        int n = cleaner.clean();

        assertThat(n).isEqualTo(1);
        assertThat(b.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(b.getCancelReason()).isEqualTo(StaleBookingCleaner.REASON_UNASSIGNED);
        verify(history).save(any(BookingStatusHistory.class));
        verify(dispatcher).onCancelled(b); // 남은 pending 오퍼 정리
        verify(outbox).publish(new BookingUnassigned(b.getId(), CUSTOMER));
    }

    @Test
    void 배정됐는데_하루가_지나도_시작하지_않은_예약은_not_started_로_취소한다() {
        // 미방문 조회는 '오늘 - 1일'(9/15)보다 앞 방문일을 찾는다 → 9/14 에 배정된 채 시작하지 않은 예약이 걸린다
        Booking b = TestBookings.scheduled(CUSTOMER, MANAGER, LocalDate.of(2026, 9, 14), LocalTime.of(10, 0));
        when(bookings.findByStatusInAndServiceDateBefore(eq(List.of(BookingStatus.SCHEDULED)), eq(LocalDate.of(2026, 9, 15))))
                .thenReturn(List.of(b));

        int n = cleaner.clean();

        assertThat(n).isEqualTo(1);
        assertThat(b.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(b.getCancelReason()).isEqualTo(StaleBookingCleaner.REASON_NOT_STARTED);
        verify(history).save(any(BookingStatusHistory.class));
        verify(outbox).publish(new BookingCancelled(b.getId(), CUSTOMER, MANAGER, StaleBookingCleaner.REASON_NOT_STARTED)); // 고객·매니저 푸시 (검수 4차)
        verify(bookingService, never()).endSeriesIfNothingLeft(any(), any()); // 1회 예약
    }

    // 9/14 에 시작한 진행 중(in_progress) 작업을 ACTIVE(진행 중·일시정지) 조회 결과로 준다
    // → 상태는 그대로, 취소 수 0, 푸시 이벤트 없음 (서비스는 오류 로그만 남긴다)
    @Test
    void 청소_완료가_안_눌린_지난_작업은_건드리지_않고_운영자에게만_알린다() { // 자체 점검 #17 — 자동으로 닫지 않는다(정산은 매니저·운영자 확인 뒤)
        Booking b = TestBookings.inProgress(CUSTOMER, MANAGER, LocalDate.of(2026, 9, 14), LocalTime.of(10, 0), java.time.Instant.parse("2026-09-14T01:00:00Z"));
        when(bookings.findByStatusInAndServiceDateBefore(eq(BookingStatus.ACTIVE), eq(LocalDate.of(2026, 9, 15)))).thenReturn(List.of(b));
        assertThat(cleaner.clean()).isZero();
        assertThat(b.getStatus()).isEqualTo(BookingStatus.IN_PROGRESS);
        verify(bookings).findByStatusInAndServiceDateBefore(eq(BookingStatus.ACTIVE), eq(LocalDate.of(2026, 9, 15)));
        verify(outbox, never()).publish(any());
    }

    @Test
    void 미방문으로_취소된_정기_회차는_시리즈가_active_로_남지_않게_닫기를_시도한다() {
        // seriesId 가 있는 정기청소 회차가 미방문으로 취소되면 endSeriesIfNothingLeft(남은 회차가 없으면 시리즈 종료)를 부른다
        Booking b = TestBookings.scheduledSeries(CUSTOMER, MANAGER, UUID.randomUUID(), LocalDate.of(2026, 9, 14), LocalTime.of(10, 0));
        when(bookings.findByStatusInAndServiceDateBefore(eq(List.of(BookingStatus.SCHEDULED)), eq(LocalDate.of(2026, 9, 15))))
                .thenReturn(List.of(b));

        cleaner.clean();

        verify(bookingService).endSeriesIfNothingLeft(b.getSeriesId(), NOW); // 검수 4차: 빠져 있어 회차가 다 사라진 시리즈가 term_end 까지 탈퇴를 막았다
    }
}
