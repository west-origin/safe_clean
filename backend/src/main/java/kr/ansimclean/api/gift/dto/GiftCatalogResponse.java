package kr.ansimclean.api.gift.dto;

import java.util.List;

/** GET /customer/gifts/catalog — 새로 구매해 선물할 수 있는 항목 + 받는 기간(일)·유효기간(일) 안내용 숫자 */
public record GiftCatalogResponse(List<CatalogItem> items, int acceptDays, int validDays) {}
