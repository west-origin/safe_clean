package kr.ansimclean.api.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /admin/inquiries/{id}/answer */
// 요청 본문 JSON { "answer": "…" }.
// @NotBlank(빈 글 금지)·@Size(최대 4,000자)는 AdminInquiryController 가 토큰 검사 뒤 Validator 로 검사한다
public record AnswerRequest(@NotBlank @Size(max = 4000) String answer) {}
