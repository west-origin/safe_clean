package kr.ansimclean.api.support.dto;

/**
 * POST·PUT /admin/notices 본문 — 대상(customer|manager|all)·제목·본문. 앞뒤 공백은 서버가 떼고 검사한다(AdminNoticeService).
 * 컨트롤러에서 @Valid 를 쓰지 않는다 — 권한 검사보다 본문 검증이 먼저 나가지 않게 서비스가 검사한다(AdminInquiryController 와 같은 순서).
 */
public record AdminNoticeRequest(String audience, String title, String body) {}
