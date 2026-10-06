package kr.ansimclean.api.common.events;

import java.util.UUID;

// 아웃박스 이벤트 (CODE_GUIDE.md 3-6). 클래스 이름이 outbox_events 의 type 이 되어 핸들러를 고른다
// — 이름을 바꾸면 이미 쌓인 행이 "핸들러 없음" 으로 실패한다.
// 발행: LiveWebhookService(바디캠 송출이 끝났다는 LiveKit 웹훅) · LiveReconciler(1분 간격 상태 보정).
// 둘 다 작업이 진행 중(in_progress)일 때만 — 일시정지 중에 바디캠을 끈 것은 알리지 않는다.
// reason: "disconnected" = 붙어 있던 카메라가 끊김, "waiting" = 카메라가 5분 넘게 붙지 않음(LiveReconciler 만 보낸다).
// 처리: push.PushHandler — 매니저에게 (push 설정이 켜져 있을 때).
/** 바디캠이 끊겼거나(disconnected) 5분 넘게 붙지 않는다(waiting) → 매니저 푸시 "바디캠 연결이 끊겼어요" / waiting 은 "바디캠이 아직 연결되지 않았어요" (§8.1) */
public record CameraOffline(UUID bookingId, UUID managerId, String reason) {}
