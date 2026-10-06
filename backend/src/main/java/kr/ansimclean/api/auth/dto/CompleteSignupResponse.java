package kr.ansimclean.api.auth.dto;

import kr.ansimclean.api.profile.dto.UserResponse;

/**
 * POST /auth/signup/complete 의 응답 (AuthService.completeSignup 이 만든다).
 * profileComplete = 가입이 끝났는가 — 지금 코드는 늘 true 로 만든다. user = 가입 완료가 반영된 사용자 요약.
 * 앱(customer.js completeSignUp, manager.js completeManagerSignUp)은 user 만 꺼내 세션 사용자로 덮어쓴다.
 */
public record CompleteSignupResponse(boolean profileComplete, UserResponse user) {}
