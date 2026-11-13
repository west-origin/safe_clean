package kr.ansimclean.api.gift.dto;

/** 어드민이 송금할 때만 여는 계좌 원문 (열람은 activity_logs 에 남는다) */
public record AdminRefundAccountResponse(String bankName, String accountHolder, String accountNumber) {}
