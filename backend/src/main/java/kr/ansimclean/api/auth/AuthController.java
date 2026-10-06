package kr.ansimclean.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import kr.ansimclean.api.auth.dto.AuthResponse;
import kr.ansimclean.api.auth.dto.CompleteSignupRequest;
import kr.ansimclean.api.auth.dto.CompleteSignupResponse;
import kr.ansimclean.api.auth.dto.DeleteAccountRequest;
import kr.ansimclean.api.auth.dto.LoginRequest;
import kr.ansimclean.api.auth.dto.OkResponse;
import kr.ansimclean.api.auth.dto.OtpRequest;
import kr.ansimclean.api.auth.dto.OtpResponse;
import kr.ansimclean.api.auth.dto.RefreshRequest;
import kr.ansimclean.api.auth.dto.ResetPasswordRequest;
import kr.ansimclean.api.auth.dto.SignupRequest;
import kr.ansimclean.api.auth.dto.VerifyRequest;
import kr.ansimclean.api.auth.dto.VerifyResponse;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.common.web.ClientIp;
import kr.ansimclean.api.profile.dto.UserResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** /api/v1/auth/** — 앱 api/customer.js·manager.js 의 인증 함수와 1:1 (docs/api.md §5.1) */
// 인증(가입·로그인·토큰 갱신·로그아웃·탈퇴) HTTP 입구. 규칙·외부 호출은 전부 AuthService 가 하고, 여기서는 요청을 풀어 넘기기만 한다.
// 로그인 없이 부를 수 있는 경로는 SecurityConfig 가 정한다 (예: /phone/**, /login, POST /signup — 아직 토큰이 없는 사용자가 쓴다).
// 그 밖의 경로(예: /signup/complete, /account)는 로그인 토큰이 있어야 한다. 새 경로를 더하면 SecurityConfig 도 같이 본다.
// clientIp.of(http): 속도 제한에 쓸 요청 IP (ClientIp — 프록시 헤더는 설정했을 때만 믿는다).
@RestController
@RequestMapping(SecurityConfig.API + "/auth")
public class AuthController {
    private final AuthService service;

    private final ClientIp clientIp;

    public AuthController(AuthService service, ClientIp clientIp) {
        this.service = service;
        this.clientIp = clientIp;
    }

    /** POST /auth/phone/otp — 가입용 인증번호 문자 발송. 앱 requestPhoneVerification (고객·매니저) */
    @PostMapping("/phone/otp")
    public OtpResponse otp(@Valid @RequestBody OtpRequest req, HttpServletRequest http) {
        return service.requestOtp(req.phone(), req.role(), clientIp.of(http));
    }

    /** POST /auth/phone/verify — 인증번호 확인 → verificationToken. 앱 verifyPhone(가입)·verifyPasswordReset(비밀번호 재설정) */
    @PostMapping("/phone/verify")
    public VerifyResponse verify(@Valid @RequestBody VerifyRequest req, HttpServletRequest http) {
        return service.verifyOtp(req.phone(), req.role(), req.code(), clientIp.of(http));
    }

    /** 비밀번호 재설정: 인증번호 발송 → /auth/phone/verify 로 확인 → /auth/password/reset */
    @PostMapping("/password/otp") // 앱 requestPasswordReset (고객·매니저)
    public OtpResponse passwordOtp(@Valid @RequestBody OtpRequest req, HttpServletRequest http) {
        return service.requestPasswordOtp(req.phone(), req.role(), clientIp.of(http));
    }

    /** POST /auth/password/reset — 새 비밀번호로 바꾸고 바로 로그인된 세션을 돌려준다. 앱 resetPassword */
    @PostMapping("/password/reset")
    public AuthResponse resetPassword(@Valid @RequestBody ResetPasswordRequest req, HttpServletRequest http) {
        return service.resetPassword(req.verificationToken(), req.password(), clientIp.of(http));
    }

    /** POST /auth/signup — 가입 1단계: 계정 생성 + 로그인 토큰. 앱 saveProfileInfo(고객)·saveManagerSignUp(매니저) */
    @PostMapping("/signup")
    public AuthResponse signup(@Valid @RequestBody SignupRequest req, HttpServletRequest http) {
        return service.signup(req, clientIp.of(http));
    }

    /**
     * POST /auth/signup/complete — 약관 동의 기록 + 가입 완료. 앱 completeSignUp(고객)·completeManagerSignUp(매니저).
     * 스프링 @AuthenticationPrincipal: 검증된 로그인 토큰에서 꺼낸 지금 사용자(CurrentUser)를 받는다.
     */
    @PostMapping("/signup/complete")
    public CompleteSignupResponse complete(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody CompleteSignupRequest req) {
        return service.completeSignup(user.id(), req.agreedTermIds());
    }

    /** POST /auth/login — 휴대폰 + 비밀번호 + 역할. 앱 loginWithPhone */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return service.login(req, clientIp.of(http));
    }

    /** POST /auth/refresh — access 토큰이 401 로 거부됐을 때 앱 http.js 가 refresh 토큰으로 부른다 */
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest req, HttpServletRequest http) {
        return service.refresh(req.refreshToken(), clientIp.of(http));
    }

    /**
     * POST /auth/logout — 이 로그인(세션) 하나만 끝낸다. 앱 logout.
     * 스프링 @CurrentSecurityContext: 이 요청의 인증 정보 전체. credentials 는 검증된 JWT, principal 은 CurrentUser 다
     * (CurrentUserAuthentication). 둘 다 맞을 때만 JWT 의 session_id 로 끊고, 아니면 할 일 없이 성공으로 답한다.
     */
    @PostMapping("/logout")
    public OkResponse logout(@CurrentSecurityContext SecurityContext context) {
        Object credentials = context.getAuthentication().getCredentials();
        if (credentials instanceof Jwt jwt && context.getAuthentication().getPrincipal() instanceof CurrentUser user) {
            service.logout(user.id(), jwt.getClaimAsString("session_id"), jwt.getTokenValue());
        }
        return OkResponse.OK;
    }

    /** GET /auth/me — 지금 로그인한 사용자 정보(가입 단계 signupStep 포함). 앱 refreshMe */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal CurrentUser user) {
        return service.me(user.id());
    }

    /** DELETE /auth/account — 탈퇴. 본문에 본인 확인용 비밀번호. 앱 deleteAccount (고객 설정·매니저 프로필 화면) */
    @DeleteMapping("/account")
    public OkResponse deleteAccount(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody DeleteAccountRequest req) {
        service.deleteAccount(user.id(), req.password());
        return OkResponse.OK;
    }
}
