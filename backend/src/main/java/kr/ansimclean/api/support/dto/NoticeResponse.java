package kr.ansimclean.api.support.dto;

import java.util.UUID;

/** GET /support/notices/{id} 응답 — 공지 본문. date 는 서울 날짜 "2026.09.10" (두 앱의 getNotice·getManagerNotice) */
public record NoticeResponse(UUID id, String title, String date, String body) {}
