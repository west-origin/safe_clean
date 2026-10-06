package kr.ansimclean.api.auth.dto;

import kr.ansimclean.api.profile.dto.UserResponse;

// 로그인된 상태(토큰 묶음 + 사용자)를 돌려주는 응답 모양. 예: POST /auth/login·/auth/refresh.
// 비밀번호 재설정(/auth/password/reset)도 성공하면 바로 로그인된 이 응답을 준다.
//  - accessToken: API 를 부를 때 붙이는 JWT (Supabase 가 발급)
//  - refreshToken: access 토큰이 401 로 거부되면 앱 http.js 가 POST /auth/refresh 로 새 묶음을 받는 데 쓴다
//  - expiresIn: access 토큰이 유효한 초. 앱 session.js 가 만료 시각(ms)으로 바꿔 저장한다
//  - user: 앱 session.user 에 들어가는 사용자 요약 (UserResponse)
/** 로그인·가입·refresh 응답. 앱 session.js 가 그대로 저장한다 */
public record AuthResponse(String accessToken, String refreshToken, long expiresIn, UserResponse user) {}
