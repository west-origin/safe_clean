package kr.ansimclean.api.support;

import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.support.dto.AdminNoticeDeleted;
import kr.ansimclean.api.support.dto.AdminNoticeRequest;
import kr.ansimclean.api.support.dto.AdminNoticeResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * /admin/notices — 어드민 공지 관리 (매니저 앱 관리자 화면, rbac.md §10·§15-23). 실제 경로 /api/v1/admin/notices.
 * SecurityConfig 가 /admin/** 에 admin + MFA 를 요구하고, AdminNoticeService 가 AccessPolicy 로 한 번 더 본다.
 * 응답 모양은 backend/docs/api.md "어드민 공지".
 */
@RestController
@RequestMapping(SecurityConfig.API + "/admin/notices")
public class AdminNoticeController {
    private final AdminNoticeService service;

    public AdminNoticeController(AdminNoticeService service) {
        this.service = service;
    }

    /** GET /admin/notices → [{ id, audience, title, date, body: null }] 모든 대상, 최신순 */
    @GetMapping
    public List<AdminNoticeResponse> list(@AuthenticationPrincipal CurrentUser actor) {
        return service.list(actor);
    }

    /** GET /admin/notices/{id} → { id, audience, title, date, body } */
    @GetMapping("/{id}")
    public AdminNoticeResponse get(@AuthenticationPrincipal CurrentUser actor, @PathVariable UUID id) {
        return service.get(actor, id);
    }

    /** POST /admin/notices { audience, title, body } (+ Idempotency-Key) → 올린 공지 */
    @PostMapping
    public AdminNoticeResponse create(@AuthenticationPrincipal CurrentUser actor, @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                      @RequestBody AdminNoticeRequest req) {
        return service.create(actor, key, req);
    }

    /** PUT /admin/notices/{id} { audience, title, body } → 고친 공지 (게시 날짜 그대로) */
    @PutMapping("/{id}")
    public AdminNoticeResponse update(@AuthenticationPrincipal CurrentUser actor, @PathVariable UUID id, @RequestBody AdminNoticeRequest req) {
        return service.update(actor, id, req);
    }

    /** DELETE /admin/notices/{id} → { ok: true } */
    @DeleteMapping("/{id}")
    public AdminNoticeDeleted delete(@AuthenticationPrincipal CurrentUser actor, @PathVariable UUID id) {
        service.delete(actor, id);
        return AdminNoticeDeleted.OK;
    }
}
