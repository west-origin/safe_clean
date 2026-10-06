package kr.ansimclean.api.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /support/inquiries 본문 { content }.
 * SupportController 의 @Valid 가 검사한다 — 비었거나 2,000자를 넘으면 400(VALIDATION), 화면 문구는 아래 message.
 * SupportService.submit 이 앞뒤 공백을 뗀 뒤 같은 검사를 한 번 더 한다.
 */
public record InquiryRequest(@NotBlank(message = "문의 내용을 입력해주세요") @Size(max = 2000, message = "문의는 2,000자까지 쓸 수 있어요") String content) {}
