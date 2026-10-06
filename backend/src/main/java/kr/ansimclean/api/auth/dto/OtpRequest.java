package kr.ansimclean.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// 인증번호(SMS) 발송 요청 본문. 두 경로가 같이 쓴다:
//  - POST /auth/phone/otp: 가입용 — 이미 쓰는 번호면 PHONE_TAKEN (AuthService.requestOtp)
//  - POST /auth/password/otp: 비밀번호 재설정용 — 그 역할의 기존 계정 번호여야 한다 (AuthService.requestPasswordOtp)
// @NotBlank·@Pattern 에 걸리면 400 VALIDATION. message 를 적은 검사는 그 문구가, 적지 않은 검사(role 의 @NotBlank)는
// 일반 문구 "입력값을 확인해주세요" 가 나간다 (GlobalExceptionHandler). role 은 "customer" 또는 "manager" 만 — 어느 앱에서 왔는지.
/** POST /auth/phone/otp — phone 은 앱 PhoneField 값(010 뺀 8자리) */
public record OtpRequest(@NotBlank(message = "휴대폰 번호를 입력해주세요") String phone,
                         @NotBlank @Pattern(regexp = "customer|manager", message = "role 은 customer 또는 manager") String role) {}
