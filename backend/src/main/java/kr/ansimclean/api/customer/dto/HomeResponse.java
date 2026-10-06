package kr.ansimclean.api.customer.dto;

import kr.ansimclean.api.booking.dto.ReservationResponse;
import tools.jackson.databind.JsonNode;

/**
 * GET /customer/home — { slides, upcoming, changeDeadlineHours }.
 * upcoming = 가장 가까운 예정 방문 1건(없으면 null) — 홈이 예약을 전혀 보여 주지 않았다 (사용성 검토 2026-09-22)
 * slides 는 app_settings.home_slides 에 저장된 JSON 을 모양 그대로 싣고(JsonNode), upcoming 은 예약 목록 카드와 같은 모양(ReservationResponse)이다.
 * changeDeadlineHours = 변경·취소 기한(방문 몇 시간 전까지, app_settings.cancel_deadline_hours) — upcoming 카드가 잠겼을 때(changeable=false)
 * '방문 N시간 전이 지나…' 안내가 이 값을 쓴다 (문제 목록 25번)
 */
public record HomeResponse(JsonNode slides, ReservationResponse upcoming, int changeDeadlineHours) {}
