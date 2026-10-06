package kr.ansimclean.api.profile.dto;

import java.util.UUID;

/** POST /admin/penalties/{id}/waive → 운영자가 면제한 결과 (docs/manager-policy.md "증빙이 있으면 면제", 문제 목록 3번) */
// ManagerPenaltyService.waive 가 만든다. penaltyId = 면제한 원장 행, managerId = 그 매니저,
// points = 면제 뒤 누적 점수 글자(끝의 0 을 뗀다, 예 "3" · "4.5" — 매니저 앱 패널티 내역의 points 와 같은 값),
// suspendedUntil = 지금 걸려 있는 콜 배정 정지의 마지막 날 "YYYY-MM-DD"(그날까지 정지). 정지가 없거나 풀렸으면 null
public record PenaltyWaiveResponse(UUID penaltyId, UUID managerId, String points, String suspendedUntil) {}
