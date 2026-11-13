package kr.ansimclean.api.gift.dto;

import java.util.UUID;

/** 환불 접수 결과. method card → status processing(카드 취소 중), bank → requested(운영자 송금 대기) */
public record RefundResultResponse(UUID refundId, String method, String status, int rate, long amount, String amountLabel) {}
