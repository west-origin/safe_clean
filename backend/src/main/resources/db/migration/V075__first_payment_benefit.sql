-- V075__first_payment_benefit.sql — 가정 정기청소 첫 결제 혜택: 다음 분해청소 1회 무료 (사용자 결정 2026-11-14, docs/domain.md '첫 결제 혜택·정기 요금제 금액권')
-- ① 예약 할인 금액. 고객이 낼 금액 = total_amount − discount_amount − 금액권. 매니저 몫·수수료는 total_amount 그대로(할인은 회사 부담)
-- ② 혜택 사용 기록: 고객당 1회 + 같은 집(원천이 된 가정 정기 시리즈의 주소) 1회. 혜택을 쓴 예약이 취소되면 혜택은 돌아온다(그 행은 없는 것으로 본다)
-- 기존 예약 행은 바꾸지 않는다 (discount_amount = 0).

alter table bookings add column discount_amount bigint not null default 0;
alter table bookings add constraint bookings_discount_check check (discount_amount >= 0 and discount_amount <= total_amount);

create table first_payment_benefits (
  id                uuid primary key default gen_random_uuid(),
  customer_id       uuid not null references profiles(id),
  booking_id        uuid not null unique references bookings(id),        -- 혜택으로 무료가 된 분해청소 예약
  source_series_id  uuid not null references booking_series(id),         -- 결제가 확정된 가정 정기 시리즈 (혜택의 근거)
  road_address      text not null,                                       -- 원천 시리즈 주소 (같은 집 판정)
  detail_address    text,
  building_key      text,
  amount            bigint not null check (amount > 0),
  created_at        timestamptz not null default now()
);
create index first_payment_benefits_customer on first_payment_benefits(customer_id);
