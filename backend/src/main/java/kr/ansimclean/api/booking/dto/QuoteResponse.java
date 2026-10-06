package kr.ansimclean.api.booking.dto;

import java.util.List;

// 견적 응답 (POST /customer/bookings/quote). BookingService.quote 가 만들고, 고객 앱 예약 확인 화면의 "요금" 행이 쓴다.
// perVisit = 1회 금액 내역, visits = 방문 날짜 목록(정기면 회차 전부, 1회면 하루), visitsPlanned = 방문 횟수,
// totalAmount·totalLabel = 1회 금액 × 방문 횟수(원), paymentNote = 결제 안내 문구(app_settings 에서 읽는다).
/** 견적 (docs/api.md §5.2 getBookingQuote). label 들은 KoreanFormat.won */
/** savingNote = 정기청소가 1회 청소보다 싼 만큼 (없으면 null) — 화면이 할인을 말해 주지 못했다 (사용성 검토 2026-09-22) */
public record QuoteResponse(PerVisit perVisit, List<Visit> visits, int visitsPlanned, long totalAmount, String totalLabel, String paymentNote, String savingNote) {
    /** baseLabel·optionsLabel 은 요금 내역을 줄로 보여 주기 위한 것 (옵션이 없으면 optionsLabel = null) */
    // base = 1회 기본요금, options = 유료 옵션 합, total = base + options, label = total 을 "85,000원" 모양으로
    public record PerVisit(long base, long options, long total, String label, String baseLabel, String optionsLabel) {}

    /** date = "YYYY-MM-DD", label = "2026년 11월 1일 (일)" */
    public record Visit(String date, String label) {}
}
