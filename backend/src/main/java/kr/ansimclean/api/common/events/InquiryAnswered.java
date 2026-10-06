package kr.ansimclean.api.common.events;

import java.util.UUID;

// 아웃박스 이벤트 (CODE_GUIDE.md 3-6). 클래스 이름이 outbox_events 의 type 이 되어 핸들러를 고른다
// — 이름을 바꾸면 이미 쌓인 행이 "핸들러 없음" 으로 실패한다.
// 발행: SupportService.answer — 운영자가 관리자 API(X-Admin-Token)로 답변을 저장할 때.
// 처리: push.PushHandler — 문의한 사용자(고객·매니저 누구든)에게 알림 설정과 무관하게 푸시.
/** 1:1 문의에 운영자가 답변 — 사용자 푸시 (완결성 검토: 앱은 '답변은 앱 알림으로' 라 약속했는데 전달 경로가 없었다) */
public record InquiryAnswered(UUID inquiryId, UUID userId) {}
