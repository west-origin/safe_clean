-- 매니저가 영상 일시정지를 '성공' 으로 안내받았고 아직 재개하지 않았는가 (2026-09-28, 사용자 결정 "매니저가 재개할 때까지 차단 유지").
-- 영상만 막고 작업 일시정지(jobs/pause)가 실패한 세션에서 바디캠이 끊겼다 다시 붙으면, 세션이 waiting_camera 를 거치며 '일시정지' 를 잊어
-- 재접속 처리(cameraAttached)가 작업 상태(진행 중)만 보고 새 트랙을 막지 않았다 — 매니저가 재개하지 않았는데 영상과 고객 시청이 다시 열렸다.
-- 카메라 연결 상태와 따로 남는 표시를 두고, 매니저의 재개 요청(live/resume 또는 jobs/resume)에서만 지운다.
alter table live_sessions add column video_held boolean not null default false;

-- 지금 영상이 멈춰 있는 세션 중 '매니저가 멈춘 채' 인 것을 채운다: 작업이 일시정지이거나, 작업 일시정지가 영상 일시정지 뒤에 한 번도 되지 않은 것
-- (= 영상만 막고 jobs/pause 가 실패). 작업이 일시정지됐다가 재개된 세션(jobs/resume 뒤 live/resume 이 닿지 않은 경우)은 재조정기가 다시 켜도록 둔다
update live_sessions s set video_held = true
from bookings b
where b.id = s.booking_id
  and s.status = 'paused'
  and (b.status = 'paused' or s.job_paused_at is null or s.paused_at is null or s.job_paused_at < s.paused_at);
