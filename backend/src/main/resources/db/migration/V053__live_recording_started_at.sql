-- V051(스케줄러)·V052(결제)는 보존한다. 기존 녹화의 시작 시각은 추측하지 않는다.
alter table live_sessions add column recording_started_at timestamptz;
alter table live_sessions add column recording_start_confirmed boolean not null default false;
-- 요청 시각은 임시값일 뿐이다. 실제 파일 시작 시각이 확인된 녹화만 다시보기 구간을 만든다.
