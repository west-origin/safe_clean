package kr.ansimclean.api.offer.dto;

import java.util.UUID;

/**
 * POST /admin/cancel-requests/{id}/approve · reject 응답. status = "approved" | "rejected".
 * next (승인일 때) = "calling"(24시간 재배정 콜이 열림) | "awaitingAdmin"(곧바로 관리자 배정 대기) | null(그 사이 배정·취소됨),
 * reassignUntil = 재배정 콜 마감 ISO (calling 일 때만)
 */
public record CancelDecisionResponse(UUID id, UUID bookingId, String status, String next, String reassignUntil) {}
