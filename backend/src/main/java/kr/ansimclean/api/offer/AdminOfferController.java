package kr.ansimclean.api.offer;

import kr.ansimclean.api.common.security.AccessPolicy;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.offer.dto.RedispatchResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * POST /admin/bookings/{id}/redispatch — 어드민 전용 (admin 역할 + MFA, rbac.md §2.1).
 * 매칭 실패로 멈춘 예약을 다시 매칭한다 — 실패 뒤에는 저절로 다시 찾는 길이 없었다 (자체 점검 #5, docs/runbook.md)
 * 실제 주소는 /api/v1/admin/bookings/{id}/redispatch (SecurityConfig.API = "/api/v1").
 * SecurityConfig 가 /admin/** 에 ROLE_ADMIN + MFA_AAL2 를 요구한다. 아래 policy.require(AccessPolicy) 는 두 번째 겹이다 —
 * 경로 규칙이 바뀌어도 이 API 가 열리지 않게 남겨 둔다.
 * 실제 처리는 OfferService.redispatchByAdmin. 지금은 자동 재매칭(MatchingRetry)도 있지만 그것은 새 후보에게만 보낸다 —
 * 이미 콜을 받았던 매니저에게까지 다시 보내려면 이 API 를 쓴다.
 */
@RestController
@RequestMapping(SecurityConfig.API + "/admin/bookings")
public class AdminOfferController {
    private final OfferService service;
    private final AccessPolicy policy;

    public AdminOfferController(OfferService service, AccessPolicy policy) {
        this.service = service;
        this.policy = policy;
    }

    /**
     * 어드민 재매칭. actor = 로그인한 사용자(SecurityConfig 가 채운다), id = 예약 id.
     * 로그인이 없으면 401, 어드민 + MFA 가 아니면 403.
     * 맞으면 { bookingId, offered }. 매칭 중(assigning)이 아닌 예약이면 INVALID_STATE
     */
    @PostMapping("/{id}/redispatch")
    public RedispatchResponse redispatch(@AuthenticationPrincipal CurrentUser actor, @PathVariable UUID id) {
        policy.require(actor, AccessPolicy.Action.BOOKING_REDISPATCH, AccessPolicy.Resource.of("booking", id)); // 어드민 + MFA 만 (rbac.md §2.1). 본문 검증보다 먼저 — 권한 없는 요청에 검증 결과를 알려주지 않는다
        return service.redispatchByAdmin(id);
    }
}
