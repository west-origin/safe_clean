-- V017__repair_keys.sql — 수리 예약의 가전·증상 '키' 를 문구와 나란히 저장 (검증 검토 4 수정판)
-- 문구(repair_appliance/repair_symptom)는 기사 화면·스냅샷용으로 그대로 두고, 통계·수리 매니저 매칭(미결정)은 키로 한다. 카탈로그가 앱 안에 있어 키만 보내면 기사 화면이 깨진다
alter table bookings
  add column repair_appliance_key text,                      -- 'fridge'
  add column repair_symptom_key   text;                      -- 'cooling'
