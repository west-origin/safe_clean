package kr.ansimclean.api.profile;

import kr.ansimclean.api.catalog.AppSettingsService;
import kr.ansimclean.api.common.time.AppClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 탈퇴(익명화)와 매칭 후보 판정 (BACKEND_PLAN.md §6.2-1·§13-26) */
// ProfileService 단위 테스트. 저장소는 전부 Mockito 가짜이고, Profile·ManagerProfile·Address 는 진짜 객체라 필드 변화를 바로 본다.
// 제목 외에도 확인하는 것: 파기 마무리(markPurged)·파기 대상 조회, 주소 추가·삭제·기본 변경과 남의 주소 404, 주소 개수 제한,
// 시·도 없는 주소 거절, 알림 설정 기본값·허용 키, 가입 완료 때 마케팅 알림 초기값, 평가 없는 매니저의 평점 표시
class ProfileServiceTest {
    static final Instant NOW = Instant.parse("2026-09-15T05:00:00Z"); // 서비스 시계를 이 시각에 고정한다
    static final UUID MANAGER = UUID.randomUUID();

    ProfileRepository profiles = mock(ProfileRepository.class);
    AddressRepository addresses = mock(AddressRepository.class);
    CustomerHousingRepository housing = mock(CustomerHousingRepository.class);
    NotificationSettingRepository notifications = mock(NotificationSettingRepository.class);
    ManagerProfileRepository managerProfiles = mock(ManagerProfileRepository.class);
    CurrentUserCache cache = mock(CurrentUserCache.class);
    AppSettingsService settings = mock(AppSettingsService.class);
    ProfileService service;
    Profile profile;
    ManagerProfile manager;

    /**
     * 각 테스트 전에: 승인된 매니저 한 명(활동 지역 '서울특별시 강남구')과 그 프로필을 만들어 조회 결과로 돌려주게 한다.
     * holdLive(탈퇴와 줄을 서는 공유 잠금 조회)는 '탈퇴하지 않은 행 조회' 결과를 그대로 쓰게 이어 둔다 —
     * 그래서 테스트가 findByIdAndDeletedAtIsNull 을 empty 로 바꾸면 holdLive 도 empty 가 된다
     */
    @BeforeEach
    void setUp() {
        service = new ProfileService(profiles, addresses, housing, notifications, managerProfiles, cache, settings, new AppClock(Clock.fixed(NOW, AppClock.ZONE)));
        profile = Profile.create(MANAGER, "manager", "김매니저", null, "01012345678");
        manager = ManagerProfile.create(MANAGER, NOW);
        manager.approve(List.of("서울특별시 강남구"), "서울특별시", LocalDate.of(2024, 1, 1), NOW);
        when(profiles.findByIdAndDeletedAtIsNull(MANAGER)).thenReturn(Optional.of(profile));
        when(managerProfiles.findById(MANAGER)).thenReturn(Optional.of(manager));
        when(profiles.holdLive(any())).thenAnswer(inv -> profiles.findByIdAndDeletedAtIsNull(inv.getArgument(0))); // 쓰기의 공유 잠금 = 살아 있는 행 조회
    }

    // ManagerProfile.withdraw 를 직접 부른다. ReflectionTestUtils.setField/getField = private 필드에 값을 넣고 읽는 테스트 도구
    // (교육 이수·신원 확인 시각은 공개 설정 메서드가 없어 이렇게 채운다). 탈퇴 뒤 교육 '[]', 신원·시도 null, 승인 해제를 본다
    @Test
    void 매니저가_탈퇴하면_계좌_지역과_함께_신원_교육_보험_기록도_비운다() { // 동의 '매니저 탈퇴 시까지' (자체 점검 후속 검토)
        org.springframework.test.util.ReflectionTestUtils.setField(manager, "education", "[{\"label\":\"기본 청소 교육\",\"completedOn\":\"2024-03-01\"}]");
        org.springframework.test.util.ReflectionTestUtils.setField(manager, "identityVerifiedAt", NOW);
        manager.withdraw(NOW);
        assertThat(manager.getEducation()).isEqualTo("[]");
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(manager, "identityVerifiedAt")).isNull();
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(manager, "sido")).isNull();
        assertThat(manager.isApproved()).isFalse();
    }

    // 살아 있는 계정에 markPurged 를 부르면 Profile.markPurged 가 IllegalStateException 을 던진다.
    // profile.anonymize(엔티티의 탈퇴 표시) 뒤에 부르면 파기 시각이 NOW 로 적히고 매니저 기록이 비워진다.
    // 주의: 서비스는 매니저 기록을 먼저 비운 뒤 탈퇴 여부를 검사한다. 여기에는 트랜잭션이 없어 첫 호출(예외)에서 이미
    // 매니저 기록이 비워진다(실제로는 예외로 묶음이 되돌려진다) — 두 번째 호출이 한 일은 purgedAt 으로만 확인된다
    @Test
    void 파기는_탈퇴한_계정만_매니저_신원_교육_기록을_비우고_파기_시각을_적는다() { // 탈퇴 + 5년 (BACKEND_PLAN §13-22, 사용자 결정 2026-09-27)
        when(profiles.findById(MANAGER)).thenReturn(Optional.of(profile));
        assertThatThrownBy(() -> service.markPurged(MANAGER)).isInstanceOf(IllegalStateException.class); // 살아 있는 계정은 파기하지 않는다

        profile.anonymize(NOW);
        service.markPurged(MANAGER);
        assertThat(profile.getPurgedAt()).isEqualTo(NOW);
        assertThat(manager.getEducation()).isEqualTo("[]");
        assertThat(manager.isApproved()).isFalse();
        assertThat(manager.getAreas()).isEmpty();
    }

    // dueForPurge(파기 스케줄러가 부르는 조회)는 Profile 전체가 아니라 PurgeTarget(id, 역할) 목록만 돌려준다
    @Test
    void 파기_대상은_id_와_역할만_돌려준다() {
        profile.anonymize(NOW);
        when(profiles.findTop100ByDeletedAtBeforeAndPurgedAtIsNullOrderByDeletedAt(NOW)).thenReturn(List.of(profile));
        assertThat(service.dueForPurge(NOW)).containsExactly(new ProfileService.PurgeTarget(MANAGER, "manager"));
    }

    // 승인·탈퇴 여부는 쿼리(findApprovedLive)가 거르므로 가짜 쿼리 결과를 그대로 믿고,
    // 서비스는 그중 콜 배정 정지(2099년까지) 중인 매니저만 빼는지 본다
    @Test
    void 매칭_후보는_승인되고_탈퇴하지_않은_매니저에서_정지된_사람을_뺀다() { // 자체 점검 #55 — 탈퇴는 쿼리가 거른다(findApprovedLive)
        ManagerProfile ok = ManagerProfile.create(UUID.randomUUID(), NOW);
        ManagerProfile suspended = ManagerProfile.create(UUID.randomUUID(), NOW);
        suspended.suspendUntil(java.time.LocalDate.of(2099, 1, 1), NOW);
        when(managerProfiles.findApprovedLive()).thenReturn(List.of(ok, suspended));
        assertThat(service.approvedManagers()).containsExactly(ok);
    }

    // 탈퇴가 먼저 끝나 살아 있는 행이 없으면(holdLive 가 empty) 주소 추가·이름 변경이 UNAUTHENTICATED(401)로 멈추고
    // 주소를 저장하지 않는다. extracting("code") = 예외 객체의 code 필드 값을 꺼내 비교한다
    @Test
    void 탈퇴가_먼저_커밋됐으면_주소_주거_이름_쓰기는_401_이고_아무것도_남기지_않는다() { // 감사 M-06
        UUID gone = UUID.randomUUID();
        when(profiles.findByIdAndDeletedAtIsNull(gone)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addAddress(gone, new kr.ansimclean.api.profile.dto.NewAddressRequest(null, "서울 강남구 테헤란로 1", null)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(kr.ansimclean.api.common.error.ErrorCode.UNAUTHENTICATED);
        assertThatThrownBy(() -> service.updateProfile(gone, "새이름", null))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(kr.ansimclean.api.common.error.ErrorCode.UNAUTHENTICATED);
        verify(addresses, org.mockito.Mockito.never()).save(any());
    }

    // anonymize(탈퇴 익명화) 뒤: 매니저 승인·지역·계좌가 비고, 프로필에 탈퇴 시각이 적히고,
    // 주소 행 삭제와 사용자 캐시 비우기(evict)가 불렸는지 본다
    @Test
    void 매니저_탈퇴는_승인을_거두고_계좌_정보를_지운다() {
        // 탈퇴 전에는 후보였다
        assertThat(service.isApprovedManager(MANAGER)).isTrue();

        service.anonymize(MANAGER);

        assertThat(manager.isApproved()).isFalse(); // approvedManagers()(findApprovedLive) 와 우선 오퍼 대상에서 빠진다
        assertThat(manager.getAreas()).isEmpty();
        assertThat(manager.getBankName()).isNull();
        assertThat(manager.getAccountNumberMasked()).isNull();
        assertThat(profile.getDeletedAt()).isEqualTo(NOW);
        verify(addresses).deleteByCustomerId(MANAGER);
        verify(cache).evict(MANAGER);
    }

    // 준비: 이 고객의 기본 주소 '집'(200초 전)과 '회사'(100초 전), 다른 고객의 주소 하나.
    // 확인: 남의 주소 삭제는 NOT_FOUND 이고 아무것도 지우지 않는다 → 기본 주소를 지우면 삭제를 flush 로 먼저 반영한 뒤
    // 남은 주소 중 가장 오래된 '회사' 가 기본이 된다. 삭제 뒤 남은 목록은 가짜 조회로 [회사] 만 돌려준다
    @Test
    void 기본_주소를_지우면_가장_오래된_주소가_기본이_되고_남의_주소는_404() {
        UUID customer = UUID.randomUUID();
        Address home = Address.create(customer, "집", "서울특별시 강남구 테헤란로 123", null, true, NOW.minusSeconds(200));
        Address office = Address.create(customer, "회사", "서울특별시 서초구 서초대로 1", null, false, NOW.minusSeconds(100));
        Address other = Address.create(UUID.randomUUID(), "남", "부산광역시 해운대구 1", null, true, NOW);
        when(addresses.findById(home.getId())).thenReturn(Optional.of(home));
        when(addresses.findById(other.getId())).thenReturn(Optional.of(other));
        when(addresses.findByCustomerIdOrderByCreatedAt(customer)).thenReturn(List.of(office));

        assertThatThrownBy(() -> service.removeAddress(customer, other.getId())).isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(ErrorCode.NOT_FOUND); // 존재를 노출하지 않는다
        verify(addresses, never()).delete(any(Address.class));

        assertThat(service.removeAddress(customer, home.getId()).id()).isEqualTo(home.getId());
        verify(addresses).delete(home);
        verify(addresses).flush(); // 부분 유니크 인덱스 — 삭제가 먼저 반영돼야 다음 기본 주소를 켤 수 있다
        assertThat(office.isDefault()).isTrue();
        verify(cache).evict(customer);
    }

    // 조회: 저장된 행은 call=false 하나뿐 → 나머지 키는 기본값(marketing 만 꺼짐, 그 밖은 켜짐)으로 채운다.
    // 저장: 매니저 허용 키(ProfileService.MANAGER_NOTIFICATION_KEYS, 예: settlement·marketing) 안이면 새 행을 만들어 저장하고,
    // 'admin' 같은 키는 예외
    @Test
    void 알림_키는_역할별_목록_안에서만_바꾸고_조회는_없는_키를_기본값으로_채운다() {
        when(notifications.findByUserId(MANAGER)).thenReturn(List.of(NotificationSetting.of(MANAGER, "call", false)));
        assertThat(service.notifications(MANAGER, List.of("push", "call", "settlement", "marketing")))
                .containsEntry("push", true).containsEntry("call", false).containsEntry("settlement", true).containsEntry("marketing", false);

        when(notifications.findById(any())).thenReturn(Optional.empty());
        assertThat(service.updateNotification(MANAGER, "settlement", false).enabled()).isFalse();
        verify(notifications).save(any());
        assertThat(service.updateNotification(MANAGER, "marketing", true).enabled()).isTrue(); // 매니저도 마케팅 토글이 있다 (약관이 '알림 설정에서 해제' 를 약속)
        assertThatThrownBy(() -> service.updateNotification(MANAGER, "admin", true)).isInstanceOf(ApiException.class);
    }

    // 매니저 프로필은 승인 상태 그대로 두고, 살아 있는 프로필 조회만 empty(탈퇴)로 바꾼다 → 후보 아님
    @Test
    void 승인됐어도_탈퇴한_계정은_후보가_아니다() {
        when(profiles.findByIdAndDeletedAtIsNull(MANAGER)).thenReturn(Optional.empty());
        assertThat(service.isApprovedManager(MANAGER)).isFalse();
    }

    // holdLiveIfPresent: holdLive 와 같은 공유 잠금 조회 결과를 그대로 준다 — 탈퇴한 계정이면 401 을 던지지 않고 빈 값 (문제 목록 29번, 운영자 바디캠 등록용)
    @Test
    void 공유_잠금_조회는_탈퇴한_계정이면_401_대신_빈_값() {
        assertThat(service.holdLiveIfPresent(MANAGER)).contains(profile);
        when(profiles.findByIdAndDeletedAtIsNull(MANAGER)).thenReturn(Optional.empty()); // setUp 이 holdLive 를 이 조회에 이어 두었다
        assertThat(service.holdLiveIfPresent(MANAGER)).isEmpty();
        verify(profiles, times(2)).holdLive(MANAGER); // 잠그지 않는 findLive 가 아니라 FOR SHARE 조회
    }

    // @DynamicUpdate = Hibernate 가 UPDATE 에 바뀐 칸만 싣는다. 잠금 없이 읽어 둔 옛 프로필을 저장하는 경로가 새로 생겨도
    // 바꾸지 않은 이름·번호·탈퇴 표시를 옛 값으로 덮어쓰지 않게 하는 두 번째 방어다 (문제 목록 28번)
    @Test
    void 프로필은_바뀐_칸만_UPDATE_한다() {
        assertThat(Profile.class.isAnnotationPresent(org.hibernate.annotations.DynamicUpdate.class)).isTrue();
    }

    // 매니저 프로필도 바뀐 칸만 UPDATE 한다 — 청소 완료(incrementCompleted)와 운영자 면제(liftSuspension)가 같은 행을 잠그지 않고 읽어 동시에 저장하면
    // 전체 칸 UPDATE 가 상대가 바꾼 정지 종료일(또는 완료 수)을 옛 값으로 되돌렸다 (합친 38건 검토 R6. DB 로 겹침을 만든 확인은 PenaltyWaiveIT)
    @Test
    void 매니저_프로필도_바뀐_칸만_UPDATE_한다() {
        assertThat(ManagerProfile.class.isAnnotationPresent(org.hibernate.annotations.DynamicUpdate.class)).isTrue();
    }

    // 고객이 마케팅 동의(true)로 가입을 마치면 알림 설정 두 줄(push·marketing)이 모두 켜진 채 저장된다.
    // ArgumentCaptor = 가짜 save 에 넘어간 인자를 붙잡아 두는 도구. getAllValues() 로 저장된 순서대로 꺼낸다
    @Test
    void 가입_완료_때_마케팅_동의가_있으면_마케팅_알림이_켜진_채_시작한다() {
        UUID customer = UUID.randomUUID();
        Profile c = Profile.create(customer, "customer", "김하늘", "하늘", "01098765432");
        when(profiles.findByIdAndDeletedAtIsNull(customer)).thenReturn(Optional.of(c));
        when(notifications.existsById(any())).thenReturn(false);
        service.completeSignup(customer, true);
        ArgumentCaptor<NotificationSetting> saved = ArgumentCaptor.forClass(NotificationSetting.class);
        verify(notifications, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(NotificationSetting::getKey, NotificationSetting::isEnabled)
                .containsExactly(tuple("push", true), tuple("marketing", true)); // 동의 기록과 설정 토글이 어긋나지 않게 (완결성 검토)
    }

    // 이미 주소가 MAX_ADDRESSES(20)개인 고객: 설정의 주소 추가는 VALIDATION(400)으로 막고,
    // 예약 흐름의 rememberAddress 는 예외 없이 저장만 건너뛴다 (예약 자체는 막지 않는다)
    @Test
    void 주소는_20개까지_예약에서_새_주소를_저장할_때는_막지_않고_건너뛴다() { // 전체 재점검 P4
        UUID customer = UUID.randomUUID();
        when(profiles.findByIdAndDeletedAtIsNull(customer)).thenReturn(Optional.of(Profile.create(customer, "customer", "고객", null, "+821011112222")));
        when(addresses.countByCustomerId(customer)).thenReturn((long) ProfileService.MAX_ADDRESSES);
        assertThatThrownBy(() -> service.addAddress(customer, new kr.ansimclean.api.profile.dto.NewAddressRequest(null, "서울 강남구 테헤란로 1", null)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(kr.ansimclean.api.common.error.ErrorCode.VALIDATION);
        service.rememberAddress(customer, "서울 강남구 테헤란로 2", null); // 예약 흐름 — 던지지 않는다
        verify(addresses, org.mockito.Mockito.never()).save(any());
    }

    // 주소 추가(설정)와 가입 주소 저장(saveDefaultAddress) 둘 다 시·도 없는 주소를 MISSING_SIDO 문구로 거절하고 저장하지 않는다
    @Test
    void 시도를_읽을_수_없는_주소는_저장하지_않는다() { // 직접 사용 점검 U1
        UUID customer = UUID.randomUUID();
        when(profiles.findByIdAndDeletedAtIsNull(customer)).thenReturn(Optional.of(Profile.create(customer, "customer", "고객", null, "+821011112222")));
        assertThatThrownBy(() -> service.addAddress(customer, new kr.ansimclean.api.profile.dto.NewAddressRequest(null, "강남대로 396", null)))
                .isInstanceOf(ApiException.class).hasMessage(AddressRegion.MISSING_SIDO);
        assertThatThrownBy(() -> service.saveDefaultAddress(customer, new kr.ansimclean.api.profile.dto.AddressRequest("강남구 테헤란로 1", "3층")))
                .isInstanceOf(ApiException.class).hasMessage(AddressRegion.MISSING_SIDO);
        verify(addresses, org.mockito.Mockito.never()).save(any());
    }

    // 완료 1건·평점 0 인 새 매니저: 고객이 보는 소개 한 줄에는 평점이 없고(활동 시작일도 없어 경력도 빠진다),
    // 매니저 MY 화면의 두 칸(평점·완료 청소)은 "-"·"1건" 이어야 한다
    @Test
    void 평가가_없으면_완료가_있어도_평점을_보이지_않는다() { // 직접 사용 점검 U14 — 평가 화면이 없어 rating 은 0 그대로
        UUID manager = UUID.randomUUID();
        ManagerProfile m = ManagerProfile.create(manager, NOW);
        m.incrementCompleted(NOW);
        when(managerProfiles.findAllById(any())).thenReturn(java.util.List.of(m));
        when(managerProfiles.findById(manager)).thenReturn(Optional.of(m));
        when(profiles.findByIdAndDeletedAtIsNull(manager)).thenReturn(Optional.of(Profile.create(manager, "manager", "김미영", null, "+821011112222")));
        assertThat(service.managerIntros(java.util.List.of(manager)).get(manager)).isEqualTo("완료 청소 1건");
        assertThat(service.managerProfile(manager).stats()).extracting(kr.ansimclean.api.profile.dto.ManagerProfileResponse.Stat::value).containsExactly("-", "1건");
    }

    // '회사' 를 기본으로 바꾸면 옛 기본 '집' 을 먼저 내리고 flush(DB 반영)한 뒤 '회사' 를 켠다 —
    // 고객당 기본 주소 하나만 허용하는 부분 유니크 인덱스 때문에 순서가 중요하다. 결과 목록의 isDefault 는 [false, true].
    // 마지막으로 남의 주소 id 는 NOT_FOUND
    @Test
    void 기본_주소를_바꾸면_옛_기본을_먼저_내린다() { // 직접 사용 점검 U15
        UUID customer = UUID.randomUUID();
        when(profiles.findByIdAndDeletedAtIsNull(customer)).thenReturn(Optional.of(Profile.create(customer, "customer", "고객", null, "+821011112222")));
        Address home = Address.create(customer, "집", "서울 강남구 테헤란로 1", null, true, NOW);
        Address office = Address.create(customer, "회사", "서울 강남구 테헤란로 2", null, false, NOW);
        UUID officeId = UUID.randomUUID();
        when(addresses.findById(officeId)).thenReturn(Optional.of(office));
        when(addresses.findByCustomerIdAndIsDefaultTrue(customer)).thenReturn(Optional.of(home));
        when(addresses.findByCustomerIdOrderByCreatedAt(customer)).thenReturn(java.util.List.of(home, office));

        assertThat(service.makeDefaultAddress(customer, officeId)).extracting(kr.ansimclean.api.profile.dto.AddressResponse::isDefault).containsExactly(false, true);
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(addresses);
        order.verify(addresses).flush(); // 부분 유니크 인덱스 — 옛 기본을 먼저 반영
        assertThat(home.isDefault()).isFalse();
        assertThat(office.isDefault()).isTrue();

        UUID stranger = UUID.randomUUID();
        when(addresses.findById(stranger)).thenReturn(Optional.of(Address.create(UUID.randomUUID(), "남의 집", "서울 강남구 테헤란로 3", null, true, NOW)));
        assertThatThrownBy(() -> service.makeDefaultAddress(customer, stranger)).isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(kr.ansimclean.api.common.error.ErrorCode.NOT_FOUND); // 남의 주소는 존재를 드러내지 않는다
    }
}
