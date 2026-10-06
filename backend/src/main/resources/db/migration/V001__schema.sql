-- V001__schema.sql
create extension if not exists pgcrypto;

-- ── 계정 ──────────────────────────────────────────────────────────────
create table profiles (
  id            uuid primary key,                          -- = auth.users.id. FK 를 걸지 않는다: 탈퇴 시 Supabase 사용자는 지우고 이 행은 익명화해 남긴다(예약·정산 이력 보존)
  role          text not null check (role in ('customer','manager')),
  name          text not null,
  nickname      text,
  phone         text not null,                             -- E.164 (+8210...). 휴대폰 하나 = 살아 있는 계정 하나 = 역할 하나
  avatar_path   text,                                      -- Storage 경로 (profile-images/<id>.webp)
  signup_completed_at timestamptz,                         -- /auth/signup/complete 시각. null 이면 가입 중 (profileComplete=false)
  deleted_at    timestamptz,                               -- 탈퇴 시각. 익명화 후 채움
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);
create unique index profiles_phone_live on profiles(phone) where deleted_at is null;

create table idempotency_keys (                            -- 쓰기 요청 중복 방지 (§2.1, 알고리즘은 §6.9)
  key              text not null,
  user_id          uuid not null,
  request_hash     text not null,                          -- SHA-256(method + path + body). 다르면 IDEMPOTENCY_MISMATCH
  status           text not null check (status in ('processing','done')),
  response_status  int,
  response_body    jsonb,
  created_at       timestamptz not null default now(),
  primary key (key, user_id)
);

create table terms (                                       -- 약관 (문안은 법무 검토 후 여기서 교체)
  id        text not null,                                 -- service · privacy · location · marketing · ux · recording
  audience  text not null check (audience in ('customer','manager')),
  label     text not null,                                 -- "(필수) 서비스 이용약관"
  title     text not null,
  body      text not null,
  required  boolean not null,
  version   int not null default 1,
  sort      int not null,
  primary key (id, audience)
);

create table term_agreements (
  user_id    uuid not null references profiles(id) on delete cascade,
  term_id    text not null,
  version    int not null,
  agreed_at  timestamptz not null default now(),
  primary key (user_id, term_id)
);

create table notification_settings (                       -- 고객: push·marketing / 매니저: push·call·settlement
  user_id  uuid not null references profiles(id) on delete cascade,
  key      text not null,
  enabled  boolean not null default true,
  primary key (user_id, key)
);

create table phone_verifications (                        -- 휴대폰 인증번호 (Spring 이 만들고 검증. 코드 원문은 저장하지 않는다)
  id            uuid primary key default gen_random_uuid(),
  phone         text not null,                             -- E.164
  role          text not null check (role in ('customer','manager')),
  code_hash     text not null,                             -- SHA-256(code + server salt)
  expires_at    timestamptz not null,                      -- 발송 + 5분
  attempts      int not null default 0,                    -- 5회 틀리면 폐기
  verified_at   timestamptz,
  token         text unique,                               -- 검증 성공 시 발급하는 불투명 토큰 (가입 완료까지 15분)
  consumed_at   timestamptz,                               -- 가입에 쓰이면 채움 (재사용 금지)
  created_at    timestamptz not null default now()
);
create index phone_verifications_phone on phone_verifications(phone, created_at desc);

create table push_tokens (
  user_id    uuid not null references profiles(id) on delete cascade,
  platform   text not null check (platform in ('ios','android','web')),
  token      text not null,
  updated_at timestamptz not null default now(),
  primary key (user_id, token)
);

-- ── 고객 ──────────────────────────────────────────────────────────────
create table customer_housing (                            -- Auth/ProfileHousing
  customer_id   uuid primary key references profiles(id) on delete cascade,
  housing_type  text not null check (housing_type in ('apartment','studio','house','other')),
  size          text,                                      -- 평수 (자유 입력, sizeUnknown 이면 null)
  size_unknown  boolean not null default false,
  rooms         text,
  bathrooms     text,
  pet           text not null check (pet in ('yes','no')),
  updated_at    timestamptz not null default now()
);

create table addresses (
  id             uuid primary key default gen_random_uuid(),
  customer_id    uuid not null references profiles(id) on delete cascade,
  label          text not null default '기타',
  road_address   text not null,
  detail_address text,
  sigungu        text,                                     -- '강남구' (주소 검색 API 결과에서. 직접 입력이면 서버가 정규식으로 추출, 실패 시 null)
  sido           text,                                     -- '서울특별시'
  is_default     boolean not null default false,
  created_at     timestamptz not null default now()
);
create index addresses_customer on addresses(customer_id);
create unique index addresses_one_default on addresses(customer_id) where is_default;

-- ── 매니저 ────────────────────────────────────────────────────────────
create table manager_profiles (
  manager_id             uuid primary key references profiles(id) on delete cascade,
  approved               boolean not null default false,   -- 신원 확인·교육 완료 후 운영자가 true (콜을 받으려면 필요)
  rating                 numeric(2,1) not null default 0,
  completed_count        int not null default 0,
  active_since           date,
  areas                  text[] not null default '{}',     -- 활동 지역 '시도 시군구' 전체 문자열: '서울특별시 강남구', '부산광역시 해운대구' (구 이름만 쓰면 다른 도시의 '중구' 와 섞인다)
  sido                   text,                               -- 대체 매칭용 (예약의 sigungu 가 null 일 때 시/도만 비교)
  identity_verified_at   timestamptz,
  background_check_at    timestamptz,
  insurance_expires_on   date,
  education              jsonb not null default '[]',      -- [{ "label": "기본 청소 교육", "completedOn": "2024-03-01" }]
  bank_name              text,
  account_number_masked  text,                             -- 원본 계좌번호는 저장하지 않는다 (정산 대행사에 위임, §13)
  account_holder         text,
  updated_at             timestamptz not null default now()
);

create table manager_hours (                               -- 기본 근무시간. weekday 0=월 … 6=일
  manager_id  uuid not null references profiles(id) on delete cascade,
  weekday     smallint not null check (weekday between 0 and 6),
  start_time  time,
  end_time    time,
  off         boolean not null default false,
  primary key (manager_id, weekday)
);

create table manager_unavailable (                         -- 근무 불가 시간
  id          uuid primary key default gen_random_uuid(),
  manager_id  uuid not null references profiles(id) on delete cascade,
  date        date not null,
  all_day     boolean not null default false,
  start_time  time,
  end_time    time,
  repeat      text not null default 'none' check (repeat in ('none','weekly','monthly')),
  reason      text,
  created_at  timestamptz not null default now()
);
create index manager_unavailable_lookup on manager_unavailable(manager_id, date);

create table manager_devices (                             -- 바디캠 1대 = 매니저 1명. LiveKit Ingress 와 1:1
  manager_id     uuid primary key references profiles(id) on delete cascade,
  name           text not null,                            -- '안심캠 #AC-00231' (운영자가 등록)
  serial         text unique,                              -- 기기 일련번호
  ingress_id     text unique,                              -- LiveKit Ingress id (등록 시 생성, 재사용)
  stream_url     text,                                     -- rtmp://…/live  (기기에 한 번 설정)
  stream_key     text,                                     -- Ingress stream key. 앱 화면에는 마스킹해서 보여준다
  stream_status  text not null default 'offline' check (stream_status in ('offline','online')),  -- ingress_started/ended 웹훅
  last_online_at timestamptz,
  pending_room   text,                                     -- 활성 중이라 바꾸지 못한 방 이름. ingress_ended 때 적용
  last_report    jsonb,                                    -- { battery, network } 기기 API 가 있으면 (§13-19). 없으면 null
  reported_at    timestamptz
);

create table outbox_events (                               -- 외부 효과의 원장 (§2.4). 업무 트랜잭션과 같이 커밋된다
  id               uuid primary key default gen_random_uuid(),
  type             text not null,                          -- BookingAssigned · OffersDispatched · …
  payload          jsonb not null,
  status           text not null default 'pending' check (status in ('pending','sent','failed')),
  attempts         int not null default 0,
  next_attempt_at  timestamptz not null default now(),
  last_error       text,
  created_at       timestamptz not null default now(),
  sent_at          timestamptz
);
create index outbox_pending on outbox_events(status, next_attempt_at) where status = 'pending';

create table livekit_events (                              -- 웹훅 중복·순서 방어: 같은 event id 는 한 번만 처리
  id           text primary key,
  event        text not null,
  received_at  timestamptz not null default now()
);

-- ── 카탈로그 (참조 데이터, V002 에서 seed) ─────────────────────────────
create table service_categories (
  key        text primary key,                             -- home · office · appliance · move-in
  label      text not null,                                -- '가정/원룸 청소'
  promo      text not null check (promo in ('recurring','placeholder')),
  promo_text text,
  sort       int not null
);
create table catalog_prices (
  category_key text not null references service_categories(key),
  frequency    text not null check (frequency in ('recurring','oneTime')),
  base_amount  bigint not null,                            -- 원
  primary key (category_key, frequency)
);
create table catalog_options (
  category_key text not null references service_categories(key),
  key          text not null,
  kind         text not null check (kind in ('free','paid')),
  label        text not null,
  price        bigint not null default 0,
  icon         smallint not null,                          -- ASSETS.bookingOptionIcons[category][icon]
  sort         int not null,
  primary key (category_key, key)
);
create table catalog_notice_chips (
  category_key text not null references service_categories(key),
  key          text not null,
  label        text not null,
  icon         text not null,                              -- NoticeChip 아이콘 키
  sort         int not null,
  primary key (category_key, key)
);
create table catalog_frequency_copy (
  category_key text not null references service_categories(key),
  frequency    text not null,
  description  text not null,
  primary key (category_key, frequency)
);
create table checklist_templates (                         -- 작업 체크리스트 템플릿 (예약 배정 시 복사)
  category_key text not null references service_categories(key),
  group_name   text not null,
  label        text not null,
  sort         int not null,
  primary key (category_key, group_name, label)
);
create table catalog_reasons (                             -- 콜 거절·촬영 중단 사유 (Figma 문구)
  kind  text not null check (kind in ('reject','pause')),
  label text not null,
  sort  int not null,
  primary key (kind, label)
);
create table app_settings (                                -- 운영 값. 코드에 상수로 두지 않는다
  key   text primary key,                                  -- platform_fee_rate · offer_ttl_minutes · free_option_limit · time_slots · cancel_deadline_hours
  value jsonb not null
);

-- ── 정기청소 시리즈 (frequency=recurring 이면 매주 같은 요일·시각에 예약을 만든다, §6.8) ──
create table booking_series (
  id              uuid primary key default gen_random_uuid(),
  customer_id     uuid not null references profiles(id),
  category_key    text not null references service_categories(key),
  weekday         smallint not null check (weekday between 0 and 6),
  service_time    time not null,
  road_address    text not null,
  detail_address  text,
  free_options    text[] not null default '{}',
  paid_options    text[] not null default '{}',
  notices         text[] not null default '{}',
  request         text,
  status          text not null check (status in ('active','ended','cancelled')),
  term_start      date not null,                           -- 첫 방문일
  term_end        date not null,                           -- term_start + 1개월 − 1일 (정기청소 = 한 달 단위, §6.8)
  visits_planned  int not null,                            -- 4 또는 5
  ending_notified_at timestamptz,
  created_at      timestamptz not null default now(),
  cancelled_at    timestamptz
);
create index booking_series_active on booking_series(status, term_end);

-- ── 예약 = 작업 (고객이 만들고 매니저가 수행하는 같은 것) ────────────────
create table bookings (
  id              uuid primary key default gen_random_uuid(),
  series_id       uuid references booking_series(id),      -- 정기청소면 채움
  customer_id     uuid not null references profiles(id),
  manager_id      uuid references profiles(id),
  category_key    text not null references service_categories(key),
  frequency       text not null check (frequency in ('recurring','oneTime')),
  service_date    date not null,
  service_time    time not null,
  road_address    text not null,
  detail_address  text,
  sigungu         text,                                    -- 매칭용 (addresses.sigungu 와 같은 규칙)
  sido            text,
  free_options    text[] not null default '{}',
  paid_options    text[] not null default '{}',
  notices         text[] not null default '{}',
  request         text,
  repair_type     text,                                    -- Repair 화면에서 왔을 때 (appliance·furniture·plumbing …). 수리 전용 모델은 §13-24
  base_amount     bigint not null,
  options_amount  bigint not null default 0,
  total_amount    bigint not null,
  platform_fee    bigint not null,                         -- 생성 시점의 수수료율로 고정. offers.pay_amount = total_amount − platform_fee, 정산도 이 값
  duration_minutes int not null,                           -- 카테고리 기본 소요(app_settings.duration_hours) 스냅샷. 겹침 검사·일정 표시에 사용
  starts_at       timestamptz not null,                    -- service_date + service_time (Asia/Seoul) → 겹침 검사·취소 기한의 기준
  ends_at         timestamptz not null,                    -- starts_at + duration_minutes
  status          text not null check (status in ('requested','assigning','scheduled','in_progress','paused','done','cancelled')),
  payment_status  text not null default 'unpaid' check (payment_status in ('unpaid','authorized','paid','refunded')),  -- 결제 구조만 (PG 연동 전 항상 unpaid)
  assigned_at     timestamptz,
  started_at      timestamptz,
  ended_at        timestamptz,
  cancelled_at    timestamptz,
  cancel_reason   text,
  dispatch_round  int not null default 1,                  -- 오퍼 회차 (offers.round)
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);
create index bookings_customer on bookings(customer_id, status);
create index bookings_manager_date on bookings(manager_id, service_date);
create index bookings_status_date on bookings(status, service_date);
create unique index bookings_one_active_per_manager on bookings(manager_id) where status in ('in_progress','paused');
-- 같은 매니저에게 시간이 겹치는 예약 두 건이 배정될 수 없다 (동시 수락 경합의 최종 방어). btree_gist 필요
create extension if not exists btree_gist;
alter table bookings add constraint bookings_manager_no_overlap
  exclude using gist (manager_id with =, tstzrange(starts_at, ends_at) with &&)
  where (manager_id is not null and status in ('scheduled','in_progress','paused'));

create table booking_status_history (
  id          bigserial primary key,
  booking_id  uuid not null references bookings(id) on delete cascade,
  from_status text,
  to_status   text not null,
  actor_id    uuid,                                        -- null = 시스템(스케줄러)
  reason      text,
  at          timestamptz not null default now()
);
create index booking_status_history_booking on booking_status_history(booking_id);

create table offers (                                      -- 매니저에게 보낸 콜
  id            uuid primary key default gen_random_uuid(),
  booking_id    uuid not null references bookings(id) on delete cascade,
  manager_id    uuid not null references profiles(id),
  round         int not null default 1,                    -- 예약의 dispatch_round. 일정 변경·재매칭마다 +1 → 같은 매니저에게 다시 보낼 수 있다
  status        text not null check (status in ('pending','accepted','rejected','expired','cancelled','superseded')),  -- superseded: 일정 변경으로 무효가 된 accepted
  pay_amount    bigint not null,                           -- 매니저 수령 예정액 = total - platform_fee
  expires_at    timestamptz not null,
  responded_at  timestamptz,
  reject_reason text,
  created_at    timestamptz not null default now(),
  unique (booking_id, manager_id, round)
);
create index offers_manager_pending on offers(manager_id, status, expires_at);
create unique index offers_one_accepted on offers(booking_id) where status = 'accepted';

create table job_checklist_items (
  id          uuid primary key default gen_random_uuid(),
  booking_id  uuid not null references bookings(id) on delete cascade,
  group_name  text not null,
  label       text not null,
  done        boolean not null default false,
  done_at     timestamptz,
  sort        int not null
);
create index job_checklist_booking on job_checklist_items(booking_id);

create table job_pauses (
  id          uuid primary key default gen_random_uuid(),
  booking_id  uuid not null references bookings(id) on delete cascade,
  reason      text not null,
  paused_at   timestamptz not null default now(),
  resumed_at  timestamptz
);

create table live_sessions (
  booking_id        uuid primary key references bookings(id) on delete cascade,
  room_name         text not null unique,                  -- 'booking-<uuid>'
  status            text not null check (status in ('created','waiting_camera','live','paused','ended')),  -- waiting_camera: 시작 눌렀지만 바디캠 스트림이 아직 안 들어옴
  ingress_id        text,                                  -- 이 방에 묶인 바디캠 Ingress (manager_devices.ingress_id)
  camera_online_at  timestamptz,
  camera_track_sid  text,                                  -- mute 대상 (ingress_started 웹훅에서)
  mute_pending      boolean not null default false,        -- 카메라가 아직 안 붙었을 때 pause 를 누르면 true → 붙는 즉시 mute
  started_at        timestamptz,
  ended_at          timestamptz,
  egress_id         text,
  recording_status  text not null default 'none' check (recording_status in ('none','recording','uploading','ready','failed')),
  recording_path    text,                                  -- Storage 경로 recordings/<booking_id>.mp4
  duration_seconds  int,
  viewer_count      int not null default 0
);

create table settlements (
  id              uuid primary key default gen_random_uuid(),
  booking_id      uuid not null unique references bookings(id),
  manager_id      uuid not null references profiles(id),
  service_amount  bigint not null,
  platform_fee    bigint not null,
  other_fee       bigint not null default 0,
  final_amount    bigint not null,
  status          text not null check (status in ('pending','complete')),
  settled_at      timestamptz,
  created_at      timestamptz not null default now()
);
create index settlements_manager on settlements(manager_id, created_at desc);

-- ── 결제 (구조만. PG 연동은 사용자가 나중에 PaymentGateway 구현체로) ────
create table payment_methods (
  id           uuid primary key default gen_random_uuid(),
  customer_id  uuid not null references profiles(id) on delete cascade,
  provider     text not null,                              -- 'toss' | 'portone' … (PG 결정 후)
  billing_key  text not null,                              -- PG 가 준 빌링키 (카드번호는 절대 저장하지 않는다)
  label        text not null,                              -- '신한카드 1234'
  is_default   boolean not null default false,
  created_at   timestamptz not null default now()
);
create index payment_methods_customer on payment_methods(customer_id);

create table payments (
  id                 uuid primary key default gen_random_uuid(),
  booking_id         uuid not null references bookings(id),
  payment_method_id  uuid references payment_methods(id),
  provider           text not null,
  provider_tx_id     text unique,                          -- PG 거래 id
  amount             bigint not null,
  status             text not null check (status in ('pending','authorized','paid','failed','refunded')),
  failure_reason     text,
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now()
);
create index payments_booking on payments(booking_id);

-- ── 지원 ──────────────────────────────────────────────────────────────
create table notices (
  id           uuid primary key default gen_random_uuid(),
  audience     text not null check (audience in ('customer','manager','all')),
  title        text not null,
  body         text not null,
  published_at timestamptz not null default now()
);
create table faqs (
  id        uuid primary key default gen_random_uuid(),
  audience  text not null check (audience in ('customer','manager')),
  question  text not null,
  answer    text not null,
  sort      int not null
);
create table inquiries (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references profiles(id) on delete cascade,
  content     text not null,
  status      text not null default 'open' check (status in ('open','answered')),
  answer      text,
  created_at  timestamptz not null default now(),
  answered_at timestamptz
);

-- ── RLS: 전부 켜고 정책 없음 (앱 키로는 아무것도 못 읽는다) ──────────────
do $$ declare t text; begin
  for t in select tablename from pg_tables where schemaname = 'public' and tablename <> 'flyway_schema_history' loop
    execute format('alter table %I enable row level security', t);
  end loop;
end $$;

-- updated_at 자동 갱신
create or replace function set_updated_at() returns trigger language plpgsql as $$
begin new.updated_at = now(); return new; end $$;
do $$ declare t text; begin
  for t in select tablename from pg_tables where schemaname='public' and tablename in ('profiles','customer_housing','manager_profiles','bookings') loop
    execute format('create trigger %I_updated_at before update on %I for each row execute function set_updated_at()', t, t);
  end loop;
end $$;
