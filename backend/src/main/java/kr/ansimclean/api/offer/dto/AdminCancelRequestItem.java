package kr.ansimclean.api.offer.dto;

import java.util.UUID;

/**
 * GET /admin/cancel-requests 의 한 줄 — 대기 중인 매니저 방문 취소 요청 (docs/api.md '매니저 방문 취소 승인·재배정').
 * reason = 사유 라벨("질병·부상"), detail = 매니저가 적은 상세(없으면 null), requestedAt = ISO, requestedLabel = "11월 13일 오후 3:05",
 * visitLabel = 방문 일시 "2026년 11월 20일 오전 10:00", address = 도로명 주소, customerName = 고객 이름(어드민 + MFA 화면)
 */
public record AdminCancelRequestItem(UUID id, UUID bookingId, String managerName, String reason, String detail, String requestedAt, String requestedLabel,
                                     String visitLabel, String address, String customerName) {}
