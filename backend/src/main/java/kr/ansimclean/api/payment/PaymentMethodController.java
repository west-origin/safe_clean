package kr.ansimclean.api.payment;

import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.payment.dto.PaymentMethodResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 실제 경로는 /api/v1/customer/payment-methods (SecurityConfig.API = "/api/v1").
// /customer/** 경로라 가입을 끝낸 고객(ROLE_CUSTOMER)만 들어온다 (SecurityConfig).
// 부르는 곳: 고객 앱 getPaymentMethods (설정 › 결제 수단 화면). 응답 한 줄은 { id, label, isDefault }.
/** GET /customer/payment-methods — PG 연동 전에는 항상 [] (docs/api.md §5.2) */
@RestController
@RequestMapping(SecurityConfig.API + "/customer/payment-methods")
public class PaymentMethodController {
    private final PaymentService service;

    public PaymentMethodController(PaymentService service) {
        this.service = service;
    }

    // @AuthenticationPrincipal = 로그인한 사용자(토큰에서 JwtToCurrentUserConverter 가 만든 CurrentUser).
    // 그 사람의 id 로만 조회하므로 남의 결제 수단은 볼 수 없다
    @GetMapping
    public List<PaymentMethodResponse> methods(@AuthenticationPrincipal CurrentUser user) {
        return service.methods(user.id());
    }
}
