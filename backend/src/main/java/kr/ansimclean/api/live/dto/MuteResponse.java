package kr.ansimclean.api.live.dto;

/** POST /live/sessions/{id}/pause · /resume — 막을(풀) 영상 트랙이 있으면 확인된 뒤에만 200 (§5.4) */
// 만드는 곳: LiveService.pause/resume. 확인하지 못하면 이 응답 대신 502(LIVE_PAUSE_UNCONFIRMED).
// 세션이 없거나 카메라 트랙이 없으면(pause 는 LiveKit 에 한 번 더 묻는다) 확인할 것 없이 200 이다 — 이때 pause 는
// 세션에 mute 요청을 남기고 Ingress 잠금을 시도한다. 작업이 일시정지된 뒤 카메라가 붙으면 그 순간 막고,
// 작업이 진행 중인 채 붙으면 요청은 버려진다(LiveService.cameraAttached).
// status = 처리 뒤 세션 상태(세션이 없으면 "created"), muted = 요청한 쪽(pause 면 true, resume 이면 false) — 실제로 막혔는지는 status 로 본다
public record MuteResponse(String status, boolean muted) {}
