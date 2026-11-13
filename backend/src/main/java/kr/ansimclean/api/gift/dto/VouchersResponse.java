package kr.ansimclean.api.gift.dto;

import java.util.List;

/** GET /customer/vouchers — 내 금액권 (보유·선물 중), 최근 순 */
public record VouchersResponse(List<VoucherView> vouchers) {}
