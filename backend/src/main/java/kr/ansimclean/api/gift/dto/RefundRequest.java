package kr.ansimclean.api.gift.dto;

import jakarta.validation.constraints.Size;

/** POST /customer/vouchers/{id}/refund — 계좌 환불이면 은행·계좌번호·예금주 필수(카드 결제 취소면 비워도 된다). 계좌번호는 서버가 암호화해 둔다 */
public record RefundRequest(@Size(max = 20) String bankName, @Size(max = 30) String accountNumber, @Size(max = 20) String accountHolder) {}
