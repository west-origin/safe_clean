package kr.ansimclean.api.banner;

import kr.ansimclean.api.banner.dto.HomeBannerResponse;
import kr.ansimclean.api.common.security.SecurityConfig;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** GET /customer/banners — 고객 앱 홈 혜택 배너 (getHomeBanners). 오늘 게시 중인 것만 순서대로. 고객만(SecurityConfig /customer/**) */
@RestController
@RequestMapping(SecurityConfig.API + "/customer/banners")
public class CustomerBannerController {
    private final BannerService service;

    public CustomerBannerController(BannerService service) {
        this.service = service;
    }

    @GetMapping
    public List<HomeBannerResponse> list() {
        return service.liveBanners();
    }
}
