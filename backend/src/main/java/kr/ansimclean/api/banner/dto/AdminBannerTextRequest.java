package kr.ansimclean.api.banner.dto;

/**
 * PUT /admin/banners/{id} 본문 — 대체 문구(제목·설명)와 게시 기간("2026-11-12", 비우거나 null 이면 제한 없음).
 * 검사는 AdminBannerService 가 한다(권한 검사 뒤에).
 */
public record AdminBannerTextRequest(String title, String description, String startsOn, String endsOn) {}
