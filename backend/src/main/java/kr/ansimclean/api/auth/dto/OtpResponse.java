package kr.ansimclean.api.auth.dto;

// 인증번호 발송 결과. POST /auth/phone/otp·/auth/password/otp 가 돌려준다 (AuthService.requestOtp·requestPasswordOtp).
// sent 는 문자 발송이 끝났으면 true — 발송에 실패하면 이 응답 대신 UPSTREAM 오류가 나간다 (OtpService.request).
// expiresIn = 인증번호 유효 시간(OtpService.CODE_TTL, 5분), resendAfter = 다시 요청할 수 있을 때까지(RESEND_INTERVAL, 60초).
// 60초 안에 다시 요청하면 OTP_RATE_LIMITED(HTTP 429).
/** = 앱 requestPhoneVerification → { sent, expiresIn(초), resendAfter(초 — 이 시간 안의 재요청은 429) } */
public record OtpResponse(boolean sent, long expiresIn, long resendAfter) {}
