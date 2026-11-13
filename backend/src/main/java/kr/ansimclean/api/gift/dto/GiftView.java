package kr.ansimclean.api.gift.dto;

import kr.ansimclean.api.friend.dto.FriendUser;

import java.util.UUID;

/**
 * 선물 한 건. direction: sent(내가 보냄 — person 은 받는 사람) | received(내가 받음 — person 은 보낸 사람).
 * method: purchase | voucher. status: awaiting_payment | sent | accepted | rejected | expired | cancelled | payment_failed.
 * 시각은 ISO-8601(없으면 null). expiresLabel = "11월 20일 14:32까지" (sent 일 때만)
 */
public record GiftView(UUID id, String direction, String method, String status, long amount, String amountLabel, String label, String message,
                       FriendUser person, String createdAt, String sentAt, String expiresAt, String expiresLabel, String decidedAt) {}
