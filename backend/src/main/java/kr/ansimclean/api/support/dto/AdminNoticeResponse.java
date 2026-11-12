package kr.ansimclean.api.support.dto;

import java.util.UUID;

/**
 * 어드민 공지 한 건 (매니저 앱 공지 관리). date 는 서울 날짜 "2026.09.10", audience = customer | manager | all.
 * 목록(GET /admin/notices)에서는 body 가 null 이다(목록에 본문을 싣지 않는다).
 */
public record AdminNoticeResponse(UUID id, String audience, String title, String date, String body) {}
