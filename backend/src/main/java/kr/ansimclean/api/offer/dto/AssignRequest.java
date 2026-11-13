package kr.ansimclean.api.offer.dto;

import java.util.UUID;

/** POST /admin/bookings/{id}/assign 본문 { managerId } */
public record AssignRequest(UUID managerId) {}
