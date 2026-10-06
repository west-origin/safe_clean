package kr.ansimclean.api.common.events;

import java.util.UUID;

// 아웃박스 이벤트 (CODE_GUIDE.md 3-6). 클래스 이름이 outbox_events 의 type 이 되어 핸들러를 고른다
// — 이름을 바꾸면 이미 쌓인 행이 "핸들러 없음" 으로 실패한다.
// 발행 두 곳:
//  - BookingService.cancelOne — 고객 취소·정기청소 전체 취소, 배정된 매니저가 있을 때만(또는 밤에 일정을 바꿔 '일정 변경' 안내를 아침으로
//    미뤄 둔 원래 매니저가 있을 때 — 그 매니저가 managerId, 합친 38건 검토 R1). reason = "customer" | "series_cancelled"
//  - StaleBookingCleaner (매일 03:10) — 방문일 다음 날까지 scheduled(배정됨·미시작)로 남은 예약을 시스템이 취소. reason = "not_started"
// reason 에는 사유 코드만 싣는다(고객의 자유 입력 사유는 넣지 않는다 — BookingService.cancelOne 의 주석).
// 처리: push.PushHandler — not_started 면 고객·매니저 둘 다 '미방문' 문구로(알림 설정과 무관), 그 밖에는 매니저에게만
// '예약이 취소됐어요'(push 설정이 켜져 있을 때).
/** 예약이 취소됐다 — 고객 취소, 또는 매니저 미방문으로 시스템이 취소(not_started). managerId 가 있으면 그 매니저에게 푸시 (BACKEND_PLAN.md §6.1) */
public record BookingCancelled(UUID bookingId, UUID customerId, UUID managerId, String reason) {}
