package kr.ansimclean.api.profile;

import jakarta.validation.Valid;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.profile.dto.AddressRequest;
import kr.ansimclean.api.profile.dto.AddressResponse;
import kr.ansimclean.api.profile.dto.DefaultAddressResponse;
import kr.ansimclean.api.profile.dto.HousingRequest;
import kr.ansimclean.api.profile.dto.IdResponse;
import kr.ansimclean.api.profile.dto.NewAddressRequest;
import kr.ansimclean.api.profile.dto.NotificationRequest;
import kr.ansimclean.api.profile.dto.NotificationResponse;
import kr.ansimclean.api.profile.dto.ProfileFormResponse;
import kr.ansimclean.api.profile.dto.SuggestedNoticesResponse;
import kr.ansimclean.api.profile.dto.UpdateProfileRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 고객 프로필·주소·알림 (docs/api.md §5.2). 가입 화면의 PUT /profile/address·/profile/housing 은 가입 중(PENDING_CUSTOMER)에도 허용 (SecurityConfig).
 * 실제 경로 앞부분은 /api/v1/customer. 위 두 PUT 말고는 가입을 끝낸 고객(ROLE_CUSTOMER)만.
 * 모든 메서드가 토큰의 사용자 id(@AuthenticationPrincipal)로만 ProfileService 를 부른다 — 남의 주소 id 를 보내면 NOT_FOUND.
 * 부르는 쪽은 고객 앱 api/customer.js (예: saveProfileAddress, getAddresses — 메서드 설명의 괄호 안이 앱 함수 이름).
 */
@RestController
@RequestMapping(SecurityConfig.API + "/customer")
public class CustomerProfileController {
    private final ProfileService service;

    public CustomerProfileController(ProfileService service) {
        this.service = service;
    }

    /** 가입 Auth/ProfileAddress '다음' (앱 saveProfileAddress) — 기본 주소를 만들거나 바꾼다. 시·도를 못 읽으면 400 */
    @PutMapping("/profile/address")
    public AddressResponse address(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody AddressRequest req) {
        return service.saveDefaultAddress(user.id(), req);
    }

    /** 가입 Auth/ProfileHousing '다음' (앱 saveProfileHousing) — 주거 정보를 저장하고 받은 값을 그대로 돌려준다 */
    @PutMapping("/profile/housing")
    public HousingRequest housing(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody HousingRequest req) {
        return service.saveHousing(user.id(), req);
    }

    /** 설정 › 내 정보 수정의 현재 값 — 이름·닉네임·휴대폰 (앱 getProfileForm) */
    @GetMapping("/profile")
    public ProfileFormResponse profile(@AuthenticationPrincipal CurrentUser user) {
        return service.profileForm(user.id());
    }

    /** 예약 주의사항 화면이 미리 골라 둘 항목 (가입 때 받은 반려동물 여부) — 같은 것을 두 번 묻지 않게 (사용성 검토 2026-09-22) */
    @GetMapping("/profile/suggested-notices")
    public SuggestedNoticesResponse suggestedNotices(@AuthenticationPrincipal CurrentUser user) {
        return new SuggestedNoticesResponse(service.suggestedNotices(user.id()));
    }

    /** 설정 › 내 정보 수정 저장 (앱 updateProfile) — 이름·닉네임만 바꾸고 바뀐 값을 돌려준다 */
    @PatchMapping("/profile")
    public ProfileFormResponse updateProfile(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody UpdateProfileRequest req) {
        return service.updateProfile(user.id(), req.name(), req.nickname());
    }

    /** 설정 › 주소 관리 목록, 오래된 순 (앱 getAddresses) */
    @GetMapping("/addresses")
    public List<AddressResponse> addresses(@AuthenticationPrincipal CurrentUser user) {
        return service.addresses(user.id());
    }

    /** 설정 › 주소 추가 (앱 addAddress). 고객당 최대 개수를 넘거나 시·도가 없으면 400, 첫 주소면 기본 주소가 된다 */
    @PostMapping("/addresses")
    public AddressResponse addAddress(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody NewAddressRequest req) {
        return service.addAddress(user.id(), req);
    }

    /** 예약 "기존 주소를 사용할까요?" '네' 의 기본 주소 (앱 getSavedAddress). 없으면 404 — 앱이 null 로 바꾼다 */
    @GetMapping("/addresses/default")
    public DefaultAddressResponse defaultAddress(@AuthenticationPrincipal CurrentUser user) {
        return service.defaultAddress(user.id());
    }

    /** 주소 관리 '기본으로' (앱 setDefaultAddress) → 바뀐 주소 목록. 이미 기본이면 바꾸지 않고 목록만 돌려준다 */
    @PutMapping("/addresses/{id}/default")
    public List<AddressResponse> makeDefaultAddress(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return service.makeDefaultAddress(user.id(), id);
    }

    /** 주소 삭제 (앱 removeAddress) → 지운 id. 기본 주소를 지우면 남은 것 중 가장 오래된 주소가 기본이 된다 */
    @DeleteMapping("/addresses/{id}")
    public IdResponse removeAddress(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return service.removeAddress(user.id(), id);
    }

    /** 설정 › 알림 토글 저장 (앱 updateNotificationSetting). 고객이 바꿀 수 있는 key 는 push·marketing, 그 밖은 400 */
    @PutMapping("/notifications/{key}")
    public NotificationResponse notification(@AuthenticationPrincipal CurrentUser user, @PathVariable String key, @Valid @RequestBody NotificationRequest req) {
        return service.updateNotification(user.id(), key, req.enabled());
    }
}
