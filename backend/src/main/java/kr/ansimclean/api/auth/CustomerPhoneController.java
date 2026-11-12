package kr.ansimclean.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.common.web.ClientIp;
import kr.ansimclean.api.profile.FirstCleaningService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 고객 휴대폰 인증 (첫 1회 청소). /customer/** 라 가입을 마친 고객만. 계약은 backend/docs/api.md "고객 휴대폰 인증".
 * 앱: apps/customer/src/api/customer.js requestCustomerPhoneProof · verifyCustomerPhoneProof · getFirstCleaningEligibility
 */
@RestController
@RequestMapping(SecurityConfig.API + "/customer/identity")
public class CustomerPhoneController {
    private final CustomerPhoneService service;
    private final ClientIp clientIp;

    public CustomerPhoneController(CustomerPhoneService service, ClientIp clientIp) {
        this.service = service;
        this.clientIp = clientIp;
    }

    /** phone: 010 뒤 8자리 또는 010 포함 11자리 (OtpService.toE164 가 검사) */
    public record Phone(@NotBlank String phone) {}

    public record Code(@NotBlank String phone, @NotBlank @Pattern(regexp = "[0-9]{6}") String code) {}

    @GetMapping("/first-cleaning")
    public FirstCleaningService.Eligibility eligibility(@AuthenticationPrincipal CurrentUser user) {
        return service.eligibility(user.id());
    }

    /** 인증번호 요청·재발송 → { expiresIn: 180, resendAfter: 60, verified: false } */
    @PostMapping("/phone/otp")
    public CustomerPhoneService.OtpSent request(@CurrentSecurityContext SecurityContext context, @Valid @RequestBody Phone req, HttpServletRequest request) {
        var auth = context.getAuthentication();
        CurrentUser user = (CurrentUser) auth.getPrincipal();
        String token = ((Jwt) auth.getCredentials()).getTokenValue(); // Supabase 가 이 토큰의 계정에 번호를 붙인다
        return service.request(user.id(), token, req.phone(), clientIp.of(request));
    }

    /** 인증번호 확인 → 첫 1회 청소 자격 { verified, eligible, reason, completedOneTime } */
    @PostMapping("/phone/verify")
    public FirstCleaningService.Eligibility verify(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody Code req, HttpServletRequest request) {
        return service.verify(user.id(), req.phone(), req.code(), clientIp.of(request));
    }
}
