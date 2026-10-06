package kr.ansimclean.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 비밀번호 재설정 마지막 단계의 요청 본문. AuthController.resetPassword → AuthService.resetPassword.
// 흐름: POST /auth/password/otp(문자 발송) → POST /auth/phone/verify(번호 확인, 토큰 받음) → 이 요청.
// 성공하면 새 비밀번호로 로그인된 AuthResponse 를 돌려주고 다른 기기의 세션은 끊는다.
// @Size(max = 72) = 72자까지. 8자 미만은 서비스가 PASSWORD_TOO_SHORT 로 막는다.
/** POST /auth/password/reset — /auth/phone/verify 로 받은 토큰 + 새 비밀번호 */
public record ResetPasswordRequest(@NotBlank(message = "휴대폰 인증을 먼저 해주세요") String verificationToken,
                                   @NotBlank(message = "비밀번호를 입력해주세요") @Size(max = 72) String password) {}
