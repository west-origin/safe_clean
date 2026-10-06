package kr.ansimclean.api.live.dto;

/** POST /live/sessions/{id}/token { role: "viewer" } */
// 고객 앱 getLiveViewerToken 이 { role: 'viewer' } 를 보낸다. 지금 서버는 이 값을 읽지 않는다 —
// LiveController.token 은 본문을 받기만 하고(없어도 됨) LiveService.viewerToken 에 넘기지 않는다
public record TokenRequest(String role) {}
