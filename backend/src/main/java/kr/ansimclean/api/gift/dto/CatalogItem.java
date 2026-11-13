package kr.ansimclean.api.gift.dto;

/** 선물 목록 한 항목 (app_settings.gift_catalog). amountLabel = "69,000원" */
public record CatalogItem(String key, String group, String label, long amount, String amountLabel) {}
