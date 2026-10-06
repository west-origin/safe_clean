package kr.ansimclean.api.support.dto;

import java.util.UUID;

/** POST /support/inquiries 응답 { id } — 접수된 문의 id. 같은 멱등 키 재시도에는 저장해 둔 이 응답이 그대로 다시 나간다 */
public record InquiryResponse(UUID id) {}
