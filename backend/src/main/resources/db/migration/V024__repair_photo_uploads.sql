-- V024__repair_photo_uploads.sql — 수리 사진 업로드 원장 (감사 M-04).
-- 사진은 예약 전에 올라가므로(POST /customer/repair-photos) 예약을 그만두면 bookings 에 경로가 남지 않아 지울 길이 없었다.
-- 올릴 때 행을 만들고 예약에 붙으면 attached_at 을 적는다. 24시간 동안 붙지 않은 사진은 RepairPhotoCleaner 가 Storage 에서 지운다.

create table repair_photo_uploads (
  path         text primary key,               -- <customerId>/<uuid>.<ext> (Storage 버킷 repair-photos)
  customer_id  uuid not null,
  created_at   timestamptz not null,
  attached_at  timestamptz                     -- 예약에 붙은 시각. null = 아직 안 붙음
);
create index repair_photo_uploads_unattached on repair_photo_uploads(created_at) where attached_at is null;
create index repair_photo_uploads_customer on repair_photo_uploads(customer_id);

alter table repair_photo_uploads enable row level security;  -- V001 과 같게 deny-all (서버만 service_role 로 접근)
