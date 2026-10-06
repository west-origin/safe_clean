package kr.ansimclean.api.push;

import jakarta.validation.Valid;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.push.dto.OkResponse;
import kr.ansimclean.api.push.dto.RegisterTokenRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 기기 푸시 토큰 등록·해제 API. 경로는 /api/v1/push/token (SecurityConfig.API + 아래 경로).
// 부르는 곳 예: 두 앱 api 파일의 registerPushToken(PUT, push/pushClient.js 가 부른다), logout(DELETE 를 먼저 보낸다).
// SecurityConfig 에서 /push/** 는 역할과 상관없이 로그인만 되어 있으면 통과한다.
/** docs/api.md §5.5 `push.registerToken` — 두 앱 공통, 로그인한 사용자 */
@RestController
@RequestMapping(SecurityConfig.API + "/push")
public class PushController {
    private final PushService service;

    public PushController(PushService service) {
        this.service = service;
    }

    // PUT /api/v1/push/token — 이 사용자에게 이 기기 토큰을 등록한다. 응답 { ok: true }
    // @AuthenticationPrincipal = 로그인 토큰에서 꺼낸 현재 사용자. 본문이 RegisterTokenRequest 의 검사(@NotBlank 등)에 걸리면
    // GlobalExceptionHandler 가 400 VALIDATION 으로 응답한다. platform 값 검사는 PushService.register 가 한다
    @PutMapping("/token")
    public OkResponse register(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody RegisterTokenRequest req) {
        service.register(user.id(), req.platform(), req.token());
        return OkResponse.OK;
    }

    /** 로그아웃 직전 — 이 기기로 앞 사람의 푸시가 오지 않게 */
    @DeleteMapping("/token")
    public OkResponse unregister(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody RegisterTokenRequest req) {
        // 등록과 같은 본문을 받는다 — platform 도 비면 안 되지만(@NotBlank) 해제에는 token 만 쓴다
        service.unregister(user.id(), req.token());
        return OkResponse.OK;
    }
}
