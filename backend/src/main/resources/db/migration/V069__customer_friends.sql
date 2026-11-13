-- V069__customer_friends.sql — 고객 친구 (2026-11-13 사용자 결정, docs/domain.md '고객 친구·금액권 선물')
--
-- 친구는 '닉네임#태그'(태그 = 숫자 4자리, 서버가 붙인다)로 찾는다. 전화번호 검색은 없다.
-- 같은 두 사람에 대한 쓰기는 서버가 두 사람 쌍의 advisory lock 으로 줄을 세운다(FriendService). 아래 제약은 마지막 방어다.

-- ① 친구 태그. 닉네임+태그 조합은 살아 있는 계정 사이에서 유일(대소문자 무시)
alter table profiles add column friend_tag text check (friend_tag ~ '^[0-9]{4}$');
create unique index profiles_friend_handle on profiles(lower(nickname), friend_tag)
  where deleted_at is null and friend_tag is not null and nickname is not null;

-- 이미 닉네임이 있는 고객에게 태그를 붙인다. 같은 닉네임끼리는 (닉네임 해시 + 순번 × 7919) mod 10000 이라 겹치지 않는다
-- (7919 는 10000 과 서로소 — 한 닉네임에 10000명까지 서로 다른 값)
with t as (
  select id,
         row_number() over (partition by lower(nickname) order by id) as rn,
         abs(hashtext(lower(nickname))::bigint) % 10000 as base
  from profiles
  where role = 'customer' and deleted_at is null and nickname is not null and btrim(nickname) <> ''
)
update profiles p set friend_tag = lpad(((t.base + t.rn * 7919) % 10000)::text, 4, '0')
from t where p.id = t.id;

-- ② 친구 요청 (이력도 남는다). source = search(닉네임#태그로 찾음) | invite(초대 링크)
create table friend_requests (
  id            uuid primary key default gen_random_uuid(),
  requester_id  uuid not null references profiles(id),
  addressee_id  uuid not null references profiles(id),
  status        text not null default 'pending' check (status in ('pending','accepted','rejected','cancelled')),
  source        text not null default 'search' check (source in ('search','invite')),
  created_at    timestamptz not null default now(),
  decided_at    timestamptz,
  check (requester_id <> addressee_id)
);
-- 두 사람 사이 대기 요청은 방향과 상관없이 하나 (서로 동시에 보내도 하나만 — 뒤에 온 쪽은 자동 친구가 된다)
create unique index friend_requests_one_pending on friend_requests(least(requester_id, addressee_id), greatest(requester_id, addressee_id))
  where status = 'pending';
create index friend_requests_addressee on friend_requests(addressee_id, created_at desc) where status = 'pending';
create index friend_requests_requester on friend_requests(requester_id, created_at desc) where status = 'pending';

-- ③ 친구 관계. 한 쌍에 한 행 — user_a 가 작은 id
create table friendships (
  user_a      uuid not null references profiles(id),
  user_b      uuid not null references profiles(id),
  created_at  timestamptz not null default now(),
  primary key (user_a, user_b),
  check (user_a < user_b)
);
create index friendships_user_b on friendships(user_b);

-- ④ 차단 (한 방향). blocker 가 blocked 를 막는다
create table friend_blocks (
  blocker_id  uuid not null references profiles(id),
  blocked_id  uuid not null references profiles(id),
  created_at  timestamptz not null default now(),
  primary key (blocker_id, blocked_id),
  check (blocker_id <> blocked_id)
);
create index friend_blocks_blocked on friend_blocks(blocked_id);

-- ⑤ 초대 코드 (카카오톡 등으로 공유하는 링크 https://<API>/i/{code}). 고객당 살아 있는 코드 하나, 30일, 여러 번 쓸 수 있다
create table friend_invites (
  code        text primary key check (code ~ '^[A-Za-z0-9]{10}$'),
  inviter_id  uuid not null references profiles(id),
  created_at  timestamptz not null default now(),
  expires_at  timestamptz not null
);
create index friend_invites_inviter on friend_invites(inviter_id, expires_at desc);

alter table friend_requests enable row level security; -- 정책 0개 = 앱 키로는 아무것도 못 한다 (서버만)
alter table friendships enable row level security;
alter table friend_blocks enable row level security;
alter table friend_invites enable row level security;
-- 앱 키(anon·authenticated) 권한은 V064 의 기본 권한 회수로 처음부터 없다
comment on table friend_requests is '고객 친구 요청과 이력 (docs/domain.md 고객 친구·금액권 선물)';
comment on table friendships is '고객 친구 관계. 한 쌍 한 행(user_a < user_b)';
comment on table friend_blocks is '고객 차단. 차단된 사람은 검색·요청·초대 수락·선물을 못 한다';
comment on table friend_invites is '친구 초대 코드. GET /i/{code} 초대 페이지';
