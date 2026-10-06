-- V013__live_paused_at.sql — 세션이 paused 로 확정된 시각. 재조정기가 '세션 paused ∧ 작업 in_progress' 를 unmute 할 때
-- 방금(live/pause 직후, jobs/pause 전) paused 가 된 세션은 건너뛰기 위한 기준점 (검수 3차)
alter table live_sessions add column paused_at timestamptz;
