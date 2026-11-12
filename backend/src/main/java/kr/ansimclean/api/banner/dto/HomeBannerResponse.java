package kr.ansimclean.api.banner.dto;

import java.util.UUID;

/** 고객 앱 홈 배너 한 장 (GET /customer/banners). imageUrl = 서명 URL(6시간). title·description 은 이미지의 대체 문구 */
public record HomeBannerResponse(UUID id, String title, String description, String imageUrl) {}
