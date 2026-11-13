package kr.ansimclean.api.gift.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * POST /customer/gifts — method: purchase(새로 구매, catalogKey 필수) | voucher(보유 금액권, voucherId 필수).
 * message 는 선택(200자까지). 금액은 받지 않는다 — 서버 선물 목록 값만 쓴다
 */
public record CreateGiftRequest(@NotNull UUID recipientId, @NotBlank String method, String catalogKey, UUID voucherId, @Size(max = 200) String message) {}
