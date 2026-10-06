package kr.ansimclean.api.catalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** = 앱 getBookingOptions(category) → { free: [{ key, label, icon }], paid: [{ key, label, price: "+3,000원", icon }], freeLimit } */
// GET /catalog/categories/{category}/options 응답 (CatalogService.options). freeLimit 은 무료 옵션을 몇 개까지 고를 수 있나
// (app_settings.free_option_limit). 견적·예약 생성 때 CatalogService.requireOptions 가 같은 값으로 다시 검사한다.
public record OptionsResponse(List<Option> free, List<Option> paid, int freeLimit) {
    /**
     * 옵션 한 개. price 는 유료만 있는 표시용 글자("+3,000원"), 무료는 null 이라 @JsonInclude(NON_NULL) 로 JSON 에서 빠진다.
     * icon 은 앱 옵션 아이콘 목록(ASSETS.bookingOptionIcons[category])의 번호다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Option(String key, String label, String price, int icon) {}
}
