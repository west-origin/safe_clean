package kr.ansimclean.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

// 탈퇴 요청 본문. AuthController.deleteAccount 가 받아 AuthService.deleteAccount 에 넘긴다.
// 로그인돼 있어도 비밀번호를 한 번 더 받아 Supabase 로 맞는지 확인한다(틀리면 LOGIN_FAILED).
// @NotBlank = 비었거나 공백뿐이면 400 과 아래 문구로 거부한다.
/** DELETE /auth/account — 본인 확인용 비밀번호 */
public record DeleteAccountRequest(@NotBlank(message = "비밀번호를 입력해주세요") String password) {}
