package kr.ansimclean.api.common.events;

import java.util.UUID;

// 아웃박스 이벤트 (CODE_GUIDE.md 3-6). 클래스 이름이 outbox_events 의 type 이 되어 핸들러를 고른다
// — 이름을 바꾸면 이미 쌓인 행이 "핸들러 없음" 으로 실패한다.
// 발행: LiveService.cameraAttached — LiveKit 웹훅(LiveWebhookService)이나 웹훅을 놓쳤을 때 재조정기(LiveReconciler)가 부른다.
// 처리: push.PushHandler — first 가 true 일 때만 고객 푸시(재접속마다 보내지 않는다).
/** 바디캠이 방에 붙었다 (§8.1). first = 이 작업에서 처음 → 고객 푸시 "청소가 시작됐어요 · 실시간 영상 보기" */
public record CameraOnline(UUID bookingId, UUID customerId, boolean first) {}
