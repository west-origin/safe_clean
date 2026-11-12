package kr.ansimclean.api.banner;

import kr.ansimclean.api.banner.dto.HomeBannerResponse;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.supabase.SupabaseStorageClient;
import kr.ansimclean.api.common.time.AppClock;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 고객 앱 홈 배너 (GET /customer/banners): 오늘(서울) 게시 기간 안인 것만 순서대로, 서명 URL 을 못 만든 배너는 뺀다 */
class BannerServiceTest {
    static final Instant NOW = Instant.parse("2026-11-12T15:30:00Z"); // UTC 로는 12일이지만 서울은 11월 13일 00:30
    HomeBannerRepository repo = mock(HomeBannerRepository.class);
    SupabaseStorageClient storage = mock(SupabaseStorageClient.class);
    BannerService service = new BannerService(repo, new BannerImages(storage), new AppClock(Clock.fixed(NOW, AppClock.ZONE)));

    static HomeBanner banner(String title, LocalDate startsOn, LocalDate endsOn, int order) {
        UUID id = UUID.randomUUID();
        return HomeBanner.create(id, title, "설명", id + "/a.png", startsOn, endsOn, order, NOW);
    }

    @Test
    void 게시_기간_안인_배너만_순서대로_서명_URL_과_함께() {
        HomeBanner always = banner("항상", null, null, 1);
        HomeBanner endedYesterday = banner("어제 끝", null, LocalDate.of(2026, 11, 12), 2); // 서울 기준 어제
        HomeBanner startsToday = banner("오늘 시작", LocalDate.of(2026, 11, 13), null, 3);
        HomeBanner brokenUrl = banner("주소 실패", null, null, 4);
        when(repo.findAllByOrderBySortOrderAscCreatedAtAsc()).thenReturn(List.of(always, endedYesterday, startsToday, brokenUrl));
        when(storage.signedUrl(eq(BannerImages.BUCKET), anyString(), eq(BannerService.CUSTOMER_URL_SECONDS))).thenReturn("https://signed");
        when(storage.signedUrl(BannerImages.BUCKET, brokenUrl.getImagePath(), BannerService.CUSTOMER_URL_SECONDS)).thenThrow(new ApiException(ErrorCode.UPSTREAM));
        List<HomeBannerResponse> res = service.liveBanners();
        assertThat(res).extracting(HomeBannerResponse::title).containsExactly("항상", "오늘 시작");
        assertThat(res.get(0).imageUrl()).isEqualTo("https://signed");
        assertThat(res.get(0).description()).isEqualTo("설명");
    }
}
