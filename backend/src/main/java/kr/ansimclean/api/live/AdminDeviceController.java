package kr.ansimclean.api.live;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.security.AccessPolicy;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.live.dto.RegisterDeviceRequest;
import kr.ansimclean.api.live.dto.RegisterDeviceResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** POST /admin/devices — 어드민 전용 (admin 역할 + MFA, rbac.md §2.1). 관리 화면 전까지의 바디캠 등록 창구 (§13-8) */
// 실제 경로는 /api/v1/admin/devices. SecurityConfig 가 /admin/** 에 ROLE_ADMIN + MFA_AAL2 를 요구하고,
// 아래 policy.require(AccessPolicy) 가 두 번째 겹이다. 본문 검증은 @Valid 대신 Validator 로 손수 한다 (순서 이유는 register 안 주석).
// 등록 규칙(매니저 확인·LiveKit Ingress 만들기·키 회전)은 DeviceService.register 에 있다.
@RestController
@RequestMapping(SecurityConfig.API + "/admin/devices")
public class AdminDeviceController {
    private final DeviceService service;
    private final AccessPolicy policy;
    private final Validator validator;

    // Validator = 스프링이 준비해 두는 검증기(@NotBlank 같은 표시를 검사한다). register 가 권한 검사 뒤에 직접 부른다
    public AdminDeviceController(DeviceService service, AccessPolicy policy, Validator validator) {
        this.service = service;
        this.policy = policy;
        this.validator = validator;
    }

    /**
     * 바디캠을 매니저에게 등록(이미 있으면 이름·일련번호 갱신, rotate=true 면 스트림 키 교체) → 기기·Ingress·송출 URL·스트림 키.
     * 로그인이 없으면 401, 어드민 + MFA 가 아니면 403, 본문이 규칙에 어긋나면 400("필드: 문구"), 매니저가 아니면 400
     */
    @PostMapping
    public RegisterDeviceResponse register(@AuthenticationPrincipal CurrentUser actor, @RequestBody RegisterDeviceRequest req) {
        policy.require(actor, AccessPolicy.Action.DEVICE_REGISTER, AccessPolicy.Resource.of("manager", req.managerId())); // 어드민 + MFA 만 (rbac.md §2.1). 본문 검증보다 먼저 — 권한 없는 요청에 검증 결과를 알려주지 않는다
        // validate 는 어긴 규칙(@NotNull·@NotBlank·@Size)의 목록을 준다. 첫 번째 것에서 바로 400 을 던진다
        for (ConstraintViolation<RegisterDeviceRequest> v : validator.validate(req)) throw ApiException.validation(v.getPropertyPath() + ": " + v.getMessage());
        return service.register(req);
    }
}
