package kr.ansimclean.api.banner.dto;

import java.util.UUID;

/**
 * 어드민 배너 한 장 (매니저 앱 배너 관리). imageUrl = 1시간짜리 서명 URL(실패하면 null).
 * startsOn·endsOn = "2026-11-12" 또는 null(제한 없음). periodLabel 예: "11월 12일 ~ 12월 31일" · "11월 12일부터" · "기간 제한 없음".
 * state = live(게시 중) | scheduled(게시 예정) | ended(게시 종료), stateLabel = 그 한국어.
 */
public record AdminBannerResponse(UUID id, String title, String description, String imageUrl, String startsOn, String endsOn,
                                  String periodLabel, String state, String stateLabel) {}
