package kr.ansimclean.api.offer.dto;

import jakarta.validation.constraints.NotBlank;

// 콜 거절 요청 본문 { "reason": "…" }. 앱은 manager.js 의 rejectCall 로 보낸다.
// NotBlank = 비어 있거나 공백뿐이면 400 VALIDATION 으로 끝나고, 아래 message 가 화면에 보이는 오류 문구가 된다 (GlobalExceptionHandler).
// 사유가 관리 목록에 있는 문구인지는 OfferService.reject 가 따로 확인한다.
/** POST /manager/offers/{id}/reject — reason 은 catalog_reasons(kind=reject) 문구 */
public record RejectRequest(@NotBlank(message = "거절 사유를 골라주세요") String reason) {}
