package kr.ansimclean.api.booking.dto;

import java.util.List;
import java.util.UUID;

/**
 * 예약 1건 (생성·완료 화면). 정기청소면 seriesId 와 visits(회차 전부), 1회면 visits 1개.
 * cleaner = 배정 전 "배정 후 알려드려요" / 배정 후 "김미영 매니저"
 * 매칭 실패로 멈춘 예약이면 cleaner 는 매칭 실패 안내 문구다 (BookingService.cleanerLabel — 예약 목록 카드와 같은 문구).
 * 매니저 없이 취소된 예약을 다시 조회하면 cleaner 는 목록 카드와 같은 "배정 전 취소" (BookingMapper.managerLabel).
 * 돌려주는 API: POST /customer/bookings(생성), GET /customer/bookings/{id}(조회). 값은 BookingMapper.toBooking 이 채운다.
 * 칸: category = 카테고리 키, frequency = "recurring" 또는 "oneTime", date = "YYYY-MM-DD", time = "HH:mm",
 * serviceLabel = 카테고리 표시 이름, managerAssigned = 매니저 배정 여부,
 * matchingFailed = 매칭에 실패해 멈춘 예약인가(bookings.matching_failed_at 이 있다 — 매니저가 수락하면 지워진다). 참이면 cleaner 는 실패 안내(그 뒤 취소됐으면
 * '배정 전 취소')라, 고객 앱 수리 예약 완료 화면이 Figma '배정 중' 대신 cleaner 를 보인다 (문제 목록 8번),
 * cancelled = 취소된 예약인가(status = cancelled — 생성 응답에서는 늘 false). 참이면 cleaner 는 '배정 전 취소'(매니저가 있었으면 "이름 매니저")라,
 * 수리 예약 완료 화면이 매칭 실패 여부와 관계없이 '배정 중'·배정 안내 대신 cleaner 를 보인다 (합친 38건 검토 R16), seriesId = 정기청소 시리즈 id(1회면 null),
 * perVisitLabel = 이 예약 1회 금액("85,000원" 모양), totalAmount·totalLabel = visits 전체 금액의 합(원, 취소된 회차도 visits 에 있으면 더한다).
 * Visit: 회차 한 번 — id = 그 회차 예약 id, label = "2026년 11월 1일 (일)" 모양.
 */
public record BookingResponse(UUID id, String category, String frequency, String date, String time, String roadAddress, String detailAddress,
                              String serviceLabel, String cleaner, boolean managerAssigned, boolean matchingFailed, boolean cancelled, UUID seriesId, List<Visit> visits, String perVisitLabel, long totalAmount, String totalLabel) {
    public record Visit(UUID id, String date, String label) {}
}
