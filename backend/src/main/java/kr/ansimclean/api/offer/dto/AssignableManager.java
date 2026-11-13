package kr.ansimclean.api.offer.dto;

import java.util.UUID;

/** GET /admin/bookings/{id}/assignable-managers 의 한 명. phone = "010-1234-5678"(없으면 null), available = 그 시간이 근무시간 안인가 */
public record AssignableManager(UUID id, String name, String phone, boolean available) {}
