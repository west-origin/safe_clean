package kr.ansimclean.api.live.dto;

/** GET /live/sessions/{id}/recording — 서명 URL (유효 시간은 LiveService.RECORDING_URL_TTL_SECONDS) */
// 만드는 곳: LiveService.recording. url = 녹화본 파일을 잠시 열 수 있는 주소, durationSeconds = 녹화 길이(초, 모르면 0).
// 받는 곳: 고객 앱 api/customer.js 의 getRecording
public record RecordingResponse(String url, long durationSeconds) {}
