package kr.ansimclean.api.live.dto;

/** POST /live/sessions/{id}/start (§5.4). cameraBusy = 바디캠이 대기 방에 송출 중이라 방을 못 옮겼다 → 앱 안내 "바디캠을 껐다가 다시 켜주세요" */
// 만드는 곳: LiveService.start. 받는 곳: 매니저 앱 api/manager.js 의 startLiveSession.
// roomName = 이 작업의 LiveKit 방, status = 세션 상태(처음이면 waiting_camera, 이미 진행 중이면 그때 상태),
// cameraOnline = 바디캠이 이미 방에 붙어 있음
public record StartLiveResponse(String roomName, String status, boolean cameraOnline, boolean cameraBusy, Preview preview) {
    /** 매니저가 자기 바디캠 화면을 보는 시청 토큰 (TTL 6시간) */
    public record Preview(String url, String token) {} // url = LiveKit 접속 주소, token = 이 방 시청 토큰
}
