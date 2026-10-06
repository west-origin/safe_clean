package kr.ansimclean.api.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;

// 견적 요청 본문. 고객 앱 예약 확인 화면(BookingConfirm)이 api/customer.js 의 getBookingQuote 로 보낸다
// (예약 생성과 같은 bookingBody 에서 category·frequency·date·time·옵션 키만). BookingController.quote → BookingService.quote.
// frequency = "recurring"(정기) 또는 "oneTime"(1회), date = "YYYY-MM-DD", time = "HH:mm". 형식이 틀리면 400 VALIDATION.
// 압축 생성자가 빠진 frequency 는 정기로, 빠진 옵션 목록은 빈 목록으로 채운다.
/** POST /customer/bookings/quote — 확인 화면 진입 시. 저장 없음 */
public record QuoteRequest(@NotBlank String category,
                           @Pattern(regexp = "recurring|oneTime", message = "frequency 는 recurring 또는 oneTime 이에요") String frequency,
                           @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "date 는 YYYY-MM-DD 형식이에요") String date,
                           @NotBlank @Pattern(regexp = "\\d{2}:\\d{2}", message = "time 은 HH:mm 형식이에요") String time,
                           List<String> freeOptions,
                           List<String> paidOptions) {
    public QuoteRequest {
        if (frequency == null) frequency = "recurring";
        if (freeOptions == null) freeOptions = List.of();
        if (paidOptions == null) paidOptions = List.of();
    }
}
