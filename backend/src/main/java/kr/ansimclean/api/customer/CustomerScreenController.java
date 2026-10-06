package kr.ansimclean.api.customer;

import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.customer.dto.HomeResponse;
import kr.ansimclean.api.customer.dto.RepairResponse;
import kr.ansimclean.api.customer.dto.SettingsResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 고객 앱의 탭 화면 세 개가 여는 읽기 전용 API. 앱 쪽은 apps/customer/src/api/customer.js 의
// getHome()(Home 화면)·getRepair()(Repair 화면)·getSettings()(Settings 화면)가 부른다.
// 경로 앞부분은 /api/v1/customer. /customer/** 는 SecurityConfig 가 가입을 끝낸 고객(ROLE_CUSTOMER)만 통과시킨다.
/** GET /customer/home · /repair · /settings (docs/api.md §5.2) */
@RestController
@RequestMapping(SecurityConfig.API + "/customer")
public class CustomerScreenController {
    private final CustomerScreenService service;

    public CustomerScreenController(CustomerScreenService service) {
        this.service = service;
    }

    /**
     * GET /api/v1/customer/home — 홈 슬라이드 + 가장 가까운 예정 방문 1건.
     * {@code @AuthenticationPrincipal}: 로그인 토큰에서 꺼낸 지금 사용자(CurrentUser)를 인자로 받는다
     */
    @GetMapping("/home")
    public HomeResponse home(@AuthenticationPrincipal CurrentUser user) {
        return service.home(user.id());
    }

    /** GET /api/v1/customer/repair — 수리 탭 상단(히어로) 문구·이미지 이름. 모든 고객에게 같은 값이라 사용자 정보를 받지 않는다 */
    @GetMapping("/repair")
    public RepairResponse repair() {
        return service.repair();
    }

    /** GET /api/v1/customer/settings — 설정 탭: 헤더 문구·내 프로필·메뉴 그룹·알림 토글 값 */
    @GetMapping("/settings")
    public SettingsResponse settings(@AuthenticationPrincipal CurrentUser user) {
        return service.settings(user.id());
    }
}
