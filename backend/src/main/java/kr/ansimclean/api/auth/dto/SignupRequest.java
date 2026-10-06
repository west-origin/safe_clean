package kr.ansimclean.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 계정 만들기 요청 본문. AuthController.signup → AuthService.signup (휴대폰 인증 뒤, 약관 동의 전 단계).
// verificationToken 은 POST /auth/phone/verify 로 받은 1회용 토큰 — 번호와 역할(고객/매니저)이 토큰에 묶여 있다.
// nickname 은 고객만 쓰고 매니저 가입이면 서비스가 버린다. 비밀번호 8자 미만은 서비스가 PASSWORD_TOO_SHORT 로 막는다.
// @NotBlank(빈 값 금지)·@Size(최대 길이)에 걸리면 400 VALIDATION.
/** POST /auth/signup — 고객 { verificationToken, name, nickname, password } · 매니저 { verificationToken, name, password }. 역할은 토큰이 안다 */
public record SignupRequest(@NotBlank(message = "휴대폰 인증을 먼저 해주세요") String verificationToken,
                            @NotBlank(message = "이름을 입력해주세요") @Size(max = 50) String name,
                            @Size(max = 50) String nickname,
                            @NotBlank(message = "비밀번호를 입력해주세요") @Size(max = 72) String password) {}
