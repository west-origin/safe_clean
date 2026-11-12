package kr.ansimclean.api.profile;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.security.AccessPolicy;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.profile.dto.PenaltyWaiveResponse;
import kr.ansimclean.api.profile.dto.WaiveRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * POST /admin/penalties/{id}/waive — 어드민 전용 (admin 역할 + MFA, rbac.md §2.1).
 * 증빙(진단서·사고 확인서 등)이 인정된 패널티를 면제한다 — 점수에서 빼고, 그 때문에 걸린 콜 배정 정지도 한 트랜잭션에서 푼다
 * (docs/manager-policy.md "증빙이 있으면 면제", 문제 목록 3번). 예전에는 API 가 없어 SQL 로 status 만 바꿨고 정지가 남았다.
 * 실제 경로는 /api/v1/admin/penalties/{id}/waive. SecurityConfig 가 /admin/** 에 ROLE_ADMIN + MFA_AAL2 를 요구하고,
 * 아래 policy.require(AccessPolicy) 는 경로 규칙이 바뀌어도 열리지 않게 하는 두 번째 겹이다.
 * 처리는 ManagerPenaltyService.waive. 운영자가 쓰는 curl 예시는 backend/docs/runbook.md.
 */
@RestController
@RequestMapping(SecurityConfig.API + "/admin/penalties")
public class AdminPenaltyController {
    private final ManagerPenaltyService service;
    private final AccessPolicy policy;
    private final Validator validator;

    // Validator: @NotBlank·@Size 같은 표시를 검사하는 검증기 — @Valid 대신 waive 안에서 권한 검사 뒤에 손수 부른다 (AdminInquiryController 와 같다)
    public AdminPenaltyController(ManagerPenaltyService service, AccessPolicy policy, Validator validator) {
        this.service = service;
        this.policy = policy;
        this.validator = validator;
    }

    /**
     * 패널티 원장 한 줄(id)을 면제한다 → { penaltyId, managerId, points, suspendedUntil } (면제 뒤 누적 점수와 지금 걸린 정지).
     * 로그인이 없으면 401, 어드민 + MFA 가 아니면 403, 메모(note)가 비었거나 500자를 넘으면 400, 없는 id 는 404, 회복 기록·기간이 끝난 기록은 409.
     * 이미 면제된 기록이면 바꾸지 않고 같은 모양으로 돌려준다 — curl 을 다시 보내도 안전하다
     */
    @PostMapping("/{id}/waive")
    public PenaltyWaiveResponse waive(@AuthenticationPrincipal CurrentUser actor, @PathVariable UUID id, @RequestBody WaiveRequest req) {
        policy.require(actor, AccessPolicy.Action.PENALTY_WAIVE, AccessPolicy.Resource.of("penalty", id)); // 어드민 + MFA 만 (rbac.md §2.1). 본문 검증보다 먼저 — 권한 없는 요청에 검증 결과를 알려주지 않는다
        // validate 는 어긴 규칙 목록을 준다. 첫 번째 것에서 바로 400(VALIDATION)을 던진다
        for (ConstraintViolation<WaiveRequest> v : validator.validate(req)) throw ApiException.validation(v.getPropertyPath() + ": " + v.getMessage());
        return service.waive(id, req.note().trim());
    }
}
