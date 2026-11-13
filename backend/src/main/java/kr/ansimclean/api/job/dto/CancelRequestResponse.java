package kr.ansimclean.api.job.dto;

import kr.ansimclean.api.job.JobCancelRequest;

import java.util.UUID;

/**
 * POST /manager/jobs/{id}/cancel 응답 — 방문 취소 **요청** 한 건 (docs/api.md '매니저 방문 취소 승인·재배정').
 * status = "pending"(대기) | "approved" | "rejected" | "closed", requestedAt = ISO 시각
 */
public record CancelRequestResponse(UUID id, UUID bookingId, String status, String requestedAt) {
    public static CancelRequestResponse of(JobCancelRequest r) {
        return new CancelRequestResponse(r.getId(), r.getBookingId(), r.getStatus(), r.getRequestedAt().toString());
    }
}
