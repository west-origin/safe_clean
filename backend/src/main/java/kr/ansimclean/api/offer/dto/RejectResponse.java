package kr.ansimclean.api.offer.dto;

import java.util.UUID;

/**
 * POST /manager/offers/{id}/reject 응답 { id, reason } — OfferService.reject 가 만든다.
 * id = 거절한 콜 id, reason = 저장된 거절 사유. 이미 닫힌 콜에 다시 거절하면 오류 대신 처음 저장된 사유를 돌려준다
 * (거절이 아니라 만료·취소·수락·무효(superseded)로 닫혔으면 reason 은 null)
 */
public record RejectResponse(UUID id, String reason) {}
