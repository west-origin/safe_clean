package kr.ansimclean.api.live.dto;

/** GET /live/sessions/{id} — 앱이 3초마다. status = created|waiting_camera|live|paused|ended */
// 만드는 곳: LiveService.status. 받는 곳: 매니저 앱 api/manager.js 의 getLiveSessionStatus (live/liveClient.js 가 3초마다 부른다).
// 예약의 고객도 부를 수 있다(LiveService.status 는 고객·배정 매니저만 허용).
// cameraOnline = 바디캠이 방에 붙어 있음, muted = status 가 paused 인지(단, 재개 확인 실패(unmute_pending)도 paused 로 남아
// 그때는 영상이 이미 나가고 있을 수 있다), cameraBusy = 바디캠이 다른 방에서 송출 중이라
// 이 방으로 옮기기가 미뤄짐, viewers = 보고 있는 고객 수, startedAt = 라이브 시작 시각(ISO 문자열, 없으면 null),
// held = 매니저가 영상을 멈추고 아직 재개하지 않음(video_held). 작업이 진행 중인데 held 면 매니저 앱이 '작업 상태를 바꾸지 못했어요' 시트를 다시 띄운다
// source = 영상 출처 'app'(매니저 휴대폰 송출, §8.0) | 'ingress'(바디캠). 매니저 앱이 청소 준비 화면에서 휴대폰 카메라 미리보기를 켤지 정한다
public record LiveStatusResponse(String status, boolean cameraOnline, boolean muted, boolean cameraBusy, int viewers, String startedAt, boolean held, String source) {}
