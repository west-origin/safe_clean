package kr.ansimclean.api.catalog.dto;

import tools.jackson.databind.JsonNode;

import java.util.Map;

/** = 앱 getConfirmCopy(category) → { copy: CONFIRM_COPY, frequencyDescriptions: { recurring, oneTime }, seriesTermMonths } */
// GET /catalog/categories/{category}/confirm-copy 응답 (CatalogService.confirmCopy).
// copy 는 app_settings.confirm_copy 의 JSON 을 모양 그대로 내보낸다(JsonNode). frequencyDescriptions 는 주기 → 설명 글자이고,
// 설명 행이 없는 주기는 칸이 없다 (예: 수리 카테고리는 oneTime 만).
// seriesTermMonths = 정기청소 개월 수 = 회차 수(app_settings.series_term_months) — 주기 화면 안내 'N개월 동안 N회' 가 쓴다 (문제 목록 25번).
public record ConfirmCopyResponse(JsonNode copy, Map<String, String> frequencyDescriptions, int seriesTermMonths) {}
