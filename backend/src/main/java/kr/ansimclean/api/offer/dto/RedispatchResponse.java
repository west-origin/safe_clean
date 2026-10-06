package kr.ansimclean.api.offer.dto;

import java.util.UUID;

// 운영자 재매칭(AdminOfferController) 응답 모양. OfferService.redispatchByAdmin 이 만든다. bookingId = 다시 매칭한 예약 id
/** POST /admin/bookings/{id}/redispatch → { bookingId, offered } — offered = 새로 보낸 오퍼 수(0 이면 다시 매칭 실패, 고객에게 또 알린다) */
public record RedispatchResponse(UUID bookingId, int offered) {}
