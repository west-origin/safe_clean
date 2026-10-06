-- 지금 매칭 구간이 시작된 회차 — 일정 변경·매니저 방문 취소 때 새로 시작한다 (자체 점검 후속 검토).
-- 자동 재매칭(MatchingRetry)은 '이 예약의 콜을 받은 적 없는 매니저' 를 이 회차 이후의 오퍼로만 센다 — 옛 일정의 콜이나 다른 매니저 수락으로 닫힌 콜을 받은 매니저까지 영영 빠졌다.
-- 기존 행은 1(처음부터 센다 = 이 열 전과 같은 동작)
alter table bookings add column matching_from_round int not null default 1;
