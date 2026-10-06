package kr.ansimclean.api.common.events;

import java.util.UUID;

// 아웃박스 이벤트 (CODE_GUIDE.md 3-6). 클래스 이름이 outbox_events 의 type 이 되어 핸들러를 고른다
// — 이름을 바꾸면 이미 쌓인 행이 "핸들러 없음" 으로 실패한다.
// 발행: StaleBookingCleaner.clean (매일 03:10) — 방문일이 지났는데 requested·assigning 으로 남은 예약을 취소할 때.
// 처리: push.PushHandler — 고객에게 '매니저를 배정하지 못했어요' (알림 설정과 무관하게 보낸다).
/** 방문일이 지나도록 매니저가 배정되지 않아 시스템이 취소했다 → 고객 푸시 (§2.5 StaleBookingCleaner) */
public record BookingUnassigned(UUID bookingId, UUID customerId) {}
