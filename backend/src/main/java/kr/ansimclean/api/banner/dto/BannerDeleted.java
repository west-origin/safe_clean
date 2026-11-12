package kr.ansimclean.api.banner.dto;

/** DELETE /admin/banners/{id} 응답 { ok: true } */
public record BannerDeleted(boolean ok) {
    public static final BannerDeleted OK = new BannerDeleted(true);
}
