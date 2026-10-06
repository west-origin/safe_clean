package kr.ansimclean.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// 로그인 요청 본문. AuthController.login 이 받아 AuthService.login 에 넘긴다.
// phone 은 앱 입력값(010 뒤 8자리)이고 서버가 +8210… 형식으로 바꾼다. role 은 어느 앱에서 왔는지 — 계정 역할과 다르면 로그인 실패.
// @NotBlank(빈 값 금지)·@Pattern(정규식과 일치해야 함)에 걸리면 400 VALIDATION.
// 여기처럼 message 를 주지 않은 검사는 영어 기본 문구 대신 "입력값을 확인해주세요" 로 나간다 (GlobalExceptionHandler).
/** POST /auth/login */
public record LoginRequest(@NotBlank String phone, @NotBlank String password,
                           @NotBlank @Pattern(regexp = "customer|manager") String role) {}
