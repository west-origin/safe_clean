package kr.ansimclean.api.common.events;

import java.util.UUID;

// 아웃박스 이벤트 (CODE_GUIDE.md 3-6). 클래스 이름이 outbox_events 의 type 이다 — 이름을 바꾸지 않는다.
// 발행: offer/OfferService.awaitAdmin — 재배정 콜을 아무도 맡지 않았거나(no_accept), 방문이 가까워(imminent) 또는 후보가 없어(no_candidates)
// 예약이 관리자 배정 대기가 됐을 때 한 번. 처리: push.PushHandler — 어드민 전원에게(알림 설정과 무관). 개인정보는 싣지 않는다 —
// 고객·주소·취소한 매니저는 어드민이 MFA 로 들어온 관리자 화면(GET /admin/reassignments)에서 본다
/** 예약이 관리자 배정 대기가 됐다 → 어드민 푸시 '재배정이 필요한 예약이 있어요'. reason = no_accept | imminent | no_candidates */
public record AdminAssignNeeded(UUID bookingId, String reason) {}
