package kr.ansimclean.api.gift.dto;

import kr.ansimclean.api.payment.dto.CheckoutResponse;

/** POST /customer/gifts · GET /customer/gifts/{id}. checkout 은 보낸 사람이 결제를 아직 끝내지 않은 구매 선물일 때만 (토스 결제창에 쓸 값) */
public record GiftDetailResponse(GiftView gift, CheckoutResponse checkout) {}
