package kr.ansimclean.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// 문자로 받은 인증번호 확인 요청 본문. AuthController.verify → AuthService.verifyOtp.
// 가입과 비밀번호 재설정이 같이 쓴다. 맞으면 VerifyResponse(인증 토큰)를 돌려준다.
// phone 은 앱 입력값(010 뒤 8자리), code 는 숫자 6자리, role 은 "customer" 또는 "manager".
// @Pattern = 정규식과 일치해야 한다(아니면 400 VALIDATION).
/** POST /auth/phone/verify */
public record VerifyRequest(@NotBlank String phone,
                            @NotBlank @Pattern(regexp = "\\d{6}", message = "인증번호 6자리를 입력해주세요") String code,
                            @NotBlank @Pattern(regexp = "customer|manager") String role) {}
