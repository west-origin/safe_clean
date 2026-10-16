-- 예약별 일회성 카드 결제. 기존 예약/결제 행과 결제 수단은 보존한다.
alter table payments drop constraint payments_status_check;
alter table payments add constraint payments_status_check check
  (status in ('pending','authorized','paid','failed','partially_refunded','refunded'));
alter table payments add column refunded_amount bigint not null default 0;
alter table payments add column next_check_at timestamptz;
alter table payments add column last_webhook_at timestamptz;
alter table payments add constraint payments_refund_bounds check
  (amount >= 0 and refunded_amount >= 0 and refunded_amount <= amount);
-- 결제 실패가 확정되기 전에는 다른 주문번호로 재청구할 수 없다.
create unique index payments_one_checkout on payments(booking_id)
  where provider = 'toss' and status <> 'failed';
create index payments_reconcile on payments(next_check_at) where provider = 'toss';

create table payment_refunds (
  id uuid primary key default gen_random_uuid(),
  payment_id uuid not null unique references payments(id),
  amount bigint not null check (amount > 0),
  status text not null check (status in ('pending','done')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
alter table payment_refunds enable row level security;
-- 브라우저가 결제 테이블을 직접 읽거나 수정할 수 없도록 명시한다.
do $$ declare role_name text; begin
  foreach role_name in array array['anon','authenticated'] loop
    if exists (select 1 from pg_roles where rolname = role_name) then
      execute format('revoke all on payment_refunds, payments, payment_methods from %I', role_name);
    end if;
  end loop;
end $$;
update app_settings set value = '"예약 후 방문 회차별로 결제할 수 있어요"'::jsonb where key = 'payment_note';
