package kr.ansimclean.api.schedule.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** PUT /manager/availability/hours 의 한 줄. weekday 0=월 … 6=일 */
// 매니저 앱 updateDefaultHours 가 화면 행을 이 모양으로 바꿔 보낸다. start/end 는 "HH:mm"(휴무면 null).
// @Min/@Max = 요일 번호는 0~6 만(아니면 400). 시각 형식·시작 < 종료 검사는 AvailabilityService.updateHours 가 한다
public record HoursRequest(@Min(0) @Max(6) int weekday, String start, String end, boolean off) {}
