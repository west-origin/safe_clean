package kr.ansimclean.api.booking.dto;

import jakarta.validation.constraints.Size;

// 고객의 예약 취소 요청 본문. BookingController.cancel 이 받는다 — 본문이 없으면 (사유 없음, 이번 회차만)으로 본다.
// wholeSeries 가 true 면 BookingSeriesService.cancelWhole, 아니면 BookingService.cancel 로 간다.
// reason = 고객이 고른·적은 취소 사유(200자까지, 넘으면 400 VALIDATION). 지금 고객 앱(cancelReservation)은 wholeSeries 만 보낸다.
// 한 회차 취소에서 비었거나 시스템 사유 값(unassigned·not_started·series_cancelled)이면 서버가 "customer" 로 적는다.
// 정기청소 전체 취소는 이 값을 쓰지 않고 회차마다 "series_cancelled" 를 적는다 — 단 1회 예약이면 wholeSeries 여도 한 회차 취소로 넘어가 reason 을 쓴다.
/** POST /customer/reservations/{id}/cancel. wholeSeries = 정기청소 전체 취소 (§6.8) */
public record CancelRequest(@Size(max = 200) String reason, Boolean wholeSeries) {
    // 압축 생성자: JSON 에서 wholeSeries 가 빠지면(null) false 로 채운다
    public CancelRequest {
        if (wholeSeries == null) wholeSeries = false;
    }
}
