-- V012__repair_booking.sql — 수리 예약 (Figma rGCjx3zWMBk18fXgNZ9UYX, 2026-09-21)
-- 카테고리 'repair'(수리 서비스): 수리비는 현장 진단 후 확정이라 기본 금액 0. 수리 종류(가전수리·배관…)는 bookings.repair_type(앱 REPAIR_CATEGORIES 키).
insert into service_categories (key, label, promo, promo_text, sort) values
  (E'repair', E'수리 서비스', E'placeholder', null, 5);
insert into catalog_prices (category_key, frequency, base_amount) values
  (E'repair', 'recurring', 0),
  (E'repair', 'oneTime', 0);
insert into catalog_frequency_copy (category_key, frequency, description) values
  (E'repair', 'oneTime', E'수리비는 현장 진단 후 확정돼요');
-- 현장 상황 칩 (Figma 1:341 문구 그대로). 예약의 notices 로 저장된다
insert into catalog_notice_chips (category_key, key, label, icon, sort) values
  (E'repair', E'parking', E'주차 가능해요', E'parking', 1),
  (E'repair', E'elevator', E'엘리베이터 있어요', E'elevator', 2),
  (E'repair', E'pet', E'반려동물이 있어요', E'pet', 3),
  (E'repair', E'intercom', E'공동현관 호출 필요', E'lock', 4);
update app_settings set value = value || '{"repair":2}'::jsonb where key = 'duration_hours';   -- 출장 수리 기본 2시간 (코드에서 정함)

-- 가전·증상·상세는 앱이 보낸 표시 문구 그대로(카탈로그는 앱 안). 사진은 Storage 'repair-photos' 버킷의 경로(<customerId>/<uuid>.<ext>)
alter table bookings
  add column repair_appliance text,                          -- '냉장고'
  add column repair_symptom   text,                          -- '냉장이 잘 안 돼요'
  add column repair_detail    text,                          -- 증상 상세
  add column repair_photos    text[] not null default '{}';  -- Storage 경로 (최대 5장)
