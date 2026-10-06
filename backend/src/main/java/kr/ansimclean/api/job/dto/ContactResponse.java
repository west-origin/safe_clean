package kr.ansimclean.api.job.dto;

/** GET /manager/jobs/{id}/contact — 배정된 매니저에게, 작업 당일 ±1일에만 (그 외 404) */
// phone = 고객 번호 "010-1234-5678" 모양, mapQuery = 지도 앱에 넘길 검색어(예약의 도로명 주소). 만드는 곳: JobService.contact.
// 매니저 앱 작업 상세의 '고객 연락'·'지도 보기' 가 쓴다 (api/manager.js getJobContact — 404 면 null 로 바꿔 버튼을 끈다)
public record ContactResponse(String phone, String mapQuery) {}
