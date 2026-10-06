package kr.ansimclean.api.job;

import kr.ansimclean.api.common.security.AdminAuth;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.job.dto.JobIdResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * POST /admin/jobs/{id}/complete — 운영자 전용 (X-Admin-Token, AdminDeviceController 와 같은 규칙).
 * 매니저가 '청소 완료' 를 누르지 않고 떠난 작업을 닫는다 — 다른 길이 없어 고객 탈퇴가 막히고 녹화가 이어졌다 (자체 점검 #17, docs/runbook.md)
 * 실제 경로는 /api/v1/admin/jobs/… (SecurityConfig.API 가 앞에 붙는다).
 * SecurityConfig 는 /admin/** 를 로그인 없이 통과시키고, AdminTokenFilter 가 컨트롤러 앞에서 X-Admin-Token 을 먼저 검사한다.
 * 메서드 안의 auth.require 는 필터가 빠져도 열리지 않게 하는 이중 방어다.
 */
@RestController
@RequestMapping(SecurityConfig.API + "/admin/jobs")
public class AdminJobController {
    private final JobService service;
    private final AdminAuth auth;

    public AdminJobController(JobService service, AdminAuth auth) {
        this.service = service;
        this.auth = auth;
    }

    /**
     * 운영자가 작업(예약 id)을 완료 처리한다 → { jobId }. 토큰이 없거나 틀리면 401 이고 작업은 건드리지 않는다.
     * 정산 생성·완료 수·완료 푸시는 JobService.completeByAdmin 이 매니저 완료와 같은 경로(finish)로 한다. 이미 완료면 그대로 돌려준다
     */
    @PostMapping("/{id}/complete")
    public JobIdResponse complete(@RequestHeader(value = AdminAuth.HEADER, required = false) String token, @PathVariable UUID id) {
        auth.require(token);
        return service.completeByAdmin(id);
    }
}
