package kr.ansimclean.api.banner.dto;

import java.util.List;
import java.util.UUID;

/** PUT /admin/banners/order 본문 — 모든 배너 id 를 새 순서대로 (빠지거나 남는 id 가 있으면 VALIDATION) */
public record BannerOrderRequest(List<UUID> ids) {}
