-- V070__vouchers_and_gifts.sql — 금액권·친구 선물 (2026-11-13 사용자 결정, docs/domain.md '고객 친구·금액권 선물' › 금액권·선물 규칙)
--
-- 금액권은 선물로만 생긴다. '새로 구매해 선물' 은 기존 토스 일회성 결제(payments)를 선물 대상으로 넓혀 쓴다:
-- payments 한 행은 예약(booking_id) 또는 선물(gift_id) 중 정확히 하나의 결제다.
-- 잠금 순서: 선물 → 금액권 → 결제 (예약 결제는 그대로 예약 → 결제 → 환불).

-- ① 금액권
create table vouchers (
  id            uuid primary key default gen_random_uuid(),
  owner_id      uuid not null references profiles(id),   -- 지금 가진 사람 (선물 중에는 보낸 사람)
  purchaser_id  uuid not null references profiles(id),   -- 결제한 사람 (환불 기준)
  catalog_key   text not null,                           -- app_settings.gift_catalog 의 key (산 시점 값)
  label         text not null,                           -- 산 시점의 상품 이름
  face_amount   bigint not null check (face_amount > 0), -- 얼굴 금액(원)
  balance       bigint not null,                         -- 남은 금액(원). 3회차에서 예약 결제에 쓴 만큼 준다
  status        text not null check (status in ('unpaid','held','gifting','void')),
  transferable  boolean not null default true,           -- 프로모션 금액권은 false (선물 불가)
  purchased_at  timestamptz,                             -- 결제 확정 시각 (구매 7일 환불 기준)
  received_at   timestamptz,                             -- 마지막으로 선물을 받은 시각
  valid_until   timestamptz,                             -- 유효기간 끝. 받은 날 + 1년. 아직 아무도 받지 않았으면 null
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  check (balance >= 0 and balance <= face_amount)
);
create index vouchers_owner on vouchers(owner_id, created_at desc) where status in ('held','gifting');
create trigger vouchers_updated_at before update on vouchers for each row execute function set_updated_at();

-- ② 선물 (이력 겸용 — 5년 보관)
create table gifts (
  id            uuid primary key default gen_random_uuid(),
  sender_id     uuid not null references profiles(id),
  recipient_id  uuid not null references profiles(id),
  voucher_id    uuid not null references vouchers(id),
  method        text not null check (method in ('purchase','voucher')),
  message       text check (char_length(message) <= 200),
  amount        bigint not null check (amount > 0),      -- 보낸 금액 (이력)
  label         text not null,                           -- 보낸 상품 이름 (이력)
  status        text not null check (status in ('awaiting_payment','sent','accepted','rejected','expired','cancelled','payment_failed')),
  created_at    timestamptz not null default now(),
  sent_at       timestamptz,                             -- 받는 사람에게 보낸 시각 (구매면 결제 확정 시각)
  expires_at    timestamptz,                             -- 이 시각까지 받지 않으면 보낸 사람에게 돌아간다
  decided_at    timestamptz,                             -- 수락·거절·만료·취소·결제 실패 시각
  check (sender_id <> recipient_id)
);
-- 한 금액권에 열린 선물은 하나 — 같은 금액권 동시 선물의 마지막 방어
create unique index gifts_one_open_per_voucher on gifts(voucher_id) where status in ('awaiting_payment','sent');
create index gifts_sender on gifts(sender_id, created_at desc);
create index gifts_recipient on gifts(recipient_id, created_at desc) where status <> 'awaiting_payment';
create index gifts_due on gifts(expires_at) where status = 'sent';
create index gifts_awaiting on gifts(created_at) where status = 'awaiting_payment';

-- ③ 결제를 선물에도: 예약 또는 선물 중 정확히 하나
alter table payments alter column booking_id drop not null;
alter table payments add column gift_id uuid references gifts(id);
alter table payments add constraint payments_one_target check ((booking_id is null) <> (gift_id is null));
-- 선물 하나에 살아 있는 결제는 하나 (실패가 확정된 결제는 남긴다)
create unique index payments_one_gift on payments(gift_id) where gift_id is not null and status <> 'failed';

alter table vouchers enable row level security; -- 정책 0개 = 앱 키로는 아무것도 못 한다 (서버만). 앱 키 권한은 V064 로 처음부터 없다
alter table gifts enable row level security;
comment on table vouchers is '금액권. 선물로만 생긴다 (docs/domain.md 금액권·선물 규칙)';
comment on table gifts is '친구 선물과 이력 (5년 보관)';

-- ④ 설정: 받는 기간(일), 유효기간(일), 선물 목록(요금표 2026-11-13 — 첫 1회 체험 50,000원은 선물 불가라 뺐다)
insert into app_settings (key, value) values
  ('gift_accept_days', '7'::jsonb),
  ('voucher_valid_days', '365'::jsonb),
  ('gift_catalog', $json$[
    {"key":"home_weekly_1","group":"가정 정기청소","label":"가정 정기청소 주 1회 (한 달 4회 방문)","amount":329000},
    {"key":"home_weekly_2","group":"가정 정기청소","label":"가정 정기청소 주 2회 (한 달 8회 방문)","amount":599000},
    {"key":"home_weekly_3","group":"가정 정기청소","label":"가정 정기청소 주 3회 (한 달 12회 방문)","amount":889000},
    {"key":"studio_weekly_1","group":"원룸 정기청소","label":"원룸 정기청소 주 1회 (한 달 4회 방문)","amount":229000},
    {"key":"studio_weekly_2","group":"원룸 정기청소","label":"원룸 정기청소 주 2회 (한 달 8회 방문)","amount":399000},
    {"key":"studio_weekly_3","group":"원룸 정기청소","label":"원룸 정기청소 주 3회 (한 달 12회 방문)","amount":599000},
    {"key":"studio_once","group":"원룸 정기청소","label":"원룸 1회 청소 (1회 2시간)","amount":69000},
    {"key":"hood_600","group":"후드·덕트 분해청소","label":"후드·덕트 분해청소 600mm (일반 주방)","amount":130000},
    {"key":"hood_900","group":"후드·덕트 분해청소","label":"후드·덕트 분해청소 900mm (넓은 주방)","amount":150000},
    {"key":"hood_1200","group":"후드·덕트 분해청소","label":"후드·덕트 분해청소 1200mm (초대형)","amount":200000},
    {"key":"marble_straight","group":"대리석 코팅","label":"대리석 코팅 1자 상판 (일자형 주방)","amount":290000},
    {"key":"marble_l","group":"대리석 코팅","label":"대리석 코팅 ㄱ자 상판 (ㄱ자형 주방)","amount":360000},
    {"key":"marble_u","group":"대리석 코팅","label":"대리석 코팅 ㄷ자 상판 (ㄷ자형 주방)","amount":420000},
    {"key":"crack_basic","group":"대리석 크랙 수리","label":"대리석 크랙 수리 기본 (일자 크랙 1개, 30cm 미만)","amount":140000}
  ]$json$::jsonb)
on conflict (key) do nothing;
