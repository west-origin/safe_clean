package kr.ansimclean.api.profile;

import kr.ansimclean.api.catalog.AppSettingsService;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.Roles;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.common.format.KoreanFormat;
import kr.ansimclean.api.profile.dto.AddressRequest;
import kr.ansimclean.api.profile.dto.AddressResponse;
import kr.ansimclean.api.profile.dto.DefaultAddressResponse;
import kr.ansimclean.api.profile.dto.HousingRequest;
import kr.ansimclean.api.profile.dto.IdResponse;
import kr.ansimclean.api.profile.dto.ManagerNotificationsResponse;
import kr.ansimclean.api.profile.dto.ManagerProfileResponse;
import kr.ansimclean.api.profile.dto.NewAddressRequest;
import kr.ansimclean.api.profile.dto.NotificationResponse;
import kr.ansimclean.api.profile.dto.ProfileDetailResponse;
import kr.ansimclean.api.profile.dto.ProfileFormResponse;
import kr.ansimclean.api.profile.dto.SettingsProfile;
import kr.ansimclean.api.profile.dto.SignupStep;
import kr.ansimclean.api.profile.dto.UserResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 프로필 기능의 서비스. 다루는 테이블: profiles·addresses·customer_housing·notification_settings·manager_profiles.
// 부르는 곳 예: 고객·매니저 프로필 컨트롤러(가입·설정·MY 화면), AuthService(가입·로그인·탈퇴), CurrentUserCache(요청마다 사용자 확인).
// 다른 기능(예: 예약·콜)도 이름·승인 여부·탈퇴 잠금을 여기서 받는다.
// 가입·주소·주거·탈퇴를 바꾸는 메서드는 끝에 cache.evict 로 CurrentUserCache(1분 캐시)의 이 사용자 항목을 비운다.
// 주소·주거·이름·알림을 저장하는 메서드는 첫 줄에서 holdLive 를 불러 탈퇴와 줄을 선다 — 탈퇴 뒤에 커밋된 쓰기가 남지 않게.
// 예외: 주소 삭제(removeAddress)는 holdLive 를 부르지 않는다. 가입 완료(completeSignup)는 여기가 아니라 부르는 AuthService.completeSignup 이
// 먼저 lockLive(FOR UPDATE)로 잠가 탈퇴와 줄을 선다 (문제 목록 28번).
/** 프로필·주소·주거·알림 설정. 다른 기능은 이 Service 를 부른다(Repository 직접 접근 금지, CLAUDE.md §11-1) */
@Service
// 클래스 전체 트랜잭션(CODE_GUIDE.md 3-3). 다른 서비스가 부르면 그쪽 묶음에 합쳐진다 — 그래서 holdLive·lockLive·lockManager 의 잠금은
// 부른 쪽 묶음이 끝날 때까지 유지되고, 여기서 던진 ApiException(예: holdLive 의 401)을 부른 쪽이 잡아 삼켜도 그 묶음은 커밋 때 실패한다
@Transactional
public class ProfileService {
    private final ProfileRepository profiles;
    private final AddressRepository addresses;
    private final CustomerHousingRepository housing;
    private final NotificationSettingRepository notifications;
    private final ManagerProfileRepository managerProfiles;
    private final CurrentUserCache cache;
    private final AppSettingsService settings;
    private final AppClock clock;
    private final JsonMapper json = JsonMapper.builder().build(); // 매니저 교육 이수 기록(jsonb 문자열)을 읽는 데 쓴다 (educationRows)

    public ProfileService(ProfileRepository profiles, AddressRepository addresses, CustomerHousingRepository housing,
                          NotificationSettingRepository notifications, ManagerProfileRepository managerProfiles, CurrentUserCache cache,
                          AppSettingsService settings, AppClock clock) {
        this.profiles = profiles;
        this.addresses = addresses;
        this.housing = housing;
        this.notifications = notifications;
        this.managerProfiles = managerProfiles;
        this.cache = cache;
        this.settings = settings;
        this.clock = clock;
    }

    /** 탈퇴하지 않은 프로필을 찾는다. 없거나 탈퇴했으면 빈 Optional */
    @Transactional(readOnly = true)
    public Optional<Profile> findLive(UUID id) {
        return profiles.findByIdAndDeletedAtIsNull(id);
    }

    /** findLive 와 같지만 없거나 탈퇴했으면 NOT_FOUND(404) 를 던진다 */
    public Profile requireLive(UUID id) {
        return findLive(id).orElseThrow(ApiException::notFound);
    }

    /** 탈퇴(익명화)한 계정의 id 인가 — 가입 (a) 경로가 옛 id 를 재사용하지 않게 */
    @Transactional(readOnly = true)
    public boolean isDeleted(UUID id) {
        return profiles.findById(id).map(p -> p.getDeletedAt() != null).orElse(false);
    }

    /** 이 번호(+82 로 시작하는 국제 형식)를 쓰는 살아 있는 계정이 있는가 — AuthService 가 가입 인증번호 요청·가입에서 PHONE_TAKEN 을 판단한다 */
    @Transactional(readOnly = true)
    public boolean phoneInUse(String e164) {
        return profiles.findByPhoneAndDeletedAtIsNull(e164).isPresent();
    }

    /** 비밀번호 재설정: 번호로 살아 있는 계정을 찾는다 */
    @Transactional(readOnly = true)
    public Optional<Profile> findLiveByPhone(String e164) {
        return profiles.findByPhoneAndDeletedAtIsNull(e164);
    }

    /** 가입 1단계: Supabase 사용자가 만들어진 뒤 profiles 행 */
    public Profile createForSignup(UUID id, String role, String name, String nickname, String phone) {
        Profile p = profiles.save(Profile.create(id, role, name, nickname, phone));
        cache.evict(id); // 캐시에 남았을 수 있는 '프로필 없음' 결과를 비워 다음 요청이 새 행을 보게
        return p;
    }

    /** 가입 마지막 단계: 완료 시각 + 알림 설정 기본값 + (매니저) manager_profiles */
    public Profile completeSignup(UUID id) {
        return completeSignup(id, false);
    }

    /**
     * @param marketingAgreed 가입 때 '(선택) 마케팅 정보 수신 동의' 를 켰는가 — 설정 화면 토글의 초기값(동의 기록과 어긋나지 않게, 완결성 검토).
     * 조건: 탈퇴와 겹칠 수 있는 요청 경로에서는 부르는 쪽이 같은 트랜잭션에서 먼저 lockLive 로 잠근다(AuthService.completeSignup) — 여기의 requireLive 는 잠그지 않는다
     */
    public Profile completeSignup(UUID id, boolean marketingAgreed) {
        Profile p = requireLive(id);
        p.completeSignup(clock.now());
        List<String> keys = p.getRole().equals(Roles.MANAGER) ? MANAGER_NOTIFICATION_KEYS : List.of("push", "marketing");
        // 이미 있는 키는 그대로 둔다 — 두 번 불려도 행이 겹치거나 사용자가 바꾼 값이 덮이지 않게
        for (String key : keys) {
            if (!notifications.existsById(new NotificationSetting.Key(id, key))) {
                notifications.save(NotificationSetting.of(id, key, !key.equals("marketing") || marketingAgreed)); // 마케팅은 가입 때 동의했을 때만 켜짐 (앱 NOTIFICATION_SETTINGS)
            }
        }
        // 매니저는 manager_profiles 행을 미승인 상태로 만든다 — 운영자가 승인하기 전에는 콜 후보가 아니다
        if (p.getRole().equals(Roles.MANAGER) && !managerProfiles.existsById(id)) {
            managerProfiles.save(ManagerProfile.create(id, clock.now()));
        }
        cache.evict(id);
        return p;
    }

    /** 가입 도중이면 다음 화면, 끝났으면 null (BACKEND_PLAN.md §7.3) */
    @Transactional(readOnly = true)
    public SignupStep signupStep(Profile p) {
        if (p.isSignupComplete()) return null;
        // 고객은 주소 → 주거 정보 순서로 빠진 것을 찾는다. 둘 다 있거나 매니저면 마지막 단계(complete)
        if (p.getRole().equals(Roles.CUSTOMER)) {
            if (!addresses.existsByCustomerId(p.getId())) return SignupStep.ADDRESS;
            if (!housing.existsById(p.getId())) return SignupStep.HOUSING;
        }
        return SignupStep.COMPLETE;
    }

    /** 앱 session.user 모양의 응답 — AuthService 가 인증 응답(예: 로그인, GET /auth/me)에 넣는다 */
    @Transactional(readOnly = true)
    public UserResponse userResponse(Profile p) {
        SignupStep step = signupStep(p);
        return new UserResponse(p.getId(), p.getRole(), p.getName(), p.isSignupComplete(), step == null ? null : step.value());
    }

    /** 가입 Auth/ProfileAddress: 기본 주소를 만들거나(없으면) 바꾼다 */
    public AddressResponse saveDefaultAddress(UUID customerId, AddressRequest req) {
        holdLive(customerId); // 탈퇴와 직렬화 (감사 M-06)
        String road = req.roadAddress().trim();
        AddressRegion.requireMatchable(road); // 시·도 없는 주소는 예약이 매칭되지 않는다 (직접 사용 점검 U1)
        String detail = blankToNull(req.detailAddress());
        // 기본 주소가 있으면 그 주소를 고치고(다시 저장해도 주소가 늘지 않는다), 없으면 '집' 이름으로 기본 주소를 만든다
        Address a = addresses.findByCustomerIdAndIsDefaultTrue(customerId)
                .map(existing -> {
                    existing.update(road, detail);
                    return existing;
                })
                .orElseGet(() -> addresses.save(Address.create(customerId, "집", road, detail, true, clock.now())));
        cache.evict(customerId);
        return new AddressResponse(a.getId(), a.getLabel(), a.getRoadAddress(), a.getDetailAddress(), a.isDefault());
    }

    /**
     * 예약 주의사항 화면이 미리 골라 둘 항목 — 지금은 가입 때 받은 반려동물 여부뿐.
     * 같은 것을 두 번 묻지 않게 (사용성 검토 2026-09-22). 고객이 화면에서 끌 수 있다.
     */
    @Transactional(readOnly = true)
    public List<String> suggestedNotices(UUID customerId) {
        // 주거 정보가 있고 반려동물이 "yes" 면 ["pet"], 아니면 빈 목록
        return housing.findById(customerId).filter(h -> "yes".equals(h.getPet())).map(h -> List.of("pet")).orElseGet(List::of);
    }

    /** 가입 Auth/ProfileHousing */
    // 거주형태·평수·방·화장실·반려동물을 저장한다 (있으면 고치고 없으면 새 행). 받은 요청을 그대로 돌려준다
    public HousingRequest saveHousing(UUID customerId, HousingRequest req) {
        holdLive(customerId); // 탈퇴와 직렬화 (감사 M-06)
        CustomerHousing h = housing.findById(customerId).orElseGet(() -> CustomerHousing.create(customerId));
        h.update(req.housingType(), req.size(), req.sizeUnknown(), req.rooms(), req.bathrooms(), req.pet(), clock.now());
        housing.save(h);
        cache.evict(customerId);
        return req;
    }

    // ── 고객 설정 화면 (B3) ──

    /** Settings › ProfileCard */
    @Transactional(readOnly = true)
    public SettingsProfile settingsProfile(UUID id) {
        Profile p = requireLive(id);
        // CustomerScreenService 가 설정 화면 응답에 넣는다. 프로필 사진(avatarSrc)은 지금 항상 빈 문자열이다
        return new SettingsProfile(p.getName() + " 님", KoreanFormat.phoneDisplay(p.getPhone()), "");
    }

    /** Settings › 알림 토글 현재 값 { push: true, marketing: false } (없는 키는 기본값으로 채운다) */
    @Transactional(readOnly = true)
    public Map<String, Boolean> notifications(UUID id, List<String> keys) {
        // PushHandler 도 푸시를 보내기 전에 이 값으로 켜짐 여부를 본다
        Map<String, Boolean> out = new LinkedHashMap<>(); // 넣은 순서(keys 순서)를 지키는 Map — JSON 도 이 순서로 나간다
        for (String key : keys) out.put(key, !key.equals("marketing")); // 기본값: marketing 만 꺼짐, 나머지는 켜짐
        // 저장된 값이 있으면 덮어쓴다. 요청한 keys 밖의 행은 무시한다
        for (NotificationSetting n : notifications.findByUserId(id)) {
            if (out.containsKey(n.getKey())) out.put(n.getKey(), n.isEnabled());
        }
        return out;
    }

    /** Settings › 알림 토글 저장. key 는 역할별 목록 안에 있어야 한다 */
    public NotificationResponse updateNotification(UUID id, String key, boolean enabled) {
        Profile p = holdLive(id); // 탈퇴(익명화)와 직렬화 — 탈퇴가 지운 알림 설정 행을 다시 만들지 않게 (감사 M-06)
        List<String> allowed = p.getRole().equals(Roles.MANAGER) ? MANAGER_NOTIFICATION_KEYS : List.of("push", "marketing");
        if (!allowed.contains(key)) throw ApiException.validation("알 수 없는 알림 설정이에요: " + key);
        // 행이 있으면 값만 바꾸고, 없으면 새로 만든다. 고객·매니저 컨트롤러가 같이 쓴다
        NotificationSetting n = notifications.findById(new NotificationSetting.Key(id, key)).orElseGet(() -> NotificationSetting.of(id, key, enabled));
        n.setEnabled(enabled);
        notifications.save(n);
        return new NotificationResponse(key, enabled);
    }

    /** Settings › 내 정보 수정 — 현재 값 */
    @Transactional(readOnly = true)
    public ProfileFormResponse profileForm(UUID id) {
        Profile p = requireLive(id);
        // phoneLocal: 국제 형식 번호에서 앞 "+8210" 을 뗀 8자리 (앱 PhoneField 형식)
        return new ProfileFormResponse(p.getName(), p.getNickname(), KoreanFormat.phoneLocal(p.getPhone()));
    }

    /** Settings › 내 정보 수정 — 저장 (이름·닉네임만. 휴대폰은 §13-7) */
    public ProfileFormResponse updateProfile(UUID id, String name, String nickname) {
        Profile p = holdLive(id); // 탈퇴(익명화)와 직렬화 — 익명화한 이름을 되돌려 쓰지 않게 (감사 M-06)
        p.rename(name.trim(), nickname == null ? null : nickname.trim()); // 닉네임이 null 이면 기존 닉네임을 그대로 둔다 (Profile.rename)
        return profileForm(id);
    }

    /** Settings › 주소 관리 목록 */
    @Transactional(readOnly = true)
    public List<AddressResponse> addresses(UUID customerId) {
        // 만든 순서(오래된 것 먼저)로
        return addresses.findByCustomerIdOrderByCreatedAt(customerId).stream().map(ProfileService::toResponse).toList();
    }

    /** 고객 한 명이 저장할 수 있는 주소 수 (코드에서 정함 — 끝없이 늘릴 수 있었다, 전체 재점검 P4) */
    static final int MAX_ADDRESSES = 20;

    /** Settings › 주소 추가. 첫 주소면 기본 주소가 된다 */
    public AddressResponse addAddress(UUID customerId, NewAddressRequest req) {
        holdLive(customerId); // 탈퇴와 직렬화 (감사 M-06)
        if (addresses.countByCustomerId(customerId) >= MAX_ADDRESSES) throw ApiException.validation("주소는 " + MAX_ADDRESSES + "개까지 저장할 수 있어요"); // (코드에서 정함, 전체 재점검 P4)
        AddressRegion.requireMatchable(req.roadAddress()); // (직접 사용 점검 U1)
        boolean first = !addresses.existsByCustomerId(customerId); // 저장된 주소가 하나도 없으면 이 주소가 기본 주소
        // label 이 비었으면 Address.create 가 "기타" 로 채운다. 시·도·시군구도 도로명주소에서 뽑아 같이 저장한다
        Address a = addresses.save(Address.create(customerId, req.label(), req.roadAddress().trim(), blankToNull(req.detailAddress()), first, clock.now()));
        cache.evict(customerId);
        return toResponse(a);
    }

    /** Settings › 주소 삭제. 기본 주소를 지우면 남은 것 중 가장 오래된 주소가 기본이 된다 (docs/api.md) */
    public IdResponse removeAddress(UUID customerId, UUID addressId) {
        // 남의 주소 id 면 없는 것과 똑같이 NOT_FOUND — 다른 고객의 주소가 있는지 드러내지 않는다
        Address a = addresses.findById(addressId).filter(x -> x.getCustomerId().equals(customerId)).orElseThrow(ApiException::notFound);
        boolean wasDefault = a.isDefault();
        addresses.delete(a);
        addresses.flush(); // 부분 유니크 인덱스(addresses_one_default) 때문에 삭제가 먼저 반영돼야 다음 기본 주소를 켤 수 있다
        if (wasDefault) {
            addresses.findByCustomerIdOrderByCreatedAt(customerId).stream().findFirst().ifPresent(next -> next.makeDefault(true));
        }
        cache.evict(customerId);
        return new IdResponse(addressId);
    }

    /**
     * Settings › 주소 관리 '기본으로' — 예약의 "기존 주소를 사용할까요?" 가 쓰는 주소를 바꾼다.
     * 어느 주소가 기본인지 보이지 않고 바꿀 길도 없어, 가입 주소를 고치려면 지우는 수밖에 없었다 (직접 사용 점검 U15)
     * 이미 기본이면 아무것도 바꾸지 않는다. 결과로 바뀐 주소 목록 전체를 돌려준다
     */
    public List<AddressResponse> makeDefaultAddress(UUID customerId, UUID addressId) {
        holdLive(customerId); // 탈퇴와 직렬화 (감사 M-06)
        Address target = addresses.findById(addressId).filter(x -> x.getCustomerId().equals(customerId)).orElseThrow(ApiException::notFound);
        if (!target.isDefault()) {
            addresses.findByCustomerIdAndIsDefaultTrue(customerId).ifPresent(current -> {
                current.makeDefault(false);
                addresses.flush(); // 부분 유니크 인덱스(addresses_one_default) — 옛 기본을 먼저 내려야 새 기본을 켤 수 있다
            });
            target.makeDefault(true);
        }
        cache.evict(customerId);
        return addresses(customerId);
    }

    /** Booking/Step2 "네" — 기본 주소. 없으면 NOT_FOUND (앱 어댑터가 null 로 바꾼다) */
    @Transactional(readOnly = true)
    public DefaultAddressResponse defaultAddress(UUID customerId) {
        Address a = addresses.findByCustomerIdAndIsDefaultTrue(customerId).orElseThrow(ApiException::notFound);
        return new DefaultAddressResponse(a.getRoadAddress(), a.getDetailAddress());
    }

    /** 예약에서 새로 입력한 주소를 주소 목록에 남긴다(saveAddress). 같은 도로명주소가 이미 있으면 그대로 둔다 */
    public void rememberAddress(UUID customerId, String roadAddress, String detailAddress) {
        if (addresses.countByCustomerId(customerId) >= MAX_ADDRESSES) return; // 목록이 가득 차도 예약은 막지 않는다 — 저장만 건너뛴다
        boolean exists = addresses.findByCustomerIdOrderByCreatedAt(customerId).stream().anyMatch(a -> a.getRoadAddress().equals(roadAddress));
        // addAddress 를 거치므로 탈퇴 잠금(holdLive)·시·도 검사도 같이 한다. label 이 null 이라 "기타" 로 저장된다
        if (!exists) addAddress(customerId, new NewAddressRequest(null, roadAddress, detailAddress));
    }

    /** 여러 사용자의 표시 이름 (예약 목록의 매니저 이름). 탈퇴한 사용자는 익명화된 이름이 그대로 온다 */
    @Transactional(readOnly = true)
    public Map<UUID, String> namesOf(Collection<UUID> ids) {
        Map<UUID, String> out = new LinkedHashMap<>();
        if (ids.isEmpty()) return out;
        // 한 번의 조회로 가져온다. 없는 id 는 결과에서 빠진다 — 빈 값은 부르는 쪽이 채운다 (예: getOrDefault 로 빈 문자열)
        for (Profile p : profiles.findAllById(ids)) out.put(p.getId(), p.getName());
        return out;
    }

    // ── 매니저 (B4) ──
    /** marketing: 매니저 마케팅 약관이 '알림 설정에서 해제할 수 있다' 고 약속하는데 토글이 없었다 (완결성 검토) */
    // 매니저 알림 키 (화면의 행 이름·순서는 app_settings.manager_notification_rows).
    // 고객 키 목록(push·marketing)은 completeSignup·updateNotification 안과 CustomerScreenService.CUSTOMER_NOTIFICATION_KEYS 에
    // 따로 적혀 있다 — 키를 늘리면 모두 같이 바꾼다
    static final List<String> MANAGER_NOTIFICATION_KEYS = List.of("push", "call", "settlement", "marketing");
    static final String NOT_REGISTERED = "미등록"; // 매니저 정산 계좌 칸이 비었을 때 보이는 글자

    /** 매니저 작업 상세용 한 줄 — "아파트·빌라 · 30평 · 방 3 · 화장실 2 · 반려동물 있음". 가입 때 받은 거주 정보를 처음으로 쓰는 곳 (검증 검토: 저장만 하고 아무도 읽지 않았다). 없으면 null */
    @Transactional(readOnly = true)
    public String housingSummary(UUID customerId) {
        return housing.findById(customerId).map(h -> {
            // 값이 있는 칸만 모아 " · " 로 잇는다. '평수 모름' 을 골랐으면 평수는 뺀다
            List<String> parts = new java.util.ArrayList<>();
            if (h.getHousingType() != null) parts.add(HOUSING_LABELS.getOrDefault(h.getHousingType(), h.getHousingType()));
            if (!h.isSizeUnknown() && h.getSize() != null && !h.getSize().isBlank()) parts.add(h.getSize() + "평");
            if (h.getRooms() != null && !h.getRooms().isBlank()) parts.add("방 " + h.getRooms());
            if (h.getBathrooms() != null && !h.getBathrooms().isBlank()) parts.add("화장실 " + h.getBathrooms());
            if ("yes".equals(h.getPet())) parts.add("반려동물 있음");
            return parts.isEmpty() ? null : String.join(" · ", parts);
        }).orElse(null);
    }

    /** 앱 HOUSING_TYPES 의 id → 표시 문구 (Figma) */
    static final Map<String, String> HOUSING_LABELS = Map.of("apartment", "아파트·빌라", "studio", "원룸·오피스텔", "house", "단독주택", "other", "기타");

    /** 매칭 후보의 모집단 (§6.2-1: approved=true) */
    // OfferService 가 콜을 보낼 매니저를 고를 때 부른다. findApprovedLive = 승인됐고 탈퇴하지 않은 매니저
    @Transactional(readOnly = true)
    public List<ManagerProfile> approvedManagers() {
        // 패널티로 콜 배정이 정지된 매니저는 후보에서 뺀다 (docs/manager-policy.md)
        return managerProfiles.findApprovedLive().stream().filter(m -> !m.isSuspended(clock.today())).toList();
    }

    /** 지금도 매칭 후보인가 (승인됐고 정지·탈퇴가 아님) — 예: 우선 오퍼(정기 직전 매니저·일정 변경 전 매니저)를 보내기 전, 콜 탭의 approved */
    @Transactional(readOnly = true)
    public boolean isApprovedManager(UUID managerId) {
        // 세 가지를 모두 본다: 승인됨 · 오늘 정지 중이 아님 · 탈퇴하지 않음(findLive). 매니저 프로필이 없으면 false
        return managerProfiles.findById(managerId).map(m -> m.isApproved() && !m.isSuspended(clock.today())).orElse(false) && findLive(managerId).isPresent();
    }

    /**
     * isApprovedManager 와 같은 판단을 DB 의 지금 값으로 한다 — 같은 트랜잭션에서 approvedManagers() 로 먼저 올려 둔 매니저 객체(1차 캐시)의
     * 옛 승인·정지 값을 보지 않는다. OfferExpirer(closeBlockedOffers)가 정지 매니저의 콜을 닫기 직전 '그사이 정지가 풀렸나(면제 등)' 를 다시 볼 때 쓴다.
     * 예전에는 isApprovedManager 가 캐시된 옛 정지 값을 돌려줘 방금 풀린 매니저의 콜을 닫았다 (합친 38건 검토 R3)
     */
    @Transactional(readOnly = true)
    public boolean isApprovedManagerNow(UUID managerId) {
        return managerProfiles.countCallableNow(managerId, clock.today()) > 0;
    }

    /** 승인만 본다 (정지 여부는 빼고) — 홈이 정지된 매니저에게 '승인 대기' 를 보이지 않게 (자체 점검 #11). 매칭·수락은 isApprovedManager */
    @Transactional(readOnly = true)
    public boolean isApproved(UUID managerId) {
        return managerProfiles.findById(managerId).map(ManagerProfile::isApproved).orElse(false) && findLive(managerId).isPresent();
    }

    /** 콜 화면에 보여 줄 정지 안내 (정지 중이 아니면 null) */
    @Transactional(readOnly = true)
    public String suspensionNotice(UUID managerId) {
        // 예: "10월 3일 까지 콜을 받을 수 없어요". 매니저 홈(ManagerHomeService)도 같이 쓴다
        return managerProfiles.findById(managerId).filter(m -> m.isSuspended(clock.today()))
                .map(m -> KoreanFormat.dateShort(m.getSuspendedUntil()) + " 까지 콜을 받을 수 없어요").orElse(null);
    }

    /** 콜 수락의 첫 잠금 (§6.2-3 순서: 매니저 → 예약 → 오퍼). 매니저 프로필이 없으면 NOT_FOUND */
    public ManagerProfile lockManager(UUID managerId) {
        // 탈퇴도 lockManagerIfExists 로 같은 행을 잠근다 — 탈퇴가 먼저 끝나면 approved=false 라 OfferService 가 수락을 거절한다
        return managerProfiles.findForUpdate(managerId).orElseThrow(ApiException::notFound);
    }

    /** 탈퇴용: 가입 1단계만 끝낸 매니저는 manager_profiles 가 아직 없다 — 있으면 잠그고 없으면 넘어간다 */
    public void lockManagerIfExists(UUID managerId) {
        managerProfiles.findForUpdate(managerId); // 결과는 쓰지 않는다 — 행이 있으면 잠금만 건다
    }

    /** 운영자 승인 — 관리 API 전까지는 통합 테스트·SQL 이 부른다 (§13-8) */
    public void approveManager(UUID managerId, List<String> areas, String sido) {
        managerProfiles.findById(managerId).orElseThrow(ApiException::notFound).approve(areas, sido, clock.today(), clock.now());
    }

    /** 작업 완료 → completed_count+1 (§6.1 complete) */
    public void incrementCompleted(UUID managerId) {
        managerProfiles.findById(managerId).ifPresent(m -> m.incrementCompleted(clock.now()));
    }

    /** 고객 라이브 화면의 매니저 소개에 쓰는 요약 (이름·활동 시작·완료 수) */
    public record ManagerIdentity(String name, java.time.LocalDate activeSince, int completedCount) {}

    /** LiveService 가 고객 라이브 화면의 매니저 소개를 만들 때 부른다. 프로필이나 매니저 프로필이 없으면 NOT_FOUND */
    @Transactional(readOnly = true)
    public ManagerIdentity managerIdentity(UUID id) {
        Profile p = profiles.findById(id).orElseThrow(ApiException::notFound); // 탈퇴한 매니저도 완료 예약의 녹화 화면에는 '탈퇴한 사용자' 로 남는다
        ManagerProfile m = managerProfiles.findById(id).orElseThrow(ApiException::notFound);
        return new ManagerIdentity(p.getName(), m.getActiveSince(), m.getCompletedCount());
    }

    /**
     * 고객이 보는 매니저 한 줄 소개 — "경력 2년 · 완료 청소 142건 · 평점 4.9".
     * 내 예약 카드에 이름만 있어 누가 집에 오는지 알 수 없었다 (사용성 검토 2026-09-22). 평점은 실제로 매겨졌을 때만(hasRating).
     */
    @Transactional(readOnly = true)
    public Map<UUID, String> managerIntros(Collection<UUID> ids) {
        Map<UUID, String> out = new java.util.HashMap<>();
        for (ManagerProfile m : managerProfiles.findAllById(ids)) out.put(m.getManagerId(), intro(m)); // 한 번에 (감사 M-09 — 매니저마다 조회하던 N+1)
        return out;
    }

    /**
     * 평점이 실제로 매겨졌는가. 고객이 평가하는 화면이 아직 없어 rating 은 0 에서 바뀌지 않는다 —
     * 예전엔 '완료 1건 이상' 을 기준으로 해서 첫 작업을 마친 매니저가 고객에게 "평점 0.0" 으로 보였다 (직접 사용 점검 U14)
     */
    private static boolean hasRating(ManagerProfile m) {
        return m.getRating() != null && m.getRating().signum() > 0;
    }

    /**
     * 매니저 한 명의 소개 한 줄. 경력 = 활동 시작일부터 오늘(서울 날짜)까지 만 년 수, 1년 미만도 '1년'.
     * 활동 시작일이 없으면 경력 부분을 빼고, 평점은 hasRating 일 때만 붙인다
     */
    private String intro(ManagerProfile m) {
        StringBuilder sb = new StringBuilder();
        if (m.getActiveSince() != null) sb.append("경력 ").append(Math.max(1, java.time.Period.between(m.getActiveSince(), clock.today()).getYears())).append("년 · ");
        sb.append("완료 청소 ").append(m.getCompletedCount()).append("건");
        if (hasRating(m)) sb.append(" · 평점 ").append(m.getRating().toPlainString());
        return sb.toString();
    }

    /** Manager/Profile */
    // GET /manager/profile — 이름 + " 매니저"(이름을 가리지 않는다), 평점·완료 청소 칸, 메뉴 목록(app_settings.manager_profile_menu)
    @Transactional(readOnly = true)
    public ManagerProfileResponse managerProfile(UUID id) {
        Profile p = requireLive(id);
        ManagerProfile m = managerProfiles.findById(id).orElseThrow(ApiException::notFound);
        // 평가가 쌓이기 전에는 '-' — 0.0 은 '0점 매니저' 처럼 읽힌다 (사용성 검토 2026-09-22)
        String rating = hasRating(m) ? m.getRating().toPlainString() : "-";
        return new ManagerProfileResponse(p.getName() + " 매니저",
                List.of(new ManagerProfileResponse.Stat("평점", rating), new ManagerProfileResponse.Stat("완료 청소", m.getCompletedCount() + "건")),
                settings.node("manager_profile_menu"));
    }

    /** Manager/Profile › 매니저 정보·활동 지역·신원 인증·교육 이수·정산 계좌 (읽기 전용, 값은 manager_profiles) */
    @Transactional(readOnly = true)
    public ProfileDetailResponse managerProfileDetail(UUID id, String key) {
        Profile p = requireLive(id);
        ManagerProfile m = managerProfiles.findById(id).orElseThrow(ApiException::notFound);
        JsonNode notes = settings.node("manager_profile_notes"); // 화면 아래 안내 문구 묶음 { info, area, account } (app_settings)
        // key 는 GET /manager/profile/{key} 의 경로 값. switch 는 key 에 맞는 '->' 오른쪽 값을 돌려주고, 모르는 key 면 NOT_FOUND
        return switch (key) {
            case "info" -> new ProfileDetailResponse("매니저 정보", List.of(
                    row("이름", p.getName()),
                    row("휴대폰", KoreanFormat.phoneDisplay(p.getPhone())),
                    row("활동 시작", m.getActiveSince() == null ? "승인 후 표시" : KoreanFormat.yearMonth(m.getActiveSince())),
                    row("완료 청소", m.getCompletedCount() + "건")), null, notes.get("info").asString());
            case "area" -> new ProfileDetailResponse("활동 지역", null, m.getAreas(), notes.get("area").asString());
            // 범죄경력 조회 시각은 Instant(UTC) 라 서울 날짜로 바꾼 뒤 연·월만 보인다
            case "identity" -> new ProfileDetailResponse("신원 인증", List.of(
                    row("본인 인증", m.getIdentityVerifiedAt() == null ? "미완료" : "완료"),
                    row("범죄경력 조회", m.getBackgroundCheckAt() == null ? "미완료" : "완료 · " + KoreanFormat.yearMonthDot(AppClock.toSeoul(m.getBackgroundCheckAt()).toLocalDate())),
                    row("보험 가입", m.getInsuranceExpiresOn() == null ? "미가입" : "가입 · " + KoreanFormat.yearMonthDot(m.getInsuranceExpiresOn()) + " 만료")), null, null);
            case "education" -> new ProfileDetailResponse("교육 이수", educationRows(m.getEducation()), null, null);
            // 계좌번호는 DB 에 가린 값(accountNumberMasked)만 있다. 비어 있으면 "미등록"
            case "account" -> new ProfileDetailResponse("정산 계좌", List.of(
                    row("은행", orNotRegistered(m.getBankName())),
                    row("계좌번호", orNotRegistered(m.getAccountNumberMasked())),
                    row("예금주", orNotRegistered(m.getAccountHolder()))), null, notes.get("account").asString());
            default -> throw ApiException.notFound();
        };
    }

    /** Manager/Profile › 알림 설정 */
    @Transactional(readOnly = true)
    public ManagerNotificationsResponse managerNotifications(UUID id) {
        return new ManagerNotificationsResponse(settings.node("manager_notification_rows"), notifications(id, MANAGER_NOTIFICATION_KEYS));
    }

    /** education jsonb [{ label, completedOn }] → "이수 · 2024.03" / "미이수" */
    private List<ProfileDetailResponse.Row> educationRows(String educationJson) {
        List<ProfileDetailResponse.Row> rows = new ArrayList<>();
        // 문자열을 JSON 트리로 읽는다. completedOn 은 "YYYY-MM-DD" 형식이어야 한다 — 다르면 LocalDate.parse 가 예외를 던져 이 화면 요청이 실패한다
        JsonNode list = json.readTree(educationJson == null ? "[]" : educationJson);
        for (JsonNode e : list) {
            JsonNode done = e.get("completedOn");
            String value = done == null || done.isNull() ? "미이수" : "이수 · " + KoreanFormat.yearMonthDot(java.time.LocalDate.parse(done.asString()));
            rows.add(row(e.path("label").asString(), value));
        }
        return rows;
    }

    /** 정보 화면의 한 줄 (왼쪽 이름, 오른쪽 값) */
    private static ProfileDetailResponse.Row row(String label, String value) {
        return new ProfileDetailResponse.Row(label, value);
    }

    /** 비었으면(null·공백) "미등록" */
    private static String orNotRegistered(String s) {
        return s == null || s.isBlank() ? NOT_REGISTERED : s;
    }

    /** 주소 엔티티 → 앱 ADDRESSES 항목 모양 */
    private static AddressResponse toResponse(Address a) {
        return new AddressResponse(a.getId(), a.getLabel(), a.getRoadAddress(), a.getDetailAddress(), a.isDefault());
    }

    /** 상세 주소처럼 선택 칸: 비었으면 null, 아니면 앞뒤 공백을 뗀 값 */
    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // ── 탈퇴: 잠금(lockLive) → 가드 검사(AuthService) → 개인정보 삭제·익명화(anonymize). Supabase 사용자 삭제는 AuthService 가 이어서 한다 ──
    /**
     * 행 잠금(FOR UPDATE)으로 살아 있는 프로필을 읽는다. 없거나 탈퇴했으면 NOT_FOUND.
     * 부르는 곳: 탈퇴(AuthService.deleteAccount, 가드 검사 전)와 가입 완료(AuthService.completeSignup — 탈퇴와 줄을 서려고, 문제 목록 28번).
     * 콜 수락은 lockManager, 예약 생성·개인정보 쓰기는 holdLive 로 이 행과 줄을 선다
     */
    public Profile lockLive(UUID id) {
        // PESSIMISTIC_WRITE = SELECT … FOR UPDATE: 탈퇴 트랜잭션이 끝날 때까지 holdLive(공유 잠금)를 거는 쓰기는 기다린다.
        // 콜 수락은 이 행이 아니라 manager_profiles 행으로 줄을 선다 — 탈퇴 쪽이 lockManagerIfExists 로 그 행도 잠근다
        return profiles.lockLive(id).orElseThrow(ApiException::notFound);
    }

    /**
     * 예약 생성·개인정보 쓰기의 첫 줄 (감사 M-06). 공유 잠금이라 같은 사용자의 쓰기끼리는 막지 않고 탈퇴와만 직렬화한다.
     * 탈퇴가 먼저면 여기서 401 — 앱은 로그아웃한다. 쓰기가 먼저면 탈퇴 가드가 방금 만든 예약을 본다.
     * 조건: 부르는 쪽 트랜잭션 안에서 불러야 잠금이 그 쓰기가 끝날 때까지 남는다. 묶음 없이 부르면 이 메서드가 끝날 때 바로 풀린다
     * (예: RepairPhotoService 는 그래서 올린 뒤 한 번 더 확인한다).
     */
    public Profile holdLive(UUID id) {
        // PESSIMISTIC_READ = SELECT … FOR SHARE. 행이 없으면(탈퇴함) NOT_FOUND 가 아니라 UNAUTHENTICATED(401)
        return profiles.holdLive(id).orElseThrow(() -> new ApiException(kr.ansimclean.api.common.error.ErrorCode.UNAUTHENTICATED));
    }

    /**
     * holdLive 와 같은 공유 잠금(FOR SHARE) 조회지만, 없거나 탈퇴했으면 401 대신 빈 Optional — 부르는 쪽이 제 오류를 고른다.
     * 운영자 API 처럼 대상이 요청한 사람이 아닌 곳에서 쓴다: 거기서 401 이 나가면 '운영자 토큰이 틀렸다' 로 읽힌다.
     * 부르는 곳: live/DeviceService.register(운영자의 바디캠 등록 — 그 매니저의 탈퇴와 줄을 서려고, 문제 목록 29번).
     * 조건은 holdLive 와 같다: 부르는 쪽 트랜잭션 안에서 불러야 잠금이 그 쓰기가 끝날 때까지 남는다
     */
    public Optional<Profile> holdLiveIfPresent(UUID id) {
        return profiles.holdLive(id);
    }

    /**
     * 탈퇴 익명화. AuthService.deleteAccount 가 탈퇴 트랜잭션 안에서 lockLive·가드 검사 뒤에 부른다.
     * 주소·주거·알림 설정 행을 지우고, 매니저면 승인을 거두고 계좌·활동 지역·신원·교육·보험 기록 등을 비운다(ManagerProfile.withdraw — 행은 남는다).
     * profiles 행은 지우지 않고 이름·닉네임·번호·사진을 비우거나 익명 값으로 바꾼다 — 예약·정산 이력이 이 행을 가리킨다 (Profile.anonymize)
     */
    public void anonymize(UUID id) {
        Profile p = requireLive(id);
        addresses.deleteByCustomerId(id);
        housing.deleteById(id);
        notifications.deleteByUserId(id);
        managerProfiles.findById(id).ifPresent(m -> m.withdraw(clock.now())); // 탈퇴한 매니저가 approved 후보로 남지 않게
        p.anonymize(clock.now());
        cache.evict(id);
    }

    /** 파기 대상: 탈퇴한 지 5년이 지났고 아직 파기하지 않은 계정 (id·역할만, 오래된 순 최대 100) */
    public record PurgeTarget(UUID id, String role) {}

    /** DeletedAccountPurger(매일 새벽 스케줄러)가 부른다. cutoff 는 서울 달력으로 5년 전 시각 */
    @Transactional(readOnly = true)
    public List<PurgeTarget> dueForPurge(Instant cutoff) {
        return profiles.findTop100ByDeletedAtBeforeAndPurgedAtIsNullOrderByDeletedAt(cutoff).stream().map(p -> new PurgeTarget(p.getId(), p.getRole())).toList();
    }

    /** 파기 마무리: 매니저 프로필의 신원·교육 기록을 비우고 파기 시각을 적는다. 탈퇴하지 않은 계정이면 던진다 */
    public void markPurged(UUID id) {
        Instant now = clock.now();
        managerProfiles.findById(id).ifPresent(m -> m.purge(now));
        profiles.findById(id).orElseThrow(ApiException::notFound).markPurged(now);
    }

    /** JWT → CurrentUser (JwtToCurrentUserConverter 가 CurrentUserCache 를 통해 부른다) */
    @Transactional(readOnly = true)
    public Optional<CurrentUser> currentUser(UUID id) {
        return findLive(id).map(p -> new CurrentUser(p.getId(), p.getRole(), p.isSignupComplete()));
    }
}
