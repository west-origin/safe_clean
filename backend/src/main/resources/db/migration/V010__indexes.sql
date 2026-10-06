-- V010__indexes.sql — 조회 패턴에 맞는 인덱스 (검수 2차). 스케줄러·화면 조회가 전체 스캔이 되지 않게
create index if not exists bookings_series_date on bookings(series_id, service_date) where series_id is not null;   -- 시리즈 회차 조회·전체 취소·직전 매니저
create index if not exists job_pauses_booking on job_pauses(booking_id, paused_at);                                  -- 중단 기록·열린 중단
create index if not exists live_sessions_status on live_sessions(status);                                            -- LiveReconciler 활성 세션
create index if not exists live_sessions_recording on live_sessions(recording_status, ended_at);                     -- RecordingRetention·끝난 세션 녹화 마무리
create index if not exists live_sessions_ended on live_sessions(ended_at) where status = 'ended';                    -- 끝난 방 정리 재확인
create index if not exists idempotency_keys_created on idempotency_keys(created_at);                                 -- AuthCleaner 04:10
create index if not exists phone_verifications_created on phone_verifications(created_at);                           -- AuthCleaner 04:10
