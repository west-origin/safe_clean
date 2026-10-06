package kr.ansimclean.api.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

// booking_status_history(예약 상태 이력) 테이블. 쌓기만 하고(save, 예: BookingService.record) 고치지 않는다.
// 탈퇴 때 고객 사유를 비우는 UPDATE 는 여기가 아니라 BookingRepository.scrubCustomerHistory 에 있다.
public interface BookingStatusHistoryRepository extends JpaRepository<BookingStatusHistory, Long> {
    // 한 예약의 이력을 시각 순으로. 지금 main·test 코드에는 이 메서드를 부르는 곳이 없다(이력을 보여 주는 화면·API 가 없다)
    List<BookingStatusHistory> findByBookingIdOrderByAt(UUID bookingId);
}
