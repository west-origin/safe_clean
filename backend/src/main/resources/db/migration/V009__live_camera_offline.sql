-- V009__live_camera_offline.sql — 작업 중 바디캠이 끊긴 시각. '5분 넘게 끊기면' 매니저 푸시(§2.5)의 기준점 (시작 시각 기준이면 끊긴 직후 바로 나갔다)
alter table live_sessions add column camera_offline_at timestamptz;
