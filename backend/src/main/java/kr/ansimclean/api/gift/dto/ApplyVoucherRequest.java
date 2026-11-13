package kr.ansimclean.api.gift.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** POST /customer/bookings/{id}/voucher — 이 예약 결제에 쓸 내 금액권 (예약당 1장) */
public record ApplyVoucherRequest(@NotNull UUID voucherId) {}
