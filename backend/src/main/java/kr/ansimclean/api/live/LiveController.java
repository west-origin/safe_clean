package kr.ansimclean.api.live;

import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.live.dto.CustomerLiveResponse;
import kr.ansimclean.api.live.dto.LiveStatusResponse;
import kr.ansimclean.api.live.dto.MuteResponse;
import kr.ansimclean.api.live.dto.OkResponse;
import kr.ansimclean.api.live.dto.RecordingResponse;
import kr.ansimclean.api.live.dto.StartLiveResponse;
import kr.ansimclean.api.live.dto.TokenRequest;
import kr.ansimclean.api.live.dto.TokenResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 라이브 API (docs/api.md §5.4 + 고객 §5.2 getLiveSession). /live/** 는 로그인만 요구하고 소유권은 서비스가 본다 */
// 경로 {id} 는 예약(작업) id 다 — 라이브 세션은 예약 하나에 하나(live_sessions 의 키가 예약 id).
// 부르는 곳 예: 매니저 앱 api/manager.js 의 pauseLiveSession, 고객 앱 api/customer.js 의 getLiveViewerToken.
// 규칙(상태 검사·mute 확인·재시도 표시)은 LiveService 에 있다. 경로 앞머리는 SecurityConfig.API(/api/v1)
@RestController
@RequestMapping(SecurityConfig.API)
public class LiveController {
    private final LiveService service;

    public LiveController(LiveService service) {
        this.service = service;
    }

    /**
     * 매니저: 청소 시작 때 방을 만들고(이미 열려 있으면 그대로) 바디캠을 그 방에 묶는다 → 방 이름·상태·미리보기 토큰.
     * 인자의 {@code @AuthenticationPrincipal} 은 로그인 토큰으로 찾은 현재 사용자
     */
    @PostMapping("/live/sessions/{id}/start")
    public StartLiveResponse start(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return service.start(user.id(), id);
    }

    /** 세션 상태(카메라 연결·일시정지·시청자 수). 매니저 앱이 폴링한다. 예약의 고객·배정 매니저만, 그 외는 404 */
    @GetMapping("/live/sessions/{id}")
    public LiveStatusResponse status(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return service.status(user.id(), id);
    }

    /**
     * 매니저: 일시정지 ① 영상 차단. 서버가 mute 를 확인해야 200, 확인 못 하면 LIVE_PAUSE_UNCONFIRMED(502). 작업 상태는 jobs/pause 가 따로 바꾼다.
     * 예외: 카메라가 안 붙어 있으면(LiveKit 에 한 번 물어 본다) 막을 영상이 없어 확인 없이 200(jobs/pause 뒤에 붙으면 그 순간 막는다).
     * 502 여도 mute_pending 표시는 저장된다 — LiveService.pause 가 noRollbackFor 라서다. 502 는 '계속 전송' 안내라 막아 둔 표시(video_held)는
     * 지우고, 작업이 진행 중인 채로 남으면 재조정기는 이 요청을 다시 시도하지 않고 버린다(LiveService 클래스 설명).
     * 200 이면 매니저가 재개할 때까지 막아 둔다 — jobs/pause 가 실패하고 바디캠이 다시 붙어도
     */
    @PostMapping("/live/sessions/{id}/pause")
    public MuteResponse pause(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return service.pause(user.id(), id);
    }

    /**
     * 매니저: 영상 재개. 작업이 아직 일시정지(paused)면 INVALID_STATE — 작업 재개(jobs/resume)가 먼저다.
     * unmute 를 확인 못 하면 502 지만 unmute_pending 은 저장돼(noRollbackFor) 재조정기가 이어서 확인한다
     */
    @PostMapping("/live/sessions/{id}/resume")
    public MuteResponse resume(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return service.resume(user.id(), id);
    }

    /** 매니저: 작업 완료(또는 취소) 뒤 녹화 정지·방 삭제·바디캠을 대기 방으로. 작업이 아직 끝나지 않았으면 INVALID_STATE */
    @PostMapping("/live/sessions/{id}/stop")
    public OkResponse stop(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return service.stop(user.id(), id);
    }

    /**
     * 고객: 시청 전용 LiveKit 토큰. 작업이 진행 중이고 영상이 일시정지가 아니며(매니저가 멈춘 채도 아니며) mute·unmute 확인 대기도 없을 때만, 아니면 LIVE_NOT_AVAILABLE.
     * 본문 { role: "viewer" } 는 받기만 하고 쓰지 않는다 — 언제나 시청 토큰이다 (required = false: 본문이 없어도 된다)
     */
    @PostMapping("/live/sessions/{id}/token")
    public TokenResponse token(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id, @RequestBody(required = false) TokenRequest req) {
        return service.viewerToken(user.id(), id);
    }

    /**
     * 고객: 녹화본 서명 URL(유효 시간은 LiveService.RECORDING_URL_TTL_SECONDS)과 길이.
     * 아직 준비 전이면 RECORDING_NOT_READY (고객 앱 getRecording 은 이때 null 로 바꾼다)
     */
    @GetMapping("/live/sessions/{id}/recording")
    public RecordingResponse recording(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return service.recording(user.id(), id);
    }

    /** 고객: 라이브 화면 한 번에 그릴 값(매니저 소개·시작 시각·시청자 수·상태 문구·녹화본 상태). /customer/** 라 고객 역할만 들어온다 */
    @GetMapping("/customer/bookings/{id}/live")
    public CustomerLiveResponse customerLive(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return service.customerLive(user.id(), id);
    }
}
