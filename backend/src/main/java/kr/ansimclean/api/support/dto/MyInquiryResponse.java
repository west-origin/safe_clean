package kr.ansimclean.api.support.dto;

import java.util.UUID;

/** GET /support/inquiries — 내 문의와 답변. status = open | answered, answeredLabel 은 답변 전 null */
// createdLabel·answeredLabel 은 서울 시각 "9월 18일 오후 2:00" 모양 (SupportService.label).
// 운영자 답변 API(POST /admin/inquiries/{id}/answer)의 응답도 같은 모양이다
public record MyInquiryResponse(UUID id, String content, String status, String answer, String createdLabel, String answeredLabel) {}
