package kr.ansimclean.api.customer;

import kr.ansimclean.api.booking.BookingService;
import kr.ansimclean.api.catalog.AppSettingsService;
import kr.ansimclean.api.catalog.CatalogService;
import kr.ansimclean.api.customer.dto.HomeResponse;
import kr.ansimclean.api.customer.dto.RepairResponse;
import kr.ansimclean.api.customer.dto.SettingsResponse;
import kr.ansimclean.api.profile.ProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

// 부르는 곳: CustomerScreenController. 자기 테이블이 없고 예약은 BookingService, 프로필·알림 설정은 ProfileService,
// 화면 문구는 AppSettingsService, 변경·취소 기한은 CatalogService 에서 받아 화면 하나의 응답으로 묶는다.
/** 고객 앱의 홈·수리·설정 화면 (읽기 조합만. 문구는 app_settings, 프로필은 ProfileService) */
@Service
@Transactional(readOnly = true)
public class CustomerScreenService {
    // 고객 설정 화면에 보여 줄 알림 토글 키. 저장 쪽(ProfileService.updateNotification)도 고객이면 같은 두 키만 받는다
    static final List<String> CUSTOMER_NOTIFICATION_KEYS = List.of("push", "marketing");

    private final AppSettingsService settings;
    private final ProfileService profiles;
    private final BookingService bookings;
    private final CatalogService catalog;

    public CustomerScreenService(AppSettingsService settings, ProfileService profiles, BookingService bookings, CatalogService catalog) {
        this.settings = settings;
        this.profiles = profiles;
        this.bookings = bookings;
        this.catalog = catalog;
    }

    /**
     * 홈: app_settings 의 home_slides(JSON 그대로) + 이 고객의 가장 가까운 예정 방문 + 변경·취소 기한 시간.
     * 예정 방문이 없으면 upcoming 은 null 이다 (앱 홈은 그때 예약 카드를 그리지 않는다).
     * 기한 시간은 그 카드의 changeable 을 정한 것과 같은 값이라, 잠겼을 때 안내가 실제 기한을 말한다 (문제 목록 25번)
     */
    public HomeResponse home(UUID customerId) {
        return new HomeResponse(settings.node("home_slides"), bookings.nextVisit(customerId).orElse(null), catalog.cancelDeadlineHours());
    }

    /** 수리 탭: app_settings 의 repair_hero(JSON 그대로) */
    public RepairResponse repair() {
        return new RepairResponse(settings.node("repair_hero"));
    }

    /**
     * 설정 탭: 헤더 문구(settings_header)·프로필(이름 "○○ 님"·표시용 번호)·메뉴 그룹(settings_groups)·알림 토글 값.
     * 알림 값은 저장된 적 없으면 기본값(push 켬, marketing 끔)으로 채워진다 (ProfileService.notifications)
     */
    public SettingsResponse settings(UUID customerId) {
        return new SettingsResponse(settings.node("settings_header"), profiles.settingsProfile(customerId), settings.node("settings_groups"),
                profiles.notifications(customerId, CUSTOMER_NOTIFICATION_KEYS));
    }
}
