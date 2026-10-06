-- 일시정지 동안 이미 방에 들어와 있는 고객의 구독 권한을 막았는가 (재감사 H-02, 2026-09-24).
-- 바디캠이 일시정지 중 다시 붙으면 새 트랙은 mute 되기 전까지 구독자에게 흘러갈 수 있다 → 일시정지 동안 고객 참가자의 canSubscribe 를 끈다.
-- 재조정기가 이 값과 '막아야 하는가' 가 다를 때만 LiveKit 을 부른다(매분 전원에게 부르지 않게)
alter table live_sessions add column viewers_blocked boolean not null default false;
