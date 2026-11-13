package kr.ansimclean.api.gift.dto;

import java.util.UUID;

/** 어드민 계좌 환불 목록 한 줄. 계좌번호는 끝 4자리만 — 전체는 /account 로 따로 연다(활동 이력) */
public record AdminVoucherRefundItem(UUID id, UUID voucherId, String voucherLabel, String ownerName, int rate, long baseAmount, long amount,
                                     String amountLabel, String bankName, String accountHolder, String accountLast4, String status, String requestedAt) {}
