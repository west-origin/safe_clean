package kr.ansimclean.api.push.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// PUT·DELETE /api/v1/push/token 의 요청 본문 { platform, token } (PushController).
// @NotBlank: 비었거나 공백뿐이면 검증 오류(400). @Size(max = 4096): 토큰 길이 상한 — PushService.register 도 같은 상한을 다시 본다.
// platform 이 ios·android·web 중 하나인지는 여기서가 아니라 PushService.register 가 검사한다.
public record RegisterTokenRequest(@NotBlank String platform, @NotBlank @Size(max = 4096) String token) {}
