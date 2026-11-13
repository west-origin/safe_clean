-- V068__manager_cancel_requests.sql — 매니저 방문 취소 승인·재배정 (2026-11-13 사용자 결정, docs/domain.md 같은 이름의 절)
--
-- 매니저는 방문을 직접 취소하지 못하고 취소를 요청한다(manager_cancel_requests). 어드민이 승인하면 배정이 풀리고
-- 24시간(reassign_window_hours) 동안 근처 매니저 전원에게 재배정 콜(offers.reassign)을 한 번 보낸다.
-- 아무도 맡지 않으면 예약에 '관리자 배정 대기' 표시(bookings.admin_assign_pending_at)를 남기고 어드민이 직접 배정한다.
-- 상태값(bookings.status)은 늘리지 않는다 — assigning 그대로 두고 표시 컬럼으로 구분한다(사용자 결정).

-- ① 취소 요청. 매니저가 만들고 어드민이 승인·거절한다. 승인 전에 작업이 시작되거나 고객이 취소·일정 변경하면 closed
create table manager_cancel_requests (
  id            uuid primary key default gen_random_uuid(),
  booking_id    uuid not null references bookings(id) on delete cascade,
  manager_id    uuid not null references profiles(id),
  reason        text not null,                        -- app_settings.manager_cancel_reasons 의 key
  detail        text,                                 -- 매니저가 적은 상세(선택, 500자까지 — 서버 검사)
  status        text not null default 'pending' check (status in ('pending','approved','rejected','closed')),
  requested_at  timestamptz not null,
  decided_at    timestamptz,                          -- 승인·거절·자동 종료 시각
  decided_by    uuid references profiles(id),         -- 승인·거절한 어드민 (자동 종료는 null)
  decision_note text,                                 -- 거절 메모(선택) 또는 자동 종료 사유(job_started·booking_cancelled·rescheduled)
  created_at    timestamptz not null default now()
);
-- 예약당 대기 요청은 하나 — 같은 취소를 두 번 요청해도 승인 흐름이 두 번 돌지 않게
create unique index manager_cancel_requests_one_pending on manager_cancel_requests(booking_id) where status = 'pending';
create index manager_cancel_requests_pending on manager_cancel_requests(requested_at) where status = 'pending';
create index manager_cancel_requests_booking on manager_cancel_requests(booking_id);

alter table manager_cancel_requests enable row level security; -- 정책 0개 = 앱 키로는 아무것도 못 한다 (서버만)
comment on table manager_cancel_requests is '매니저 방문 취소 요청. 어드민 승인 뒤 재배정 (docs/domain.md 매니저 방문 취소 승인·재배정)';

-- ② 예약의 재배정 상태
alter table bookings
  add column reassign_request_id     uuid references manager_cancel_requests(id), -- 재배정을 시작한 승인 요청 (추적용)
  add column reassign_until          timestamptz,                                 -- 재배정 콜 마감. null = 재배정 중이 아니다
  add column admin_assign_pending_at timestamptz,                                 -- 관리자 배정 대기 시작 시각. null = 대기 아님
  add column admin_assign_reason     text check (admin_assign_reason in ('no_accept','imminent','no_candidates'));
create index bookings_admin_assign_pending on bookings(starts_at) where admin_assign_pending_at is not null;

-- ③ 재배정 콜 표시 — 매니저 콜 카드·푸시에서 '재배정 건' 으로 구분한다
alter table offers add column reassign boolean not null default false;

-- ④ 승인된 취소는 0점 원장 행으로 남긴다 (이 예약·이 정기청소 우선 콜에서 그 매니저를 빼는 기준)
alter table manager_penalties drop constraint manager_penalties_category_check;
alter table manager_penalties add constraint manager_penalties_category_check
  check (category in ('cancel_early','cancel_late','cancel_approved','no_show','tardy','recovery'));

-- ⑤ 재배정 콜을 열어 두는 시간(시간). 방문까지 이보다 적게 남았으면 콜 없이 바로 관리자 배정 대기
insert into app_settings (key, value) values
  (E'reassign_window_hours', '24'::jsonb)
on conflict (key) do nothing;
