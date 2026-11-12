package kr.ansimclean.api.support;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.common.security.AccessPolicy;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.support.dto.AnswerRequest;
import kr.ansimclean.api.support.dto.MyInquiryResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** POST /admin/inquiries/{id}/answer — 어드민 전용 (admin 역할 + MFA, rbac.md §2.1). 관리 화면 전까지의 답변 창구 — 답하면 사용자에게 푸시가 간다 */
// 실제 경로는 /api/v1/admin/inquiries/{id}/answer.
// SecurityConfig 가 /admin/** 에 ROLE_ADMIN + MFA_AAL2 를 요구하고, 여기서도 AccessPolicy.require 로 한 번 더 검사한다. 본문 검증은 @Valid 대신 Validator 로 손수 한다 (순서 이유는 answer 안 주석).
// 답변 저장과 푸시 예약(아웃박스)은 SupportService.answer 가 한다. 운영자가 쓰는 curl 예시는 backend/docs/runbook.md.
@RestController
@RequestMapping(SecurityConfig.API + "/admin/inquiries")
public class AdminInquiryController {
    private final SupportService service;
    private final AccessPolicy policy;
    private final Validator validator;

    // Validator: @NotBlank·@Size 같은 표시를 검사하는 검증기 — @Valid 대신 answer 안에서 권한 검사 뒤에 손수 부른다
    public AdminInquiryController(SupportService service, AccessPolicy policy, Validator validator) {
        this.service = service;
        this.policy = policy;
        this.validator = validator;
    }

    /**
     * 문의 한 건에 운영자 답변을 저장한다 → 답변이 들어간 문의(MyInquiryResponse, 앱의 '내 문의' 항목과 같은 모양).
     * 로그인이 없으면 401, 어드민 + MFA 가 아니면 403, 답변이 비었거나 4,000자를 넘으면 400("answer: 문구"), 없는 문의면 404.
     * 이미 답한 문의에 다시 보내면 답변을 덮어쓰고 푸시 이벤트도 한 번 더 적힌다 (Inquiry.answer, SupportService.answer).
     */
    @PostMapping("/{id}/answer")
    public MyInquiryResponse answer(@AuthenticationPrincipal CurrentUser actor, @PathVariable UUID id, @RequestBody AnswerRequest req) {
        policy.require(actor, AccessPolicy.Action.INQUIRY_ANSWER, AccessPolicy.Resource.of("inquiry", id)); // 어드민 + MFA 만 (rbac.md §2.1). 본문 검증보다 먼저 — 권한 없는 요청에 검증 결과를 알려주지 않는다
        // @Valid 를 붙이면 스프링이 메서드 본문보다 먼저(인자를 만들 때) 검사한다 — 그래서 권한 검사 뒤에 손수 부른다.
        // validate 는 어긴 규칙 목록을 준다. 첫 번째 것에서 바로 400(VALIDATION)을 던진다
        for (ConstraintViolation<AnswerRequest> v : validator.validate(req)) throw ApiException.validation(v.getPropertyPath() + ": " + v.getMessage());
        return service.answer(id, req.answer());
    }
}
