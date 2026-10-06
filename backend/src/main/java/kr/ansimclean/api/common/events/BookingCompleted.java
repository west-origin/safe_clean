package kr.ansimclean.api.common.events;

import java.util.UUID;

// 아웃박스 이벤트 (CODE_GUIDE.md 3-6). 클래스 이름이 outbox_events 의 type 이 되어 핸들러를 고른다
// — 이름을 바꾸면 이미 쌓인 행이 "핸들러 없음" 으로 실패한다.
// 발행: JobService.finish — 매니저 완료·운영자 완료가 같이 쓰는 완료 처리(정산 생성과 같은 트랜잭션).
// 처리: push.PushHandler — 고객에게 '청소가 완료됐어요', 매니저에게 '정산이 등록됐어요' (각자 알림 설정이 켜져 있을 때).
// 라이브 정지는 이 이벤트와 무관하다 — 매니저 앱이 완료 뒤 live/stop 을 부르고, 없거나 실패하면(운영자 완료 포함) 아래 재조정기가 멈춘다.
/** 작업 완료 → 고객 푸시 + 매니저 정산 푸시. 라이브 정지(Egress·방)는 B5 의 LiveReconciler 가 상태(done ∧ live≠ended)로 처리한다 */
public record BookingCompleted(UUID bookingId, UUID customerId, UUID managerId) {}
