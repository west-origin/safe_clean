package kr.ansimclean.api.gift.dto;

import java.util.UUID;

/**
 * 예약 결제에 쓰는 금액권. 쓰는 금액권이 없으면 voucherId·label null, appliedAmount 0.
 * remainingDue = 카드로 낼 금액(예약 금액 − 금액권). coveredByVoucher = 금액권이 전부 덮어 카드 결제가 필요 없다
 */
public record BookingVoucherResponse(UUID bookingId, UUID voucherId, String label, long appliedAmount, String appliedLabel,
                                     long remainingDue, String remainingLabel, boolean coveredByVoucher) {}
