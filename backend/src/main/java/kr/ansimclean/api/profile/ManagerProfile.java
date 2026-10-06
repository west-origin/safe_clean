package kr.ansimclean.api.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// 매니저 한 명의 업무용 정보(승인·평점·활동 지역·신원·교육·정산 계좌·패널티 정지). 계정 공통 정보(이름·전화)는 Profile 에 있다.
// 행은 매니저 가입 마지막 단계(ProfileService.completeSignup)에서 만든다.
// 쓰는 곳 예: ProfileService(매칭 후보·MY 화면·탈퇴), ManagerPenaltyService(패널티 정지·해제).
// setter 가 없다 — 값은 아래 메서드로만 바꾼다.
/** manager_profiles — 매니저 승인·평점·활동 지역·신원·정산 계좌(마스킹). approved 는 운영자가 바꾼다(탈퇴 때는 withdraw 가 거둔다) */
@Entity
@Table(name = "manager_profiles")
// @DynamicUpdate: UPDATE 문에 바뀐 칸만 싣는다(기본은 모든 칸). 청소 완료(incrementCompleted)와 운영자 면제(liftSuspension)처럼 이 행을 잠그지 않고
// 읽어 둔 두 트랜잭션이 겹쳐 저장해도, 한쪽의 전체 칸 UPDATE 가 상대가 바꾼 정지 종료일·완료 수를 옛 값으로 되돌리지 않게 (합친 38건 검토 R6 — Profile 과 같은 방어)
@org.hibernate.annotations.DynamicUpdate
public class ManagerProfile {
    @Id
    @Column(name = "manager_id", columnDefinition = "uuid")
    private UUID managerId; // profiles.id 와 같은 값 (계정 하나당 한 행)

    @Column(nullable = false)
    private boolean approved; // true 여야 매칭 후보가 된다

    @Column(nullable = false, precision = 2, scale = 1)
    private BigDecimal rating; // 소수 한 자리 평점. 이 클래스에는 바꾸는 메서드가 없다 (가입 때 0.0, 화면은 0 이면 '-')

    @Column(name = "completed_count", nullable = false)
    private int completedCount; // 완료한 청소 수 — 작업 완료 때 incrementCompleted 로 +1

    @Column(name = "active_since")
    private LocalDate activeSince; // 활동 시작일 (승인한 날, 서울 기준)

    // @JdbcTypeCode(ARRAY) + text[] = Postgres 배열 칸을 자바 List 로 읽고 쓴다
    // 조건: 구가 있는 시는 시까지만 적는다(예: '경기도 성남시'). 예약 주소 '경기도 성남시 분당구 …' 에서 AddressRegion 이 '성남시' 만 뽑아
    // OfferService.inArea 가 글자 그대로 비교하므로, '경기도 성남시 분당구' 로 적으면 그 매니저에게 콜이 가지 않는다
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]", nullable = false)
    private List<String> areas; // 활동 지역 '시도 시군구' 문자열 목록 (예: '서울특별시 강남구')

    @Column(columnDefinition = "text")
    private String sido; // 예약 주소에 시군구가 없을 때 시/도만 비교하는 대체 매칭용 (OfferService)

    // 아래 신원·보험·교육·계좌 칸은 이 클래스에 채우는 메서드가 없다 — 탈퇴(withdraw) 때 비우기만 한다
    @Column(name = "identity_verified_at")
    private Instant identityVerifiedAt;

    @Column(name = "background_check_at")
    private Instant backgroundCheckAt; // 범죄경력 조회 시각

    @Column(name = "insurance_expires_on")
    private LocalDate insuranceExpiresOn;

    // @JdbcTypeCode(JSON) + jsonb = JSON 칸을 자바에서는 글자(String)로 다룬다. 모양: [{ "label", "completedOn" }]
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String education;

    @Column(name = "bank_name", columnDefinition = "text")
    private String bankName;

    @Column(name = "account_number_masked", columnDefinition = "text")
    private String accountNumberMasked; // 가린 계좌번호만 둔다 (원본 칸이 없다)

    @Column(name = "account_holder", columnDefinition = "text")
    private String accountHolder;

    /** 패널티 정지 종료일 (이 날까지 콜을 받지 않는다). docs/manager-policy.md */
    @Column(name = "suspended_until")
    private java.time.LocalDate suspendedUntil;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** JPA 가 DB 행을 읽어 객체를 만들 때 쓰는 빈 생성자. 코드에서는 create 를 쓴다 */
    protected ManagerProfile() {}

    /** 가입 직후: 미승인, 평점 0, 활동 지역 없음 */
    // 부르는 곳: ProfileService.completeSignup (매니저이고 행이 아직 없을 때). 저장은 그쪽이 한다
    public static ManagerProfile create(UUID managerId, Instant now) {
        ManagerProfile m = new ManagerProfile();
        m.managerId = managerId;
        m.approved = false;
        m.rating = BigDecimal.ZERO.setScale(1);
        m.completedCount = 0;
        m.areas = List.of();
        m.education = "[]";
        m.updatedAt = now;
        return m;
    }

    // 읽기용 getter (예: MY 화면 ProfileService, 매칭 OfferService)
    public UUID getManagerId() { return managerId; }
    public boolean isApproved() { return approved; }
    public BigDecimal getRating() { return rating; }
    public int getCompletedCount() { return completedCount; }
    public LocalDate getActiveSince() { return activeSince; }
    public List<String> getAreas() { return areas; }
    public String getSido() { return sido; }
    public Instant getIdentityVerifiedAt() { return identityVerifiedAt; }
    public Instant getBackgroundCheckAt() { return backgroundCheckAt; }
    public LocalDate getInsuranceExpiresOn() { return insuranceExpiresOn; }
    public String getEducation() { return education; }
    public String getBankName() { return bankName; }
    public String getAccountNumberMasked() { return accountNumberMasked; }
    public String getAccountHolder() { return accountHolder; }

    /** 운영자 승인 (§13-8: 관리 API 전까지 SQL·테스트에서). areas = '시도 시군구' 문자열, sido = 대체 매칭용 */
    public void approve(List<String> areas, String sido, LocalDate activeSince, Instant now) {
        // 부르는 곳: ProfileService.approveManager (activeSince = 서울 기준 오늘)
        this.approved = true;
        this.areas = List.copyOf(areas); // 받은 목록을 복사해 둔다 — 밖에서 원본 목록을 바꿔도 여기 값은 그대로
        this.sido = sido;
        this.activeSince = activeSince;
        this.updatedAt = now;
    }

    /** 탈퇴: 승인을 거두고(매칭 후보·우선 오퍼 대상에서 빠진다) 계좌 정보를 지운다. completed_count·rating 은 정산 이력용으로 남긴다 */
    public void withdraw(Instant now) {
        // 부르는 곳: ProfileService.anonymize (탈퇴 트랜잭션 안), 그리고 아래 purge
        this.approved = false;
        this.areas = List.of();
        this.bankName = null;
        this.accountNumberMasked = null;
        this.accountHolder = null;
        // 신원 확인·교육·보험 기록도 탈퇴 때 — 매니저 동의 '보유 기간: 매니저 탈퇴 시까지'(V039, 사용자 결정 그대로)가 사실이 되게 (자체 점검 후속 검토)
        this.sido = null;
        this.activeSince = null;
        this.identityVerifiedAt = null;
        this.backgroundCheckAt = null;
        this.insuranceExpiresOn = null;
        this.education = "[]";
        this.suspendedUntil = null;
        this.updatedAt = now;
    }

    /** 탈퇴 + 5년: 탈퇴 때 비운 것을 한 번 더 — 이 규칙 전에 탈퇴한 매니저에게 남은 신원·교육 기록까지 (지금은 withdraw 가 다 비운다) */
    public void purge(Instant now) {
        // 부르는 곳: ProfileService.markPurged (DeletedAccountPurger 의 파기 마무리)
        withdraw(now);
    }

    /** 패널티 임계치를 넘었을 때 콜 배정 정지 (이미 더 긴 정지가 걸려 있으면 그대로 둔다) */
    public void suspendUntil(java.time.LocalDate until, Instant now) {
        // 부르는 곳: ManagerPenaltyService.applyThreshold. until 은 서울 기준 날짜이고 그날까지 포함해 정지다
        if (suspendedUntil == null || until.isAfter(suspendedUntil)) this.suspendedUntil = until;
        this.updatedAt = now;
    }

    /** 정지 해제 — 증빙 면제로 점수가 정지 기준 아래로 내려갔을 때 (전체 재점검 P3) */
    public void liftSuspension(Instant now) {
        // 부르는 곳: ManagerPenaltyService.waive
        this.suspendedUntil = null;
        this.updatedAt = now;
    }

    /** 오늘 콜 배정 정지 중인가 (true 면 콜을 받을 수 없다) */
    public boolean isSuspended(java.time.LocalDate today) {
        // today 는 서울 날짜(clock.today()). 정지 종료일 당일까지 true, 다음 날부터 false
        // 쓰는 곳 예: ProfileService(매칭 후보에서 빼기), OfferService(콜 수락 막기)
        return suspendedUntil != null && !today.isAfter(suspendedUntil);
    }

    public java.time.LocalDate getSuspendedUntil() { return suspendedUntil; } // 정지 종료일 (정지가 없으면 null)

    /** 완료 청소 수를 1 늘린다. JobService 가 작업 완료 때 ProfileService.incrementCompleted 를 거쳐 부른다 */
    public void incrementCompleted(Instant now) {
        this.completedCount++;
        this.updatedAt = now;
    }
}
