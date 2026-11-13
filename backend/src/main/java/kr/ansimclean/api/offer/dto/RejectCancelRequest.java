package kr.ansimclean.api.offer.dto;

/** POST /admin/cancel-requests/{id}/reject 본문 { note } — 거절 메모(선택, 500자까지) */
public record RejectCancelRequest(String note) {}
