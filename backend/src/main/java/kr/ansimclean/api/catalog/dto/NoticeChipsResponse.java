package kr.ansimclean.api.catalog.dto;

import tools.jackson.databind.JsonNode;

import java.util.List;

/** = 앱 getNoticeChips(category) → { chips: [{ key, label, icon }], copy: NOTES_COPY } */
// GET /catalog/categories/{category}/notice-chips 응답 (CatalogService.noticeChips). copy 는 app_settings.notes_copy 를
// 모양 그대로(JsonNode) 내보낸다 — 서버는 그 안의 칸을 읽지 않으므로 칸 모양은 app_settings 값과 앱 화면이 정한다.
public record NoticeChipsResponse(List<Chip> chips, JsonNode copy) {
    /** 칩 한 개: key(예약에 저장되는 값), label(화면 글자), icon(앱 NoticeChip 아이콘 키) */
    public record Chip(String key, String label, String icon) {}
}
