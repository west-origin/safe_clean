package kr.ansimclean.api.gift.dto;

import jakarta.validation.constraints.Size;

/** 어드민 환불 완료·반려 메모 (선택, 500자) */
public record RefundDecisionRequest(@Size(max = 500) String note) {}
