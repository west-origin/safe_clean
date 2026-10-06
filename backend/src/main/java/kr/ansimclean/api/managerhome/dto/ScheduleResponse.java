package kr.ansimclean.api.managerhome.dto;

import java.util.List;

// 매니저 앱 일정 화면의 응답 모양. ManagerHomeService.schedule() 이 만든다.
// from 이 없으면 오늘, to 가 없으면 from + 6일(7일치). 취소된 예약은 빠지고 끝난(done) 것은 보인다. 앱은 manager.js 의 getSchedule() 로 받는다.
/** GET /manager/schedule?from=&to= — items = 오늘, week = 날짜별(항목이 있는 날만) */
public record ScheduleResponse(List<ScheduleItem> items, List<Day> week) {
    // 하루치 묶음: dateKey = "2026-09-15" (날짜를 구분하는 키), label = "9월 15일 (화)" (화면에 보이는 글자)
    public record Day(String dateKey, String label, List<ScheduleItem> items) {}
}
