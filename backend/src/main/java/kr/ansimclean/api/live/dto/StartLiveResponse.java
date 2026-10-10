package kr.ansimclean.api.live.dto;

/** POST /live/sessions/{id}/start (§5.4). cameraBusy = 바디캠이 대기 방에 송출 중이라 방을 못 옮겼다 → 앱 안내 "바디캠을 껐다가 다시 켜주세요" */
// 만드는 곳: LiveService.start. 받는 곳: 매니저 앱 api/manager.js 의 startLiveSession.
// roomName = 이 작업의 LiveKit 방, status = 세션 상태(처음이면 waiting_camera, 이미 진행 중이면 그때 상태),
// cameraOnline = 바디캠이 이미 방에 붙어 있음
// preview = 바디캠 모드(ingress)에서만, publish = 휴대폰 송출 모드(app, §8.0)에서만 — 다른 쪽은 null
public record StartLiveResponse(String roomName, String status, boolean cameraOnline, boolean cameraBusy, Preview preview, Publish publish) {
    /** 매니저가 자기 바디캠 화면을 보는 시청 토큰 (TTL 6시간) */
    public record Preview(String url, String token) {} // url = LiveKit 접속 주소, token = 이 방 시청 토큰

    /** 매니저 앱이 휴대폰 카메라 영상을 올리는 송출 토큰 (identity camera-매니저id, 카메라만 publish, TTL 6시간) */
    public record Publish(String url, String token) {}

    /** 바디캠 모드 응답 (publish 없음) */
    public StartLiveResponse(String roomName, String status, boolean cameraOnline, boolean cameraBusy, Preview preview) {
        this(roomName, status, cameraOnline, cameraBusy, preview, null);
    }
}
