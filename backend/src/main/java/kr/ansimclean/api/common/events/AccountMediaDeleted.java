package kr.ansimclean.api.common.events;

import java.util.UUID;

// 아웃박스 이벤트 (CODE_GUIDE.md 3-6). 클래스 이름이 outbox_events 의 type 이 되어 핸들러를 고른다
// — 이름을 바꾸면 이미 쌓인 행이 "핸들러 없음" 으로 실패한다.
// 발행: AuthService.deleteAccount (탈퇴 트랜잭션 안).
// 처리: live.AccountMediaDeletionHandler — 그 사용자의 수리 사진 전부, 고객 예약의 준비된 녹화본, 매니저 기기 행과 바디캠 Ingress 를 지운다.
// 지우다 실패하면(예: Ingress·Storage 삭제) 예외가 올라가 그 처리의 DB 변경은 되돌려지고 아웃박스가 재시도한다.
/** 탈퇴 뒤 미디어·기기 정리 (live.AccountMediaDeletionHandler). AccountDeleted 와 같이 발행 — 아웃박스는 타입당 핸들러 하나라 이벤트를 나눴다 */
public record AccountMediaDeleted(UUID userId) {}
