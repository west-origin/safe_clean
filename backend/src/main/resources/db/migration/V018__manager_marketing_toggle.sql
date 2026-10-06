-- V018__manager_marketing_toggle.sql — 매니저 알림 설정에 '마케팅 정보 수신' 토글 (매니저 마케팅 약관 본문이 '알림 설정에서 해제' 를 약속한다 — 완결성 검토)
update app_settings
set value = value || '[{"key":"marketing","label":"마케팅 정보 수신"}]'::jsonb
where key = 'manager_notification_rows';
