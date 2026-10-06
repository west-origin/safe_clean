package kr.ansimclean.api.profile;

import jakarta.validation.Valid;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.profile.dto.ManagerNotificationsResponse;
import kr.ansimclean.api.profile.dto.ManagerProfileResponse;
import kr.ansimclean.api.profile.dto.NotificationRequest;
import kr.ansimclean.api.profile.dto.NotificationResponse;
import kr.ansimclean.api.profile.dto.ProfileDetailResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 매니저 앱의 MY(프로필) 화면들과 작업 상세의 방문 취소 시트(JobDetail)가 부르는 API.
// 앱 쪽 함수는 api/manager.js 에 있다 (예: getProfile, getCancelReasons).
// 여기는 입출력만 하고, 규칙은 ProfileService·ManagerPenaltyService 에 있다.
// 경로에 사용자 id 가 없다 — 사용자별 데이터는 로그인 토큰의 사용자 것만 준다 (취소 사유 목록은 모두에게 같다).
/** 매니저 프로필·알림 (docs/api.md §5.3). /manager/** 는 ROLE_MANAGER */
@RestController
// 경로 앞부분: /api/v1/manager (SecurityConfig.API = "/api/v1")
@RequestMapping(SecurityConfig.API + "/manager")
public class ManagerProfileController {
    private final ProfileService service;
    private final ManagerPenaltyService penalties;

    public ManagerProfileController(ProfileService service, ManagerPenaltyService penalties) {
        this.service = service;
        this.penalties = penalties;
    }

    /** MY › 패널티 내역 (docs/manager-policy.md) */
    // @AuthenticationPrincipal CurrentUser = 로그인 토큰에서 꺼낸 지금 사용자 (본인 것만 본다)
    @GetMapping("/penalties")
    public kr.ansimclean.api.profile.dto.PenaltyResponse penalties(@AuthenticationPrincipal CurrentUser user) {
        return penalties.screen(user.id());
    }

    /** 방문 취소 사유 목록 (앱 시트) */
    @GetMapping("/cancel-reasons")
    public tools.jackson.databind.JsonNode cancelReasons() {
        return penalties.reasons(); // app_settings 의 [{key, label}] 배열을 그대로 준다 (사용자와 무관)
    }

    /** GET /manager/profile — MY 화면 머리: "이름 매니저", 평점·완료 청소 수, 메뉴 목록(app_settings) */
    @GetMapping("/profile")
    public ManagerProfileResponse profile(@AuthenticationPrincipal CurrentUser user) {
        return service.managerProfile(user.id());
    }

    /** GET /manager/profile/{key} — MY 하위 화면(읽기 전용). key = info·area·identity·education·account, 그 밖은 404 */
    @GetMapping("/profile/{key}")
    public ProfileDetailResponse detail(@AuthenticationPrincipal CurrentUser user, @PathVariable String key) {
        return service.managerProfileDetail(user.id(), key);
    }

    /** GET /manager/notifications — 알림 설정 화면: 행 목록(app_settings)과 켜짐 여부 */
    @GetMapping("/notifications")
    public ManagerNotificationsResponse notifications(@AuthenticationPrincipal CurrentUser user) {
        return service.managerNotifications(user.id());
    }

    /** PUT /manager/notifications/{key} — 알림 한 항목을 켜고 끈다. 본문 { enabled }. 매니저 키 목록 밖이면 400 */
    // enabled 가 빠지면 NotificationRequest 의 @NotNull 에 걸려 400 이다
    @PutMapping("/notifications/{key}")
    public NotificationResponse notification(@AuthenticationPrincipal CurrentUser user, @PathVariable String key, @Valid @RequestBody NotificationRequest req) {
        return service.updateNotification(user.id(), key, req.enabled());
    }
}
