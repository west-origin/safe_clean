package kr.ansimclean.api.booking;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 탈퇴 차단 (§6.8): 고객은 열린 예약·활성 정기청소, 매니저는 남은 작업 */
// BookingDeletionGuard.blockReason 만 확인하는 단위 테스트 (DB·스프링 없이 돈다).
// BookingService 는 Mockito 가짜(mock)다. 따로 정하지 않은 boolean 메서드는 false 를 돌려주므로,
// 아무것도 정하지 않으면 막을 이유가 없다(null).
class BookingDeletionGuardTest {
    static final UUID USER = UUID.randomUUID();
    BookingService bookings = mock(BookingService.class);
    BookingDeletionGuard guard = new BookingDeletionGuard(bookings);

    // 같은 가짜의 답을 바꿔 가며 차례로 본다: 아무것도 없음 → 열린 예약만 → 활성 정기청소만
    @Test
    void 고객은_열린_예약이나_활성_정기청소가_있으면_막힌다() {
        assertThat(guard.blockReason(USER, "customer")).isNull();
        when(bookings.hasOpenBookings(USER)).thenReturn(true);
        assertThat(guard.blockReason(USER, "customer")).isEqualTo(BookingDeletionGuard.CUSTOMER_BLOCKED);
        when(bookings.hasOpenBookings(USER)).thenReturn(false);
        when(bookings.hasActiveSeries(USER)).thenReturn(true);
        assertThat(guard.blockReason(USER, "customer")).isEqualTo(BookingDeletionGuard.CUSTOMER_BLOCKED);
    }

    // 열린 예약과 진행 중 작업이 둘 다 있어도 '끝난 뒤' 문구가 나와야 한다 — 가드는 진행 중 검사를 먼저 한다
    @Test
    void 청소가_진행_중인_예약이_있으면_취소하라는_대신_끝난_뒤_다시_하라고_한다() { // 자체 점검 #17 — 진행 중인 청소는 고객이 취소할 수 없다
        when(bookings.hasOpenBookings(USER)).thenReturn(true);
        when(bookings.hasActiveJobsAsCustomer(USER)).thenReturn(true);
        assertThat(guard.blockReason(USER, "customer")).isEqualTo(BookingDeletionGuard.CUSTOMER_IN_PROGRESS);
    }

    // 역할이 "manager" 면 hasOpenJobs(맡은 채 끝나지 않은 예약) 하나만 본다
    @Test
    void 매니저는_맡은_작업이_남아_있으면_막히고_고객_조건은_보지_않는다() {
        when(bookings.hasOpenBookings(USER)).thenReturn(true); // 매니저 역할이면 무시
        assertThat(guard.blockReason(USER, "manager")).isNull();
        when(bookings.hasOpenJobs(USER)).thenReturn(true);
        assertThat(guard.blockReason(USER, "manager")).isEqualTo(BookingDeletionGuard.MANAGER_BLOCKED);
    }
}
