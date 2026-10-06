package kr.ansimclean.api.common.events;

import java.util.UUID;

// 아웃박스 이벤트 (CODE_GUIDE.md 3-6). 클래스 이름이 outbox_events 의 type 이 되어 핸들러를 고른다
// — 이름을 바꾸면 이미 쌓인 행이 "핸들러 없음" 으로 실패한다.
// 발행: AuthService.deleteAccount — 탈퇴 트랜잭션 안에서 AccountMediaDeleted 와 함께.
// 처리: auth.SupabaseUserDeletionHandler — Supabase 로그인 계정을 지운다(외부 호출이라 커밋 뒤 아웃박스가 보낸다).
/** 탈퇴(익명화) 완료 — SupabaseUserDeletionHandler 가 auth.users 를 지운다(실패하면 아웃박스가 재시도) */
public record AccountDeleted(UUID userId) {}
