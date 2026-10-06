package kr.ansimclean.api.support.dto;

import java.util.UUID;

/** 목록 항목 — date 는 "2026.09.10" */
// GET /support/notices 목록의 한 줄 (두 앱의 getNotices·getManagerNotices). 본문(body)은 NoticeResponse 에만 있다
public record NoticeSummary(UUID id, String title, String date) {}
