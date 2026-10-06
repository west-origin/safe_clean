package kr.ansimclean.api.auth.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

// 가입 마지막 단계(약관 동의 보내기)의 요청 본문. AuthController.complete 가 받아 AuthService.completeSignup 에 넘긴다.
// @NotNull = 목록 자체가 빠지면 400(검증 실패). 빈 목록은 여기서 막지 않고, 필수 약관 검사는 서비스 쪽(TermsService)이 한다.
// 이미 가입이 끝난 계정이면 서비스가 목록을 다시 기록하지 않고 그대로 성공으로 답한다 (두 번 보내도 안전).
// 목록에 "marketing" 이 있으면 마케팅 수신 동의로 저장된다 (AuthService.completeSignup).
/** POST /auth/signup/complete — 약관 화면에서 체크한 id 들 */
public record CompleteSignupRequest(@NotNull List<String> agreedTermIds) {}
