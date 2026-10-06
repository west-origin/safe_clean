package kr.ansimclean.api.managerhome.dto;

import java.util.UUID;

// 작업 일정 카드 한 장의 응답 모양. ManagerHomeService.items() 가 예약(Booking) 하나로 만든다.
// HomeResponse.remaining 과 ScheduleResponse(items·week) 가 이 모양을 같이 쓴다.
// id·jobId 는 지금 둘 다 예약 id 로 같은 값이다 — 앱은 카드를 누르면 /job/{jobId} 로 간다.
// time = "09:00 - 12:00" (시작~시작+소요 시간), service = 청소 종류 이름(예: 가정/원룸 청소)
/** 홈 "남은 일정"·일정 화면의 카드 (앱 ScheduleItem). status = StatusBadge 값, customer = "박○○ 고객 · 해운대구 우동" */
public record ScheduleItem(UUID id, UUID jobId, String time, String status, String customer, String service) {}
