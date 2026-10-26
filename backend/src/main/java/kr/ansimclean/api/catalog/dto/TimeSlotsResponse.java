package kr.ansimclean.api.catalog.dto;

import java.util.List;

/**
 * = 앱 getTimeSlots() → { am, pm } + unavailable(마감된 시각. B3 는 빈 배열, 결정 §13-6) + bookableDays(오늘부터 며칠 뒤까지 예약할 수 있나, 전체 재점검 B1)
 * + changeDeadlineHours(방문 몇 시간 전부터 변경·취소가 막히나 = cancel_deadline_hours — 그 안의 시간을 고르면 앱이 미리 알린다, 직접 사용 점검 U2)
 * GET /catalog/time-slots 응답 (CatalogService.timeSlots). am·pm 은 "HH:mm" 글자 목록(app_settings.time_slots).
 */
public record TimeSlotsResponse(List<String> am, List<String> pm, List<String> unavailable, int bookableDays, int changeDeadlineHours, int minimumLeadHours) {}
