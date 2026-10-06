package kr.ansimclean.api.support.dto;

import java.util.UUID;

/** GET /support/faq 목록 항목 { id, question, answer } — 고객 앱 getFaq() 가 받는 모양 */
public record FaqResponse(UUID id, String question, String answer) {}
