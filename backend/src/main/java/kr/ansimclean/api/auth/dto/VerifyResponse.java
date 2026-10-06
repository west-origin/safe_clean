package kr.ansimclean.api.auth.dto;

// 인증번호 확인 성공 응답 (POST /auth/phone/verify, AuthService.verifyOtp).
// verificationToken = 가입(POST /auth/signup)이나 비밀번호 재설정(POST /auth/password/reset)에 한 번 쓰는 토큰.
// expiresIn = 토큰 유효 시간(초, OtpService.TOKEN_TTL 15분). 지나면 가입·재설정이 OTP_EXPIRED 로 거부된다.
/** = 앱 verifyPhone → { verificationToken, expiresIn(초) } */
public record VerifyResponse(String verificationToken, long expiresIn) {}
