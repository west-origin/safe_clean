package kr.ansimclean.api.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// 예약 일정 변경 요청 본문: 새 방문 날짜 date("YYYY-MM-DD")와 시각 time("HH:mm").
// BookingController.reschedule 이 받아 BookingService.reschedule 로 넘긴다 — 기한·상태·예약 가능 시간 검사는 그쪽에서 한다.
// @NotBlank(빈 값 금지)·@Pattern(정규식 형식)을 어기면 400 VALIDATION 과 message 문구.
/** PATCH /customer/reservations/{id}/schedule */
public record ScheduleChangeRequest(@NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "date 는 YYYY-MM-DD 형식이에요") String date,
                                    @NotBlank @Pattern(regexp = "\\d{2}:\\d{2}", message = "time 은 HH:mm 형식이에요") String time) {}
