-- 약속 시간에서 tardyMinutes(기본 30분)이 지나도 청소를 시작하지 않으면 고객에게 한 번 알린다 (docs/manager-policy.md §2, 사용자 결정 2026-09-27 — 자체 점검 #25).
-- 한 번만 보내려고 보낸 시각을 남긴다
alter table bookings add column late_notified_at timestamptz;
create index bookings_late_notice_idx on bookings (starts_at) where status = 'scheduled' and late_notified_at is null;
