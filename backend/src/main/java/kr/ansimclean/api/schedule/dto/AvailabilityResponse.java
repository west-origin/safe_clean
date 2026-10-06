package kr.ansimclean.api.schedule.dto;

import java.util.List;
import java.util.UUID;

// 매니저 앱 근무 가능 시간 화면(getAvailability)이 받는 응답. AvailabilityService.availability 가 만든다.
// 필드 이름이 그대로 JSON 키가 된다 (defaultHours, unavailable) — 앱 mock.js 의 키와 같아야 한다.
// 안쪽 DayHours 는 PUT /hours 응답(7줄), UnavailableItem 은 POST /unavailable 응답(한 줄)으로도 쓰인다.
/** GET /manager/availability — 앱 AVAILABILITY 모양 */
public record AvailabilityResponse(List<DayHours> defaultHours, List<UnavailableItem> unavailable) {
    /** { day: "월", hours: "09:00 - 18:00" | "휴무", off } */
    /** start/end = "HH:mm"(휴무면 null) — 수정 시트가 hours 문구를 쪼개지 않게 (검증 검토) */
    public record DayHours(String day, String hours, boolean off, String start, String end) {}

    /** { id, date: "9월 18일", label: "개인 일정", time: "14:00 - 18:00" | "하루 종일", repeat } */
    // label 은 사유가 있으면 사유, 없으면 '휴무'(하루 종일)·'개인 일정'. repeat 은 서버 값(none|weekly|monthly) 그대로. id 는 삭제할 때 쓴다
    public record UnavailableItem(UUID id, String date, String label, String time, String repeat) {}
}
