package kr.ansimclean.api.common.events;

import java.util.UUID;

// 아웃박스 이벤트 (CODE_GUIDE.md 3-6). 클래스 이름이 outbox_events 의 type 이 되어 핸들러를 고른다
// — 이름을 바꾸면 이미 쌓인 행이 "핸들러 없음" 으로 실패한다.
// 발행: LiveWebhookService.applyEgress — 녹화(Egress)가 완료됐을 때(웹훅·재조정기 공통). 그때 고객이 이미 탈퇴했으면
// 녹화본을 바로 지우고 발행하지 않는다.
// 처리: push.PushHandler — 고객 푸시(알림 설정의 push 가 꺼져 있으면 보내지 않는다).
/** 녹화본이 Storage 에 올라갔다 → 고객 푸시 "청소 영상이 준비됐어요" (§8.2) */
public record RecordingReady(UUID bookingId, UUID customerId) {}
