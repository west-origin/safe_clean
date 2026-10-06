-- V020__support_phone.sql — 고객센터 전화번호.
-- 현장에서 바디캠이 죽으면 "영업일 1일 안에 답변" 은 소용이 없다 (사용성 검토 2026-09-22).
-- 운영 번호가 정해지면 이 값을 채운다(docs/runbook.md). 비어 있으면 앱은 전화 버튼을 그리지 않는다.
insert into app_settings (key, value) values (E'support_phone', '""'::jsonb)
on conflict (key) do nothing;
