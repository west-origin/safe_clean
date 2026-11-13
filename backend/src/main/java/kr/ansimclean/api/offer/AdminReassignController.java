package kr.ansimclean.api.offer;

import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.offer.dto.AdminCancelRequestItem;
import kr.ansimclean.api.offer.dto.AssignRequest;
import kr.ansimclean.api.offer.dto.AssignResponse;
import kr.ansimclean.api.offer.dto.AssignableManager;
import kr.ansimclean.api.offer.dto.CancelDecisionResponse;
import kr.ansimclean.api.offer.dto.ReassignmentItem;
import kr.ansimclean.api.offer.dto.RejectCancelRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 어드민 — 매니저 방문 취소 승인·거절과 관리자 배정 (docs/api.md '매니저 방문 취소 승인·재배정'). 실제 주소는 /api/v1/admin/….
 * SecurityConfig 가 /admin/** 에 admin + MFA(aal2) 를 요구하고, ReassignService 가 AccessPolicy 로 한 번 더 본다.
 */
@RestController
@RequestMapping(SecurityConfig.API + "/admin")
public class AdminReassignController {
    private final ReassignService service;

    public AdminReassignController(ReassignService service) {
        this.service = service;
    }

    /** GET /admin/cancel-requests → 대기 중인 방문 취소 요청, 오래된 순 */
    @GetMapping("/cancel-requests")
    public List<AdminCancelRequestItem> cancelRequests(@AuthenticationPrincipal CurrentUser actor) {
        return service.cancelRequests(actor);
    }

    /** POST /admin/cancel-requests/{id}/approve → { id, bookingId, status: "approved", next, reassignUntil } */
    @PostMapping("/cancel-requests/{id}/approve")
    public CancelDecisionResponse approve(@AuthenticationPrincipal CurrentUser actor, @PathVariable UUID id) {
        return service.approve(actor, id);
    }

    /** POST /admin/cancel-requests/{id}/reject { note } → { id, bookingId, status: "rejected" }. 본문은 없어도 된다 */
    @PostMapping("/cancel-requests/{id}/reject")
    public CancelDecisionResponse reject(@AuthenticationPrincipal CurrentUser actor, @PathVariable UUID id,
                                         @RequestBody(required = false) RejectCancelRequest req) {
        return service.reject(actor, id, req == null ? null : req.note());
    }

    /** GET /admin/reassignments → 관리자 배정 대기 예약, 방문 가까운 순 */
    @GetMapping("/reassignments")
    public List<ReassignmentItem> reassignments(@AuthenticationPrincipal CurrentUser actor) {
        return service.reassignments(actor);
    }

    /** GET /admin/bookings/{id}/assignable-managers → 배정할 수 있는 매니저 (근무시간 안 먼저) */
    @GetMapping("/bookings/{id}/assignable-managers")
    public List<AssignableManager> assignableManagers(@AuthenticationPrincipal CurrentUser actor, @PathVariable UUID id) {
        return service.assignableManagers(actor, id);
    }

    /** POST /admin/bookings/{id}/assign { managerId } → { bookingId, managerId } */
    @PostMapping("/bookings/{id}/assign")
    public AssignResponse assign(@AuthenticationPrincipal CurrentUser actor, @PathVariable UUID id, @RequestBody AssignRequest req) {
        return service.assign(actor, id, req == null ? null : req.managerId());
    }
}
