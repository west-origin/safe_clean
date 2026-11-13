package kr.ansimclean.api.gift.dto;

import java.util.List;

/** GET /customer/gifts?box=sent|received — 최근 100건 */
public record GiftsResponse(String box, List<GiftView> gifts) {}
