-- V015__matching_failed.sql — 매칭 실패(후보 0명·회차 소진)를 예약 행에 남긴다. 고객 카드 문구·운영자 조회의 기준 (검증 검토: 로그 한 줄뿐이라 아무도 몰랐다)
alter table bookings add column matching_failed_at timestamptz;
create index bookings_matching_failed_idx on bookings (matching_failed_at) where matching_failed_at is not null;
insert into app_settings (key, value) values
  (E'cleaner_matching_failed_label', '"매니저를 찾고 있어요 · 조금 더 걸릴 수 있어요"'::jsonb);
