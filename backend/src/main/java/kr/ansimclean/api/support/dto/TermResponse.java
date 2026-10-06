package kr.ansimclean.api.support.dto;

/** = 앱 getTerm(id) / getManagerTerm(id) → { id, title, body } */
// GET /terms/{id}?audience=… 응답 (TermsService.term)
public record TermResponse(String id, String title, String body) {}
