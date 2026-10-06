package kr.ansimclean.api.support.dto;

import java.util.List;

/** = 앱 getManagerTerms() → { terms, note } */
// GET /terms?audience=manager 응답 (TermsService.managerTerms). terms = 약관 목록, note = 목록 아래 안내 문구
// (app_settings 의 manager_privacy_note). 고객 응답은 이 객체 없이 목록 배열만 나간다
public record ManagerTermsResponse(List<TermSummary> terms, String note) {}
