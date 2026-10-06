-- V023__revoked_sessions.sql — 로그아웃·비밀번호 재설정으로 끊은 Supabase 세션 (감사 M-03).
-- Supabase access JWT 는 만료(1시간) 전까지 서명만으로 통과하고, 로그아웃 upstream 호출이 실패하면 refresh 토큰도 살아 있다.
-- 서버가 끊은 session_id(JWT 의 session_id claim)를 여기 적어 두고, 인증 필터와 /auth/refresh 가 거부한다.
-- expires_at 이 지나면 AuthCleaner 가 지운다(기본 90일 — Supabase 세션 비활성 만료를 이보다 짧게 둔다, runbook).

create table revoked_sessions (
  session_id  text primary key,
  user_id     uuid not null,
  revoked_at  timestamptz not null,
  expires_at  timestamptz not null
);
create index revoked_sessions_expires on revoked_sessions(expires_at);

alter table revoked_sessions enable row level security;  -- V001 과 같게 deny-all (서버만 service_role 로 접근)
