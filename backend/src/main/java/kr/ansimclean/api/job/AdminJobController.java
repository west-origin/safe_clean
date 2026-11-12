package kr.ansimclean.api.job;

import kr.ansimclean.api.common.security.AccessPolicy;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.job.dto.JobIdResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * POST /admin/jobs/{id}/complete — 어드민 전용 (admin 역할 + MFA, rbac.md §2.1).
 * 매니저가 '청소 완료' 를 누르지 않고 떠난 작업을 닫는다 — 다른 길이 없어 고객 탈퇴가 막히고 녹화가 이어졌다 (자체 점검 #17, docs/runbook.md)
 * 실제 경로는 /api/v1/admin/jobs/… (SecurityConfig.API 가 앞에 붙는다).
 * SecurityConfig 가 /admin/** 에 ROLE_ADMIN + MFA_AAL2 를 요구하고(로그인 없으면 401, 권한 없으면 403),
 * 메서드 안의 policy.require(AccessPolicy) 는 경로 규칙이 바뀌어도 열리지 않게 하는 두 번째 겹이다.
 */
@RestController
@RequestMapping(SecurityConfig.API + "/admin/jobs")
public class AdminJobController {
    private final JobService service;
    private final AccessPolicy policy;

    public AdminJobController(JobService service, AccessPolicy policy) {
        this.service = service;
        this.policy = policy;
    }

    /**
     * 어드민이 작업(예약 id)을 완료 처리한다 → { jobId }. 로그인이 없으면 401, 어드민 + MFA 가 아니면 403 이고 작업은 건드리지 않는다.
     * 정산 생성·완료 수·완료 푸시는 JobService.completeByAdmin 이 매니저 완료와 같은 경로(finish)로 한다. 이미 완료면 그대로 돌려준다
     */
    @PostMapping("/{id}/complete")
    public JobIdResponse complete(@AuthenticationPrincipal CurrentUser actor, @PathVariable UUID id) {
        policy.require(actor, AccessPolicy.Action.JOB_FORCE_COMPLETE, AccessPolicy.Resource.of("booking", id)); // 어드민 + MFA 만 (rbac.md §2.1). 본문 검증보다 먼저 — 권한 없는 요청에 검증 결과를 알려주지 않는다
        return service.completeByAdmin(id);
    }
}
