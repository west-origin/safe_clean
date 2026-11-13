package kr.ansimclean.api.offer.dto;

import java.util.List;
import java.util.UUID;

/**
 * GET /admin/reassignments 의 한 줄 — 관리자 배정 대기 예약 (docs/api.md '매니저 방문 취소 승인·재배정').
 * reason = "no_accept"(재배정 콜을 아무도 맡지 않음) | "imminent"(방문까지 24시간 미만) | "no_candidates"(후보 없음),
 * pendingSince = 대기 시작 ISO, visitAt = 방문 시각 ISO, visitLabel = "2026년 11월 20일 오전 10:00", address = [도로명, 상세],
 * customer = { name, phone("010-1234-5678") }, cancelledManager = 방문을 취소한 매니저(없으면 null),
 * requestedAt·approvedAt = 취소 요청·승인 시각 ISO, cancelReason = 취소 사유 라벨
 */
public record ReassignmentItem(UUID bookingId, String reason, String pendingSince, String visitAt, String visitLabel, List<String> address,
                               Customer customer, Manager cancelledManager, String requestedAt, String approvedAt, String cancelReason) {
    public record Customer(String name, String phone) {}

    public record Manager(UUID id, String name) {}
}
