package kr.ansimclean.api.common.events;

import java.util.UUID;

// 아웃박스 이벤트 (CODE_GUIDE.md 3-6). 클래스 이름이 outbox_events 의 type 이다 — 이름을 바꾸지 않는다.
// 발행: offer/ReassignService.assign (어드민 직접 배정). 고객 푸시는 같은 트랜잭션의 BookingAssigned 가 따로 맡는다.
// 처리: push.PushHandler — 배정된 매니저에게(거래성, 알림 설정과 무관 — 콜을 수락하지 않았는데 일정이 생겼다)
/** 어드민이 예약을 매니저에게 직접 배정했다 → 매니저 푸시 '관리자가 청소를 배정했어요' */
public record AdminAssigned(UUID bookingId, UUID managerId) {}
