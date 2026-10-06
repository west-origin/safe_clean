package kr.ansimclean.api.booking.dto;

import tools.jackson.databind.JsonNode;

import java.util.List;

// 고객 앱 예약 탭 화면 전체의 응답. BookingService.reservations 가 만든다.
// header·tabs = 화면 머리 문구와 탭 목록 — app_settings 의 reservations_header·reservation_tabs 값(JSON)을 모양 그대로 넘긴다.
// reservations = 이 고객의 예약 카드 전부(약속 시각이 늦은 것부터). JsonNode = 모양을 정하지 않은 JSON 덩어리(Jackson).
// changeDeadlineHours = 변경·취소 기한(방문 몇 시간 전까지, app_settings.cancel_deadline_hours) — 잠긴 카드의 '방문 N시간 전이 지나…' 와
//   정기 회차 취소 시트의 'N시간 안에 시작하는 방문은…' 이 이 값을 쓴다. 앱 고정 24 였던 때는 운영자가 기한을 바꾸면 카드 잠금과 안내가 달랐다 (문제 목록 25번)
/** GET /customer/reservations */
public record ReservationsResponse(JsonNode header, JsonNode tabs, List<ReservationResponse> reservations, int changeDeadlineHours) {}
