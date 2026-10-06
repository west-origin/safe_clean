-- V019__call_details.sql — 콜 카드 이동 시간 문구를 비운다.
-- "이동 시간은 준비 중이에요"(V016) 는 개발 상태를 사용자에게 보여 주는 문구였다 (사용성 검토 2026-09-22).
-- 거리 계산이 붙으면 이 설정에 실제 문구를 넣고, 그때까지 앱은 이 줄을 그리지 않는다.
update app_settings set value = '""'::jsonb where key = 'eta_placeholder';
