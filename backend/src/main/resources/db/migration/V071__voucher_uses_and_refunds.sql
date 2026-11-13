-- V071__voucher_uses_and_refunds.sql — 금액권 사용·복원·환불 (2026-11-13 사용자 결정, docs/domain.md 금액권 사용·환불 규칙)
--
-- 예약 결제에 금액권을 쓴 기록(voucher_uses)과 환불 요청(voucher_refunds). 예약당 금액권 1장.
-- 계좌 환불의 계좌번호는 서버가 AES-GCM 으로 암호화한 값만 두고(account_cipher), 송금 완료·반려 때 지운다(끝 4자리만 남김).

-- ① 금액권 상태 추가: used(잔액 0) · refund_requested(계좌 환불 접수 — 사용·선물 불가) · refunded
alter table vouchers drop constraint vouchers_status_check;
alter table vouchers add constraint vouchers_status_check
  check (status in ('unpaid','held','gifting','void','used','refund_requested','refunded'));

-- ② 사용 원장. applied = 예약 결제에 쓰는 중, restored = 예약 취소·빼기로 돌려줌
create table voucher_uses (
  id           uuid primary key default gen_random_uuid(),
  voucher_id   uuid not null references vouchers(id),
  booking_id   uuid not null references bookings(id),
  customer_id  uuid not null references profiles(id),
  amount       bigint not null check (amount > 0),
  status       text not null check (status in ('applied','restored')),
  created_at   timestamptz not null default now(),
  restored_at  timestamptz,
  restore_reason text check (restore_reason in ('booking_cancelled','removed'))
);
-- 예약당 쓰는 중인 금액권은 하나 (사용자 결정 2026-11-13)
create unique index voucher_uses_one_per_booking on voucher_uses(booking_id) where status = 'applied';
create index voucher_uses_voucher on voucher_uses(voucher_id);
create index voucher_uses_applied on voucher_uses(booking_id) where status = 'applied';

-- ③ 환불 요청. card = 구매 카드 결제 취소(100%), bank = 계좌 송금(100% 또는 95%)
create table voucher_refunds (
  id              uuid primary key default gen_random_uuid(),
  voucher_id      uuid not null references vouchers(id),
  owner_id        uuid not null references profiles(id),  -- 요청한 사람(그때의 소유자)
  method          text not null check (method in ('card','bank')),
  rate            int not null check (rate in (95, 100)),  -- 환불 비율(%)
  base_amount     bigint not null check (base_amount > 0), -- 환불 계산의 기준 잔액
  amount          bigint not null check (amount > 0),      -- 실제 돌려줄 금액(원)
  status          text not null check (status in ('processing','done','requested','paid','rejected')),
  bank_name       text,
  account_holder  text,
  account_cipher  text,                                    -- 계좌번호 암호문(AES-GCM, 키는 서버 환경변수). 완료·반려 때 null
  account_last4   text,
  requested_at    timestamptz not null default now(),
  decided_at      timestamptz,
  decided_by      uuid references profiles(id),            -- 완료·반려한 어드민
  note            text,
  check ((method = 'card') = (bank_name is null))
);
-- 한 금액권에 진행 중인 환불은 하나
create unique index voucher_refunds_one_open on voucher_refunds(voucher_id) where status in ('processing','requested');
create index voucher_refunds_requested on voucher_refunds(requested_at) where status = 'requested';

alter table voucher_uses enable row level security;   -- 정책 0개 = 서버만. 앱 키 권한은 V064 로 처음부터 없다
alter table voucher_refunds enable row level security;
comment on table voucher_uses is '금액권을 예약 결제에 쓴 기록 (예약당 1장, 취소하면 restored)';
comment on table voucher_refunds is '금액권 환불 요청. 계좌번호는 암호문만, 처리 뒤 삭제';
