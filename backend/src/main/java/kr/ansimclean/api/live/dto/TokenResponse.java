package kr.ansimclean.api.live.dto;

// POST /live/sessions/{id}/token 응답 (LiveService.viewerToken). 고객이 라이브를 보려고 LiveKit 방에 접속할 때 쓴다.
// url = LiveKit 접속 주소, token = 구독만 되는 시청 토큰(유효 시간 LiveService.VIEWER_TTL), roomName = 들어갈 방
public record TokenResponse(String url, String token, String roomName) {}
