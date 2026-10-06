package kr.ansimclean.api.offer;

import jakarta.validation.Valid;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.offer.dto.AcceptResponse;
import kr.ansimclean.api.offer.dto.CallDetailResponse;
import kr.ansimclean.api.offer.dto.CallsResponse;
import kr.ansimclean.api.offer.dto.RejectRequest;
import kr.ansimclean.api.offer.dto.RejectResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// 매니저 앱 콜 화면의 API. 주소는 /api/v1/manager/offers 아래이고 매니저만 쓸 수 있다 (CODE_GUIDE.md 3-11).
// 앱 쪽은 apps/manager/src/api/manager.js 의 getCalls·getCall·acceptCall·rejectCall (주소마다 하나). 규칙은 OfferService 에 있다.
// @AuthenticationPrincipal CurrentUser = 로그인 토큰에서 꺼낸 지금 요청한 사용자 (user.id() = 매니저 id)
/** 콜 목록·상세·수락·거절 (docs/api.md §5.3) */
@RestController
@RequestMapping(SecurityConfig.API + "/manager/offers")
public class OfferController {
    private final OfferService service;

    public OfferController(OfferService service) {
        this.service = service;
    }

    /** GET /manager/offers — 콜 탭: 내 대기 중 콜 카드들 + 콜 수신 상태 카드 + 승인 여부 */
    @GetMapping
    public CallsResponse calls(@AuthenticationPrincipal CurrentUser user) {
        return service.calls(user.id());
    }

    /** GET /manager/offers/{id} — 콜 상세 ({id} = 콜 id). 남의 콜이면 NOT_FOUND, 이미 닫혔거나 만료면 OFFER_EXPIRED 오류 */
    @GetMapping("/{id}")
    public CallDetailResponse call(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return service.call(user.id(), id);
    }

    /**
     * POST /manager/offers/{id}/accept — 콜 수락 → { jobId }. Idempotency-Key 헤더는 선택이다 (CODE_GUIDE.md 3-7):
     * 없으면 멱등 처리 없이 그냥 실행한다. 앱의 acceptCall 은 콜마다 키를 붙이고 재시도 때 같은 키를 보낸다
     */
    @PostMapping("/{id}/accept")
    public AcceptResponse accept(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id, @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return service.accept(user.id(), id, key);
    }

    /**
     * POST /manager/offers/{id}/reject — 콜 거절. 본문 { reason } 을 RejectRequest 로 읽는다.
     * 사유가 비어 있으면 RejectRequest 검사에서 400 VALIDATION 으로 끝나 서비스까지 오지 않는다.
     * 멱등 키는 받지 않는다 — 이미 닫힌 콜에 다시 보내면 OfferService.reject 가 오류 대신 저장된 사유를 돌려준다
     */
    @PostMapping("/{id}/reject")
    public RejectResponse reject(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id, @Valid @RequestBody RejectRequest req) {
        return service.reject(user.id(), id, req.reason());
    }
}
