package kr.ansimclean.api.banner;

import kr.ansimclean.api.banner.dto.HomeBannerResponse;
import kr.ansimclean.api.common.time.AppClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * 고객 앱 홈 혜택 배너 (GET /customer/banners). 오늘(서울)이 게시 기간 안인 배너만 순서대로, 이미지는 6시간짜리 서명 URL.
 * 앱은 홈을 열 때마다 다시 부르므로 어드민이 바꾸면 다음 방문부터 보인다. 서명 URL 을 못 만든 배너는 빼고 준다.
 */
@Service
public class BannerService {
    static final int CUSTOMER_URL_SECONDS = 6 * 60 * 60; // 앱을 오래 켜 둬도 이미지가 깨지지 않게 (녹화본 재생 URL 과 같은 6시간)

    private final HomeBannerRepository banners;
    private final BannerImages images;
    private final AppClock clock;

    public BannerService(HomeBannerRepository banners, BannerImages images, AppClock clock) {
        this.banners = banners;
        this.images = images;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<HomeBannerResponse> liveBanners() {
        LocalDate today = clock.today();
        return banners.findAllByOrderBySortOrderAscCreatedAtAsc().stream()
                .filter(b -> b.isLiveOn(today))
                .map(b -> {
                    String url = images.url(b.getImagePath(), CUSTOMER_URL_SECONDS);
                    return url == null ? null : new HomeBannerResponse(b.getId(), b.getTitle(), b.getDescription(), url);
                })
                .filter(Objects::nonNull)
                .toList();
    }
}
