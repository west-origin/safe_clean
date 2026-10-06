package kr.ansimclean.api.common.events;

import java.util.UUID;

// 아웃박스 이벤트 (CODE_GUIDE.md 3-6). 클래스 이름이 outbox_events 의 type 이 되어 핸들러를 고른다
// — 이름을 바꾸면 이미 쌓인 행이 "핸들러 없음" 으로 실패한다.
// 발행: JobService 의 매니저 방문 취소(같은 트랜잭션에서 CameraReleased 도 발행). 예약은 새 회차로 다시 콜을 돌린다.
// 처리: push.PushHandler — 고객에게 알림 설정과 무관하게 푸시.
/** 매니저가 방문을 취소해 예약이 다시 매칭된다 → 고객 푸시 "매니저 배정이 취소됐어요" (docs/manager-policy.md) */
public record ManagerReleased(UUID bookingId, UUID customerId) {}
