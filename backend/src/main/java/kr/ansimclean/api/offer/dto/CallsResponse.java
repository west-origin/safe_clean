package kr.ansimclean.api.offer.dto;

import kr.ansimclean.api.schedule.dto.AvailabilityStatus;

import java.util.List;

// 매니저 앱 콜 탭의 응답 모양. OfferService.calls 가 만든다.
// tabBadge = 콜 개수(탭 배지 숫자), calls = 콜 카드 목록 (approved 가 false 면 비어 있다 — 수락할 수 없는 카드를 싣지 않는다, 문제 목록 6번),
// availability = 수신 상태 카드 { status, detail } (앱은 콜이 없을 때 빈 화면 아래에 보여 준다),
// approved = 지금 콜을 받을 수 있는 매니저인가 (승인됐고 정지·탈퇴가 아님 — ProfileService.isApprovedManager)
// 주의: 같은 이름의 홈 응답 HomeResponse.Manager.approved 는 정지를 보지 않는다(isApproved) — 정지된 매니저는 홈에서 true, 여기서 false 다
/** GET /manager/offers — pending 만, 만료 임박순 */
/** approved=false 면 availability 는 '승인 대기 중', 정지 중이면 '콜 배정 정지 중' (검증 검토) */
public record CallsResponse(int tabBadge, List<CallItem> calls, AvailabilityStatus availability, boolean approved) {}
