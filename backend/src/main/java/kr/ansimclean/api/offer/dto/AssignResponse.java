package kr.ansimclean.api.offer.dto;

import java.util.UUID;

/** POST /admin/bookings/{id}/assign 응답 */
public record AssignResponse(UUID bookingId, UUID managerId) {}
