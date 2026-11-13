package kr.ansimclean.api.gift.dto;

/**
 * GET /customer/vouchers/{id}/refund — 지금 환불하면. eligible 이면 method(card 카드 결제 취소 | bank 계좌)·rate(100|95)·amount,
 * 아니면 reason 에 이유 문구. needsAccount = 계좌를 입력해야 한다(bank). openStatus = 진행 중인 환불(processing|requested) 또는 null
 */
public record RefundQuoteResponse(boolean eligible, String method, Integer rate, long amount, String amountLabel, String reason,
                                  boolean needsAccount, String openStatus) {}
