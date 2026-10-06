package kr.ansimclean.api.profile;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.security.AdminAuth;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.profile.dto.PenaltyWaiveResponse;
import kr.ansimclean.api.profile.dto.WaiveRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * POST /admin/penalties/{id}/waive — 운영자 전용 (X-Admin-Token, AdminDeviceController 와 같은 규칙).
 * 증빙(진단서·사고 확인서 등)이 인정된 패널티를 면제한다 — 점수에서 빼고, 그 때문에 걸린 콜 배정 정지도 한 트랜잭션에서 푼다
 * (docs/manager-policy.md "증빙이 있으면 면제", 문제 목록 3번). 예전에는 API 가 없어 SQL 로 status 만 바꿨고 정지가 남았다.
 * 실제 경로는 /api/v1/admin/penalties/{id}/waive. SecurityConfig 는 /admin/** 를 로그인 없이 통과시키고,
 * AdminTokenFilter 가 컨트롤러 앞에서 X-Admin-Token 을 먼저 검사한다. 아래 auth.require 는 필터가 빠져도 열리지 않게 하는 두 번째 검사다.
 * 처리는 ManagerPenaltyService.waive. 운영자가 쓰는 curl 예시는 backend/docs/runbook.md.
 */
@RestController
@RequestMapping(SecurityConfig.API + "/admin/penalties")
public class AdminPenaltyController {
    private final ManagerPenaltyService service;
    private final AdminAuth auth;
    private final Validator validator;

    // Validator: @NotBlank·@Size 같은 표시를 검사하는 검증기 — @Valid 대신 waive 안에서 토큰 검사 뒤에 손수 부른다 (AdminInquiryController 와 같다)
    public AdminPenaltyController(ManagerPenaltyService service, AdminAuth auth, Validator validator) {
        this.service = service;
        this.auth = auth;
        this.validator = validator;
    }

    /**
     * 패널티 원장 한 줄(id)을 면제한다 → { penaltyId, managerId, points, suspendedUntil } (면제 뒤 누적 점수와 지금 걸린 정지).
     * 토큰이 틀리면 401, 메모(note)가 비었거나 500자를 넘으면 400, 없는 id 는 404, 회복 기록·기간이 끝난 기록은 409.
     * 이미 면제된 기록이면 바꾸지 않고 같은 모양으로 돌려준다 — curl 을 다시 보내도 안전하다
     */
    // 헤더는 required = false 로 받아, 없을 때도 스프링의 400(헤더 없음) 대신 AdminAuth 의 401 이 나가게 한다
    @PostMapping("/{id}/waive")
    public PenaltyWaiveResponse waive(@RequestHeader(value = AdminAuth.HEADER, required = false) String token, @PathVariable UUID id, @RequestBody WaiveRequest req) {
        auth.require(token); // 토큰 검사가 본문 검증보다 먼저
        // validate 는 어긴 규칙 목록을 준다. 첫 번째 것에서 바로 400(VALIDATION)을 던진다
        for (ConstraintViolation<WaiveRequest> v : validator.validate(req)) throw ApiException.validation(v.getPropertyPath() + ": " + v.getMessage());
        return service.waive(id, req.note().trim());
    }
}
