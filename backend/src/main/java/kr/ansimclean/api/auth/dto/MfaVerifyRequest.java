package kr.ansimclean.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** POST /auth/mfa/verify 본문 — 인증앱 6자리 (어드민 매니저 앱 로그인 2단계, rbac.md §2.1) */
public record MfaVerifyRequest(@NotBlank @Pattern(regexp = "\\d{6}", message = "인증앱의 6자리 숫자를 입력해주세요") String code) {}
