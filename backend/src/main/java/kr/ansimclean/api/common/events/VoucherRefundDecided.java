package kr.ansimclean.api.common.events;

import java.util.UUID;

// 아웃박스 이벤트 — 클래스 이름이 type 이다(바꾸지 말 것).
// 발행: gift.AdminVoucherRefundService — 어드민이 계좌 환불을 완료(송금)하거나 반려했을 때.
// 처리: push.PushHandler — 요청한 고객에게 (거래성, 토글과 무관). result = paid | rejected
/** 금액권 계좌 환불 처리 결과 */
public record VoucherRefundDecided(UUID refundId, UUID ownerId, String result) {}
