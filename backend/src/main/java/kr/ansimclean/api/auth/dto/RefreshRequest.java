package kr.ansimclean.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * POST /auth/refresh 요청 본문 — 로그인·가입 때 받은 refreshToken 으로 새 토큰 묶음(AuthResponse)을 받는다.
 * 앱 http.js 가 access 토큰이 401 로 거부됐을 때 보낸다. 로그아웃 등으로 끊은 세션의 토큰이면 UNAUTHENTICATED (AuthService.refresh).
 * 만료·폐기된 토큰도 UNAUTHENTICATED(401) 이고, 앱은 이 401 에서만 세션을 지운다 — 429·502 는 잠깐의 장애로 보고 세션을 둔다.
 * @NotBlank = 비었으면 400 VALIDATION.
 */
public record RefreshRequest(@NotBlank String refreshToken) {}
