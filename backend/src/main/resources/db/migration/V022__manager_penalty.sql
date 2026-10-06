-- V022__manager_penalty.sql — 매니저 방문 취소·미방문 정책 (사용자 승인 2026-09-22, docs/manager-policy.md).
-- 점수는 원장(manager_penalties)의 합으로 구한다 — 별도 집계 열을 두지 않는다(드리프트 방지).
-- 회복은 음수 점수 행(category='recovery')으로 남겨 이력이 append-only 로 유지된다.

alter table manager_profiles add column if not exists suspended_until date;

create table manager_penalties (
  id           uuid primary key default gen_random_uuid(),
  manager_id   uuid not null references profiles(id),
  booking_id   uuid references bookings(id),          -- 회복 행은 null
  series_id    uuid references booking_series(id),    -- 정기청소 회차였으면 그 시리즈 (우선 배정 제외에 쓴다)
  category     text not null check (category in ('cancel_early','cancel_late','no_show','tardy','recovery')),
  points       numeric(3,1) not null,                 -- 회복은 음수
  reason       text,                                  -- 매니저가 고른 사유 코드
  detail       text,                                  -- 직접 입력
  status       text not null default 'active' check (status in ('active','expired','waived')),
  created_at   timestamptz not null default now(),
  expires_at   timestamptz not null,                  -- 12개월 롤링
  waived_at    timestamptz,
  waived_note  text
);
create index manager_penalties_active on manager_penalties(manager_id, status, expires_at);
create index manager_penalties_series on manager_penalties(manager_id, series_id) where series_id is not null;

alter table manager_penalties enable row level security;  -- V001 과 같게 deny-all (서버만 service_role 로 접근)

-- 정책 숫자는 설정으로 (운영 중 바꿀 수 있게)
insert into app_settings (key, value) values
  (E'manager_penalty', '{
     "freeCancelsPerMonth": 2,
     "points": {"cancel_early": 1, "cancel_late": 1, "no_show": 3, "tardy": 0.5},
     "cancelCutoffHours": 4,
     "lateCancelHours": 24,
     "tardyMinutes": 30,
     "expireMonths": 12,
     "recoveryMonths": 3,
     "thresholds": [
       {"points": 3, "action": "warn", "days": 0},
       {"points": 5, "action": "suspend", "days": 7},
       {"points": 8, "action": "suspend", "days": 30},
       {"points": 10, "action": "review", "days": 0}
     ]
   }'::jsonb),
  (E'manager_cancel_reasons', '[
     {"key":"sick","label":"질병·부상"},
     {"key":"accident","label":"교통사고·차량 문제"},
     {"key":"equipment","label":"장비 고장"},
     {"key":"personal","label":"개인 사정"},
     {"key":"other","label":"기타"}
   ]'::jsonb)
on conflict (key) do nothing;

-- MY 메뉴에 '패널티 내역' 추가 (촬영 장비 다음)
update app_settings
set value = '[{"key":"info","label":"매니저 정보"},{"key":"area","label":"활동 지역"},{"key":"availability","label":"근무 일정 관리","to":"/schedule/availability"},{"key":"identity","label":"신원 인증"},{"key":"education","label":"교육 이수"},{"key":"account","label":"정산 계좌"},{"key":"equipment","label":"촬영 장비","to":"/profile/equipment"},{"key":"penalties","label":"패널티 내역","to":"/profile/penalties"},{"key":"notifications","label":"알림 설정"},{"key":"notice","label":"공지사항"},{"key":"support","label":"고객센터"}]'::jsonb
where key = 'manager_profile_menu';
