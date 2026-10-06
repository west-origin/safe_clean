package kr.ansimclean.api.live;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import static kr.ansimclean.api.common.security.SecurityConfig.API;

/** POST /live/webhooks/livekit — LiveKit 이 부른다. 서명(Authorization JWT)으로 검증, 실패 401, 처리 204 (§5.4) */
// 실제 경로는 API("/api/v1") + "/live/webhooks/livekit".
// 이 경로는 SecurityConfig 에서 로그인 없이 열려 있다(permitAll) — 앱 사용자가 아니라 LiveKit 서버가 부르고, 대신 서명으로 확인한다.
// 흐름: 서명 검증·파싱(LiveKitGateway.parseWebhook) → LiveWebhookService.handle. logging 모드에서는 parseWebhook 이 늘 401 을 던진다
@RestController
public class LiveWebhookController {
    private final LiveKitGateway livekit;
    private final LiveWebhookService service;

    public LiveWebhookController(LiveKitGateway livekit, LiveWebhookService service) {
        this.livekit = livekit;
        this.service = service;
    }

    // RequestBody String = 본문을 JSON 으로 풀지 않고 문자열 그대로 받아 게이트웨이에 넘긴다 (cloud 모드는 LiveKit SDK 가 서명 확인과 해석을 함께 한다).
    // Authorization 헤더가 없으면 빈 문자열로 넘겨 parseWebhook 이 검증 실패(401)로 처리하게 한다.
    // 이미 처리한 이벤트여도(handle 이 false) 똑같이 204 를 돌려준다. handle 이 예외로 끝나면 오류 응답이고 중복 방어 기록도 남지 않는다
    @PostMapping(API + "/live/webhooks/livekit")
    public ResponseEntity<Void> livekit(@RequestBody String body, @RequestHeader(value = "Authorization", required = false) String authorization) {
        service.handle(livekit.parseWebhook(body, authorization == null ? "" : authorization));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
