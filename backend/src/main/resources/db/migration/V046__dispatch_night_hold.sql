-- 밤(서울 21시~08시)에 생긴 새 콜을 아침 첫 자동 재매칭(08:05)까지 미룬다 (문제 목록 2번, 사용자 결정 ③ 2026-09-28).
-- 매니저 카드가 '근무 시간 외' 인 밤에도 콜·푸시가 가고, 밤 콜이 30분 만에 만료돼 매칭 실패로 끝나기 쉬웠다.
-- dispatch_held_at            = 보류한 시각. null 이면 보류 없음 (아침에 OfferService.releaseHeldDispatches 가 보내고 지운다)
-- dispatch_held_manager_id    = 아침에 먼저 우선 콜을 줄 매니저 (정기청소 직전 매니저·일정 변경 전 매니저). null 이면 후보 전원에게 일반 콜
-- dispatch_held_rescheduled   = 그 매니저가 일정 변경 전 이 회차를 맡았던 사람인가 — 아침에 '예약 일정이 변경됐어요' 푸시를 그때 보낸다
alter table bookings
  add column dispatch_held_at timestamptz,
  add column dispatch_held_manager_id uuid references profiles(id) on delete set null,
  add column dispatch_held_rescheduled boolean not null default false;
create index bookings_dispatch_held_idx on bookings (id) where dispatch_held_at is not null;
