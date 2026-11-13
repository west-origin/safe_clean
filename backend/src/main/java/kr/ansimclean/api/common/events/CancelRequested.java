package kr.ansimclean.api.common.events;

import java.util.UUID;

// 아웃박스 이벤트 (CODE_GUIDE.md 3-6). 클래스 이름이 outbox_events 의 type 이 되어 핸들러를 고른다 — 이름을 바꾸지 않는다.
// 발행: job/JobService 의 매니저 방문 취소 요청(새 요청을 만들었을 때만).
// 처리: push.PushHandler — 어드민 전원에게 알림 설정과 무관하게 푸시. 잠금화면에 보일 수 있어 개인정보는 싣지 않는다
/** 매니저가 방문 취소를 요청했다 → 어드민 푸시 '방문 취소 요청이 들어왔어요' (docs/domain.md '매니저 방문 취소 승인·재배정') */
public record CancelRequested(UUID requestId, UUID bookingId) {}
