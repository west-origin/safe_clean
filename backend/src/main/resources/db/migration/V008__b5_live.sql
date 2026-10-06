-- V008__b5_live.sql — B5 라이브: 재개 재시도 표시, 카메라 대기 알림 1회 표시, 고객 라이브 화면 문구
alter table live_sessions add column unmute_pending boolean not null default false;   -- live/resume 이 unmute 를 확인하지 못했다 → LiveReconciler 가 다시 시도 (§8.2.1)
alter table live_sessions add column camera_wait_notified_at timestamptz;             -- waiting_camera 5분 초과 매니저 푸시를 한 번만 (§2.5)

insert into app_settings (key, value) values
  (E'live_status_copy', '{"waiting":{"title":"카메라 연결을 기다리고 있어요","description":"매니저가 바디캠을 켜면 바로 보여드릴게요"},"live":{"title":"거실 청소 중","description":"깨끗한 공간을 만들어가고 있어요!"},"paused":{"title":"촬영이 잠시 중단됐어요","description":"다시 시작되면 영상이 이어져요"},"ended":{"title":"청소가 끝났어요","description":"녹화 영상이 준비되면 다시 볼 수 있어요"}}'::jsonb),
  (E'live_reassurance', '{"title":"안심하고 지켜보세요!","description":"항상 최선을 다해 청소하고 있어요 💙"}'::jsonb);
