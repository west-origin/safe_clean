package kr.ansimclean.api.support.dto;

/** = 앱 TERMS / MANAGER_TERMS 항목 { id, label, required } */
// GET /terms 목록 항목. 고객 응답은 이 배열 그대로, 매니저 응답은 ManagerTermsResponse.terms 에 담긴다
public record TermSummary(String id, String label, boolean required) {}
