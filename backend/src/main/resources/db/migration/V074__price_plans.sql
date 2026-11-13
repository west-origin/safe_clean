-- V074__price_plans.sql — 사용자 요금표(2026-11-13)로 전환 (친구·선물 6회차, 규칙은 docs/domain.md '요금표 전환')
-- ① 요금표 숫자는 app_settings.price_plans 한 곳 (PricingService.plan 이 읽는다)
-- ② 예약·정기 묶음에 요금제 키, 정기 묶음에 고른 요일·월 요금
-- ③ 정기 요금제는 한 달치를 시리즈 하나로 결제 (payments.series_id), 회차 취소는 회차 몫만 부분 환불 (payment_refunds.booking_id)
-- ④ 분해청소(appliance) = 후드·덕트: 추가 요금 옵션 2개를 더하고, 요금표에 없는 옛 유료 옵션은 새 예약에서 뺀다(retired — 옛 예약의 이름 표시는 그대로)
-- 기존 예약·결제 행은 바꾸지 않는다.

-- ① 요금표 (원). weekly 의 키 = 주 몇 회, firstMonth = 첫 달 요금(원룸 주 1회만), hours = 1회 소요 시간
insert into app_settings (key, value) values ('price_plans', $json${
  "house":  {"hours": 3, "trial": 50000, "weekly": {"1": 329000, "2": 599000, "3": 889000}},
  "studio": {"hours": 2, "once": 69000, "weekly": {"1": 229000, "2": 399000, "3": 599000}, "firstMonth": {"1": 199000}},
  "hood":   {"sizes": {"600": 130000, "900": 150000, "1200": 200000}},
  "weeks": 4
}$json$::jsonb)
on conflict (key) do nothing;

-- ② 요금제 키 (house_trial · studio_once · house_weekly_N · studio_weekly_N · hood_600|900|1200). 요금표를 쓰지 않는 예약은 null
alter table bookings add column price_plan text null;
alter table bookings add constraint bookings_price_plan_check
  check (price_plan is null or price_plan ~ '^(house_trial|studio_once|(house|studio)_weekly_[1-3]|hood_(600|900|1200))$');
alter table booking_series add column price_plan text null;
alter table booking_series add constraint booking_series_price_plan_check
  check (price_plan is null or price_plan ~ '^(house|studio)_weekly_[1-3]$');
alter table booking_series add column weekdays smallint[] null;   -- 고른 요일 0=월 … 6=일 (주 N회 요금제만)
alter table booking_series add column plan_amount bigint null;    -- 만들 때 정한 한 달 요금(원룸 첫 달 할인 반영)
alter table booking_series add constraint booking_series_plan_fields
  check ((price_plan is null) = (weekdays is null) and (price_plan is null) = (plan_amount is null));
create index booking_series_customer_plan on booking_series(customer_id) where price_plan is not null and status <> 'cancelled';

-- ③ 시리즈 결제: 결제 대상은 예약·선물·시리즈 중 정확히 하나
alter table payments add column series_id uuid references booking_series(id);
alter table payments drop constraint payments_one_target;
alter table payments add constraint payments_one_target
  check ((booking_id is not null)::int + (gift_id is not null)::int + (series_id is not null)::int = 1);
create unique index payments_one_series on payments(series_id) where series_id is not null and status <> 'failed';

-- 회차 부분 환불: 결제 하나에 회차마다 한 행. booking_id 가 없는 행은 예전처럼 결제당 하나(잔액 전체 환불)
alter table payment_refunds drop constraint payment_refunds_payment_id_key;
alter table payment_refunds add column booking_id uuid null references bookings(id);
create unique index payment_refunds_one_full on payment_refunds(payment_id) where booking_id is null;
create unique index payment_refunds_one_visit on payment_refunds(payment_id, booking_id) where booking_id is not null;

-- ④ 분해청소 추가 요금 (해당될 때만) — 요금표 그대로. 옛 유료 옵션(에어컨·세탁조)은 새 예약에서 고를 수 없다
alter table catalog_options add column retired boolean not null default false;
update catalog_options set retired = true where category_key = 'appliance' and key in ('ac', 'washer');
insert into catalog_options (category_key, key, kind, label, price, icon, sort) values
  ('appliance', 'panel', 'paid', E'콘센트 가림판넬\n분리', 20000, 3, 4),
  ('appliance', 'noExtinguisher', 'paid', E'자동 소화장치\n없는 경우', 20000, 4, 5)
on conflict do nothing;
