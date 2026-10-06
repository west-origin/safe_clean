package kr.ansimclean.api.common.events;

import java.util.UUID;

// 아웃박스 이벤트 (CODE_GUIDE.md 3-6). 클래스 이름이 outbox_events 의 type 이 되어 핸들러를 고른다
// — 이름을 바꾸면 이미 쌓인 행이 "핸들러 없음" 으로 실패한다.
// Egress = LiveKit 이 방 영상을 녹화해 Storage 에 올리는 작업.
// 발행: LiveWebhookService.applyEgress — 녹화가 완료되지 못한 채 끝났을 때(웹훅·재조정기 공통). 운영자용 오류 로그도 거기서 남긴다.
// 처리: push.PushHandler — 고객에게 '녹화 영상을 저장하지 못했어요' (알림 설정과 무관).
/** Egress 실패(녹화본이 만들어지지 않음) — 고객 안내 푸시 + 운영자 확인 로그 (검증 검토: 고객은 영영 오지 않을 녹화본을 기다리라는 문구만 봤다) */
public record RecordingFailed(UUID bookingId, UUID customerId) {}
