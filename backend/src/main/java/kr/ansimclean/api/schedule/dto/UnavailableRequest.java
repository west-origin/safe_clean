package kr.ansimclean.api.schedule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** POST /manager/availability/unavailable. repeat = none | weekly | monthly (앱이 '반복 없음'·'매주'·'매월' 을 바꿔 보낸다, §9.0) */
// 매니저 앱 addUnavailable 이 보내는 본문. 컨트롤러의 @Valid 가 아래 검사를 돌리고 틀리면 400.
// message 를 적은 검사는 그 문구가, 나머지(@NotBlank·@Size)는 검사기의 기본 문구가 응답에 나간다
// (기본 문구에 한글이 없으면 '입력값을 확인해주세요' 로 바꾼다 — GlobalExceptionHandler).
// date 의 @Pattern 은 숫자 모양만 본다 — 없는 날짜(13월 등)는 AvailabilityService.addUnavailable 이 거른다.
// allDay 가 true 면 start/end("HH:mm")는 보지 않는다. reason 은 자유 입력 사유(최대 50자, 없어도 된다)
public record UnavailableRequest(@NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "date 는 YYYY-MM-DD 형식이에요") String date,
                                 boolean allDay, String start, String end,
                                 @Pattern(regexp = "none|weekly|monthly", message = "repeat 는 none, weekly, monthly 중 하나예요") String repeat,
                                 @Size(max = 50) String reason) {
    // record 의 간단 생성자: 값이 필드에 들어가기 전에 먼저 돈다. repeat 을 보내지 않으면 1회성(none)으로 본다
    public UnavailableRequest {
        if (repeat == null) repeat = "none";
    }
}
