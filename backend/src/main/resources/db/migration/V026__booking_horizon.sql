-- V026__booking_horizon.sql — 예약 가능 기간 상한 (전체 재점검 B1, 코드에서 정함 — PROGRESS 판단 항목).
-- 상한이 없어 10년 뒤 날짜로도 예약이 만들어지고 바로 매칭 오퍼가 나갔다. 앱 달력도 /catalog/time-slots 의 bookableDays 로 같은 날까지만 연다.
insert into app_settings (key, value) values (E'booking_horizon_days', '60'::jsonb)
on conflict (key) do nothing;
