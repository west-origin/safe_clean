package kr.ansimclean.api.offer;

import kr.ansimclean.api.common.security.AdminAuth;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.offer.dto.RedispatchResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * POST /admin/bookings/{id}/redispatch — 운영자 전용 (X-Admin-Token, AdminDeviceController 와 같은 규칙).
 * 매칭 실패로 멈춘 예약을 다시 매칭한다 — 실패 뒤에는 저절로 다시 찾는 길이 없었다 (자체 점검 #5, docs/runbook.md)
 * 실제 주소는 /api/v1/admin/bookings/{id}/redispatch (SecurityConfig.API = "/api/v1").
 * SecurityConfig 는 /admin/** 를 로그인 없이 열어 두고, 운영자 토큰은 AdminTokenFilter 가 컨트롤러 앞에서 먼저 검사한다.
 * 아래 auth.require 는 두 번째 검사다 — 필터가 빠지거나 경로가 바뀌어도 이 API 가 열리지 않게 남겨 둔다.
 * 실제 처리는 OfferService.redispatchByAdmin. 지금은 자동 재매칭(MatchingRetry)도 있지만 그것은 새 후보에게만 보낸다 —
 * 이미 콜을 받았던 매니저에게까지 다시 보내려면 이 API 를 쓴다.
 */
@RestController
@RequestMapping(SecurityConfig.API + "/admin/bookings")
public class AdminOfferController {
    private final OfferService service;
    private final AdminAuth auth;

    public AdminOfferController(OfferService service, AdminAuth auth) {
        this.service = service;
        this.auth = auth;
    }

    /**
     * 운영자 재매칭. token = X-Admin-Token 헤더(required = false 라 없어도 받고 require 가 거절), id = 예약 id.
     * 토큰이 없거나 틀리면(서버에 운영자 토큰이 설정돼 있지 않아도) 401 UNAUTHENTICATED — 보통은 AdminTokenFilter 가 먼저 끝낸다.
     * 맞으면 { bookingId, offered }. 매칭 중(assigning)이 아닌 예약이면 INVALID_STATE
     */
    @PostMapping("/{id}/redispatch")
    public RedispatchResponse redispatch(@RequestHeader(value = AdminAuth.HEADER, required = false) String token, @PathVariable UUID id) {
        auth.require(token);
        return service.redispatchByAdmin(id);
    }
}
