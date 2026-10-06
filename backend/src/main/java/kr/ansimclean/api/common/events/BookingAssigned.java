package kr.ansimclean.api.common.events;

import java.util.UUID;

// 아웃박스 이벤트 (CODE_GUIDE.md 3-6). 클래스 이름이 outbox_events 의 type 이 되어 핸들러를 고른다
// — 이름을 바꾸면 이미 쌓인 행이 "핸들러 없음" 으로 실패한다.
// 발행: OfferService.accept — 콜 수락으로 예약을 배정하는 트랜잭션 안에서.
// 처리: push.PushHandler — 고객에게 푸시(알림 설정의 push 가 꺼져 있으면 보내지 않는다).
/** 매니저가 수락해 예약이 확정됐다 → 고객 푸시 "매니저가 배정됐어요" (§6.1) */
public record BookingAssigned(UUID bookingId, UUID customerId, UUID managerId) {}
