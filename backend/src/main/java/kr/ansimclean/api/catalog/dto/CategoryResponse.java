package kr.ansimclean.api.catalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** = 앱 mock.js BOOKING_CATEGORIES[category] ({ label, promo, promoText? }) */
// GET /catalog/categories/{category} 응답. CatalogService.category 가 ServiceCategory 행에서 만든다.
// @JsonInclude(NON_NULL) → 값이 null 인 칸은 JSON 에서 뺀다 (promoText 가 없는 카테고리는 그 칸이 아예 없다).
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CategoryResponse(String label, String promo, String promoText) {}
