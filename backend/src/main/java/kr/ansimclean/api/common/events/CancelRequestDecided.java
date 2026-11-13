package kr.ansimclean.api.common.events;

import java.util.UUID;

// 아웃박스 이벤트 (CODE_GUIDE.md 3-6). 클래스 이름이 outbox_events 의 type 이다 — 이름을 바꾸지 않는다.
// 발행: offer/ReassignService 의 승인·거절. 처리: push.PushHandler — 요청한 매니저에게(거래성, 알림 설정과 무관)
/** 어드민이 방문 취소 요청을 승인(approved=true)·거절했다 → 매니저 푸시 */
public record CancelRequestDecided(UUID requestId, UUID bookingId, UUID managerId, boolean approved) {}
