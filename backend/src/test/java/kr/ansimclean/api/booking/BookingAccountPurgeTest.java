package kr.ansimclean.api.booking;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 스프링 없이 BookingAccountPurge 를 new 로 만들고 저장소는 Mockito mock 으로 둔다 — 어떤 쿼리 메서드를 불렀는지만 본다
/** 탈퇴 + 5년: 고객 예약의 개인정보 칸만 비운다 (행은 남긴다). SQL 은 DeletedAccountPurgeIT */
class BookingAccountPurgeTest {
    BookingRepository bookings = mock(BookingRepository.class);
    BookingAccountPurge purge = new BookingAccountPurge(bookings);

    @Test
    void 고객이면_이력_사유_예약_정기청소를_비우고_매니저는_아무것도_하지_않는다() {
        UUID customer = UUID.randomUUID();
        purge.purge(customer, "customer");
        // 세 쿼리가 고객 id 로 불렸는지만 본다 (InOrder 가 아니라 호출 순서는 확인하지 않는다)
        verify(bookings).scrubCustomerHistory(customer); // 이력은 예약의 고객 id 로 찾는다 — 예약 비우기는 customer_id 를 그대로 두어 순서가 결과를 바꾸지 않는다
        verify(bookings).scrubCustomerBookings(customer);
        verify(bookings).scrubCustomerSeries(customer);

        purge.purge(UUID.randomUUID(), "manager");
        // argThat(조건) + never(): 조건에 맞는 인자(위 고객이 아닌 id)로는 한 번도 불리지 않았다 — 매니저 id 로 비우지 않았다는 뜻.
        // delete 도 없어야 한다 (예약 행은 남긴다)
        verify(bookings, never()).scrubCustomerBookings(org.mockito.ArgumentMatchers.argThat(id -> !id.equals(customer)));
        verify(bookings, never()).delete(any());
    }
}
