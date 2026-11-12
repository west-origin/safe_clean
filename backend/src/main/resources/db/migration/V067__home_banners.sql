-- V067__home_banners.sql — 고객 앱 홈 혜택 배너 (배너·공지 관리 4회차, rbac.md §15-22)
--
-- 어드민이 매니저 앱 '배너 관리' 에서 이미지 + 대체 문구(제목·설명) + 게시 기간 + 순서로 등록한다.
-- 고객 앱 홈(HomePromotions)은 GET /customer/banners 로 '오늘(서울) 게시 기간 안' 인 배너를 순서대로 받는다.
-- 이미지는 비공개 Storage 버킷 banners 의 image_path 에 있고, 서버가 짧은 서명 URL 로만 준다(rbac.md §1-8).
-- 이미지를 바꾸거나 배너를 지우면 옛 파일은 아웃박스(BannerImageDeleted)가 지운다.
-- 버킷은 이 마이그레이션이 만들지 않는다 — runbook.md 의 SQL 로 dev·prod 에 만든다(다른 버킷과 같은 방식).

create table home_banners (
  id           uuid primary key default gen_random_uuid(),
  title        text not null,                 -- 대체 문구 제목 (이미지 안 글자와 같게, 화면낭독기가 읽는다)
  description  text not null default '',      -- 대체 문구 설명 (없어도 된다)
  image_path   text not null,                 -- banners 버킷 안 경로 (<id>/<파일 uuid>.<확장자>, 바꿀 때마다 새 경로)
  starts_on    date,                          -- 게시 시작일(서울, 이 날 포함). null = 바로
  ends_on      date,                          -- 게시 종료일(서울, 이 날 포함). null = 끝없음
  sort_order   int not null,                  -- 작을수록 먼저 (1부터)
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  constraint home_banners_period check (starts_on is null or ends_on is null or starts_on <= ends_on)
);
create index home_banners_order on home_banners (sort_order);

alter table home_banners enable row level security; -- 정책 0개 = 앱 키로는 아무것도 못 한다 (서버만)

comment on table home_banners is '고객 앱 홈 혜택 배너. 어드민이 매니저 앱에서 관리(rbac.md §15-22). 이미지는 비공개 버킷 banners';
