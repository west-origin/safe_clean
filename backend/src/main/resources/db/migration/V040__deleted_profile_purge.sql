-- 탈퇴 + 5년 뒤 남은 개인정보 파기 (BACKEND_PLAN §13-22 결정 5년 — 구현은 사용자 결정 2026-09-27).
-- 예약·정산·결제 행은 상대방(매니저·고객) 이력과 법정 기록이라 지우지 않고 개인정보 칸만 비운다. 한 번 한 사용자는 purged_at 으로 다시 보지 않는다
alter table profiles add column purged_at timestamptz;
create index profiles_purge_due_idx on profiles (deleted_at) where deleted_at is not null and purged_at is null;
