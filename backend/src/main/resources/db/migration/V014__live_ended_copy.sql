-- V014__live_ended_copy.sql — 청소가 끝난 뒤 고객 라이브 화면 문구를 녹화본 상태별로 나눈다 (코드에서 정함, 검증 검토)
-- ended(기본) = 아직 업로드 중 / ended_ready = 녹화본 재생 중 / ended_failed = 저장 실패 / ended_none = 녹화 없음
update app_settings
set value = value || '{
  "ended_ready": {"title": "청소가 끝났어요", "description": "녹화 영상으로 다시 볼 수 있어요"},
  "ended_failed": {"title": "청소가 끝났어요", "description": "녹화 영상을 저장하지 못했어요. 문의하시면 도와드릴게요"},
  "ended_none": {"title": "청소가 끝났어요", "description": "이 청소는 녹화 영상이 없어요"}
}'::jsonb
where key = 'live_status_copy';
