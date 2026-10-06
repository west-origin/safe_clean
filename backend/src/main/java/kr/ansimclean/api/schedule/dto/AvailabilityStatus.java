package kr.ansimclean.api.schedule.dto;

// 콜 목록 응답(offer.dto.CallsResponse)에 실리는 하단 카드 한 장. 평소에는 AvailabilityService.status 가 만든다.
// OfferService 는 정지된 매니저면 "콜 배정 정지 중", 미승인 매니저면 "승인 대기 중" 을 같은 모양으로 직접 만든다.
/** 콜 화면 하단 카드: { status: "콜 수신 중" | "근무 시간 외", detail: "오늘 09:00 - 18:00 근무 설정됨" | "오늘 휴무" } (§6.6) */
public record AvailabilityStatus(String status, String detail) {}
