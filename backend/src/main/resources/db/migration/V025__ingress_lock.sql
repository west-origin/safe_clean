-- V025__ingress_lock.sql — 일시정지 중 바디캠 재접속 차단 (감사 H-02).
-- 일시정지 중 카메라가 끊겼다 다시 붙으면 새 트랙이 mute 되지 않은 채 올라와 서버가 사후에 mute 할 때까지 영상·녹화가 나갔다.
-- 카메라가 붙어 있지 않은 동안(Ingress 비활성)만 LiveKit Ingress 를 enabled=false 로 잠가 재접속 자체를 거부시킨다
-- (LiveKit: 비활성 상태에서만 수정 가능, enabled=false 면 새 연결을 거부). 재개·작업 종료·다음 작업 시작 때 다시 연다.
alter table manager_devices add column if not exists ingress_enabled boolean not null default true;
