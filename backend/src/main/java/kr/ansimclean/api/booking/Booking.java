package kr.ansimclean.api.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kr.ansimclean.api.catalog.dto.PriceQuote;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.time.AppClock;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * bookings — 예약 1건(정기청소는 회차마다 1건). **상태는 여기 메서드로만 바뀐다** (CLAUDE.md §11-6, 전이 표 BACKEND_PLAN.md §6.1).
 * 잘못된 전이는 ApiException(INVALID_STATE). 금액·소요 시간은 생성 시점 스냅샷(§6.4).
 *
 * 새 예약은 BookingService 만 만든다(create). 상태를 바꾸는 쪽 예: OfferService(매칭·수락), JobService(작업 시작·완료).
 * 방문 날짜·시각(service_date/service_time)은 서울 달력 값 그대로, starts_at/ends_at 은 그걸 UTC 시각(Instant)으로 바꾼 값이다.
 */
@Entity
@Table(name = "bookings")
public class Booking {
    @Id // 기본 키. DB 가 아니라 create() 에서 UUID 를 만들어 넣는다
    @Column(columnDefinition = "uuid")
    private UUID id;

    // 정기청소 시리즈(booking_series) id. 1회 예약이면 null
    @Column(name = "series_id", columnDefinition = "uuid")
    private UUID seriesId;

    @Column(name = "customer_id", columnDefinition = "uuid", nullable = false)
    private UUID customerId;

    // 배정된 매니저. 배정 전이거나, 매니저 방문 취소·일정 변경으로 다시 매칭 중이면 null
    @Column(name = "manager_id", columnDefinition = "uuid")
    private UUID managerId;

    // 서비스 종류 키 (home·office·appliance·move-in·repair 등, service_categories.key)
    @Column(name = "category_key", columnDefinition = "text", nullable = false)
    private String categoryKey;

    // "oneTime"(1회) 또는 "recurring"(정기청소) — DB check 제약이 두 값만 허용한다
    @Column(columnDefinition = "text", nullable = false)
    private String frequency;

    // 방문 날짜·시각 (서울 달력 기준). 비교·정렬용 절대 시각은 아래 startsAt
    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Column(name = "service_time", nullable = false)
    private LocalTime serviceTime;

    @Column(name = "road_address", columnDefinition = "text", nullable = false)
    private String roadAddress;

    @Column(name = "detail_address", columnDefinition = "text")
    private String detailAddress;

    // 도로명 주소에서 뽑은 시·군·구와 시·도 (BookingService 가 AddressRegion.parse 로). 매칭 후보의 활동 지역과 맞춰 본다
    @Column(columnDefinition = "text")
    private String sigungu;

    @Column(columnDefinition = "text")
    private String sido;

    // @JdbcTypeCode(SqlTypes.ARRAY) = Postgres 배열 칸(text[])을 자바 List 로 읽고 쓴다. 값은 옵션 키 목록
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "free_options", columnDefinition = "text[]", nullable = false)
    private List<String> freeOptions;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "paid_options", columnDefinition = "text[]", nullable = false)
    private List<String> paidOptions;

    // 고객이 고른 주의사항 키 목록 (카탈로그의 notice 키). 매니저 콜 카드에는 첫 항목의 문구만 보인다(OfferService)
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]", nullable = false)
    private List<String> notices;

    // 고객이 적은 요청사항(자유 입력). 탈퇴 5년 뒤 파기 대상 (BookingRepository.scrubCustomerBookings)
    @Column(columnDefinition = "text")
    private String request;

    // 수리 예약의 수리 종류 키 (앱 REPAIR_CATEGORIES 키, V012). 청소 예약이면 null
    @Column(name = "repair_type", columnDefinition = "text")
    private String repairType;

    // ── 수리 예약 전용 칸 (describeRepair 로 채운다). 가전·증상은 화면에 보인 문구 그대로 ──
    @Column(name = "repair_appliance", columnDefinition = "text")
    private String repairAppliance;

    @Column(name = "repair_symptom", columnDefinition = "text")
    private String repairSymptom;

    /** 가전·증상 키 (V017) — 문구와 나란히. 통계·수리 매니저 매칭용 */
    @Column(name = "repair_appliance_key")
    private String repairApplianceKey;

    @Column(name = "repair_symptom_key")
    private String repairSymptomKey;

    @Column(name = "repair_detail", columnDefinition = "text")
    private String repairDetail;

    // 첨부 사진의 Storage 경로 목록 ("<고객 id>/…" 형식만 받는다, BookingService.ownPhotos)
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "repair_photos", columnDefinition = "text[]", nullable = false)
    private List<String> repairPhotos = List.of();

    // ── 금액(원, long)·소요 시간. 예약을 만들 때 견적(PriceQuote)에서 복사해 고정한다 — 나중에 가격표가 바뀌어도 이 예약은 그대로 ──
    @Column(name = "base_amount", nullable = false)
    private long baseAmount;

    @Column(name = "options_amount", nullable = false)
    private long optionsAmount;

    // 고객이 내는 1회 금액 (정기청소면 회차 하나의 금액)
    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    // 플랫폼 수수료. 매니저 몫 = totalAmount − platformFee (managerPay)
    @Column(name = "platform_fee", nullable = false)
    private long platformFee;

    @Column(name = "duration_minutes", nullable = false) // 예상 소요 시간(분) — endsAt 계산에 쓴다
    private int durationMinutes;

    // 방문 시작·끝 절대 시각(UTC). startsAt = 서울 기준 serviceDate+serviceTime, endsAt = startsAt + 소요 시간.
    // 매니저 일정 겹침 검사·취소 기한 계산이 이 값을 쓴다
    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    // @Convert = DB 의 문자열("in_progress" 등)과 enum 을 BookingStatusConverter 로 서로 바꿔 읽고 쓴다
    @Convert(converter = BookingStatusConverter.class)
    @Column(columnDefinition = "text", nullable = false)
    private BookingStatus status;

    // 결제 상태. PG 연동 전이라 지금은 늘 "unpaid" (create 에서만 넣는다)
    @Column(name = "payment_status", columnDefinition = "text", nullable = false)
    private String paymentStatus;

    // ── 전이 시각(UTC). assignedAt = 매니저 수락(방문 취소·일정 변경 때 다시 비움), startedAt = 작업 시작, endedAt = 완료, cancelledAt = 취소 ──
    @Column(name = "assigned_at")
    private Instant assignedAt;

    /** 매칭 실패 시각 — 후보 0명·회차 소진. 배정되면 지운다 (V015) */
    // 일정 변경(reschedule)과 운영자·자동 재매칭(restartMatching)도 지운다 — 지워진 뒤 다시 실패하면 고객에게 또 알린다
    @Column(name = "matching_failed_at")
    private Instant matchingFailedAt;

    /** 약속 시간이 지나도 시작하지 않아 고객에게 '늦어지고 있어요' 를 보낸 시각 — 한 번만 보낸다 (V037, 자체 점검 #25) */
    @Column(name = "late_notified_at")
    private Instant lateNotifiedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    // 취소 사유. 코드(customer·series_cancelled·unassigned·not_started 등)이거나 고객이 적은 자유 입력
    @Column(name = "cancel_reason", columnDefinition = "text")
    private String cancelReason;

    // 매칭 회차 = 콜(offers.round)의 회차 번호. 처음은 1, 재매칭·일정 변경·매니저 방문 취소 때 1씩 오른다.
    // 같은 회차에 한 매니저에게 두 번 보내지 않는다 (offers 의 unique(booking_id, manager_id, round))
    @Column(name = "dispatch_round", nullable = false)
    private int dispatchRound;

    /** 지금 매칭 구간이 시작된 회차 — 일정 변경·매니저 방문 취소 때 새로 시작 (V043). 거절·만료 뒤 재매칭(OfferService.redispatch)과 자동 재매칭이 '이미 받은 매니저' 를 이 회차부터 센다 */
    @Column(name = "matching_from_round", nullable = false)
    private int matchingFromRound = 1;

    // ── 밤이라 아침으로 미룬 매칭 (V046, 문제 목록 2번). OfferService 가 적고 아침에 releaseHeldDispatches 가 보내며 지운다 ──
    /** 새 콜을 아침까지 미룬 시각 — null 이면 미룬 것이 없다. 미루는 동안 콜이 없어 고객 카드는 배정 전 문구 그대로다 */
    @Column(name = "dispatch_held_at")
    private Instant dispatchHeldAt;

    /** 아침에 먼저 우선 콜을 줄 매니저 (정기청소 직전 매니저·일정 변경 전 매니저). null 이면 후보 전원에게 일반 콜 */
    @Column(name = "dispatch_held_manager_id", columnDefinition = "uuid")
    private UUID dispatchHeldManagerId;

    /** 위 매니저가 고객의 일정 변경 전 이 회차를 맡았던 사람인가 — 그러면 '예약 일정이 변경됐어요' 푸시도 아침에 우선 콜과 함께 간다 */
    @Column(name = "dispatch_held_rescheduled", nullable = false)
    private boolean dispatchHeldRescheduled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // insertable/updatable = false: 자바에서 쓰지 않는다. DB 기본값·트리거(set_updated_at, V001)가 채운다
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    // JPA 가 DB 행을 읽어 객체를 만들 때 쓰는 빈 생성자. 코드에서 새 예약은 create() 로만 만든다
    protected Booking() {}

    /** 생성 직후 상태는 requested. 매칭을 시작하면 beginAssigning() */
    public static Booking create(UUID customerId, UUID seriesId, String categoryKey, String frequency, LocalDate date, LocalTime time,
                                 String roadAddress, String detailAddress, String sido, String sigungu,
                                 List<String> freeOptions, List<String> paidOptions, List<String> notices, String request, String repairType,
                                 PriceQuote quote, Instant now) {
        // BookingService.doCreate 가 방문일마다(정기청소면 회차 수만큼) 부른다. 저장(save)은 부른 쪽이 한다
        Booking b = new Booking();
        b.id = UUID.randomUUID();
        b.customerId = customerId;
        b.seriesId = seriesId;
        b.categoryKey = categoryKey;
        b.frequency = frequency;
        b.serviceDate = date;
        b.serviceTime = time;
        b.roadAddress = roadAddress;
        b.detailAddress = detailAddress;
        b.sido = sido;
        b.sigungu = sigungu;
        // List.copyOf = 바꿀 수 없는 복사본. 넘겨준 쪽이 나중에 자기 목록을 고쳐도 예약에 번지지 않는다
        b.freeOptions = List.copyOf(freeOptions);
        b.paidOptions = List.copyOf(paidOptions);
        b.notices = List.copyOf(notices);
        b.request = request;
        b.repairType = repairType;
        // 견적 값을 이 행에 고정한다 — 콜 금액·정산은 이후 이 칸들에서만 계산한다
        b.baseAmount = quote.base();
        b.optionsAmount = quote.options();
        b.totalAmount = quote.total();
        b.platformFee = quote.platformFee();
        b.durationMinutes = quote.durationMinutes();
        b.startsAt = AppClock.toInstant(date, time); // 서울(Asia/Seoul) 날짜+시각 → UTC 시각
        b.endsAt = b.startsAt.plus(Duration.ofMinutes(quote.durationMinutes()));
        b.status = BookingStatus.REQUESTED;
        b.paymentStatus = "unpaid";
        b.dispatchRound = 1;
        b.createdAt = now;
        return b;
    }

    // ── 읽기용 getter. 상태를 바꾸는 메서드는 아래 '전이' 절에만 있다 (setter 없음) ──
    public UUID getId() { return id; }
    public UUID getSeriesId() { return seriesId; }
    public UUID getCustomerId() { return customerId; }
    public UUID getManagerId() { return managerId; }
    public String getCategoryKey() { return categoryKey; }
    public String getFrequency() { return frequency; }
    public LocalDate getServiceDate() { return serviceDate; }
    public LocalTime getServiceTime() { return serviceTime; }
    public String getRoadAddress() { return roadAddress; }
    public String getDetailAddress() { return detailAddress; }
    public String getSigungu() { return sigungu; }
    public String getSido() { return sido; }
    public List<String> getFreeOptions() { return freeOptions; }
    public List<String> getPaidOptions() { return paidOptions; }
    public List<String> getNotices() { return notices; }
    public String getRequest() { return request; }
    public String getRepairType() { return repairType; }
    public String getRepairAppliance() { return repairAppliance; }
    public String getRepairSymptom() { return repairSymptom; }
    public String getRepairApplianceKey() { return repairApplianceKey; }
    public String getRepairSymptomKey() { return repairSymptomKey; }
    public String getRepairDetail() { return repairDetail; }
    public List<String> getRepairPhotos() { return repairPhotos; }
    public boolean isRepair() { return "repair".equals(categoryKey); } // 수리 서비스 예약인가

    /** 수리 예약의 가전·증상·상세·사진 (생성 직후 한 번) */
    // 키 없이 문구만 받는 짧은 버전 — 지금 서버 코드에서는 부르지 않고 테스트가 쓴다
    public void describeRepair(String appliance, String symptom, String detail, List<String> photos) {
        describeRepair(appliance, symptom, null, null, detail, photos);
    }

    /** 수리 칸 전부를 채운다. BookingService.doCreate 가 수리 예약을 만든 직후 부른다(가전·증상 문구와 그 키, 상세, 사진 경로). photos 가 null 이면 빈 목록 */
    public void describeRepair(String appliance, String symptom, String applianceKey, String symptomKey, String detail, List<String> photos) {
        this.repairAppliance = appliance;
        this.repairSymptom = symptom;
        this.repairApplianceKey = applianceKey;
        this.repairSymptomKey = symptomKey;
        this.repairDetail = detail;
        this.repairPhotos = photos == null ? List.of() : List.copyOf(photos);
    }

    /**
     * 매니저(기사)가 보는 요청사항 — 수리 예약이면 가전·증상·상세를 앞에 붙인다 (Figma 매니저 화면에 수리 항목이 없어 요청사항 줄로 보여준다, 코드에서 정함)
     * 사진 수는 저장된 경로 수로 센다. 콜 상세(OfferService.call)가 이 버전을 쓴다
     */
    public List<String> requestLines() {
        return requestLines(repairPhotos == null ? 0 : repairPhotos.size());
    }

    /** @param photoCount 실제로 보여줄 사진 수 — 서명 URL 을 만들지 못한 장은 세지 않는다(장수 문구와 목록이 어긋나지 않게, 검수 3차) */
    public List<String> requestLines(int photoCount) {
        // 순서: 가전 → 증상 → 증상 상세 → 사진 장수 → 고객 요청사항. 빈 칸은 줄을 만들지 않는다. JobService(작업 상세 등)가 이 버전을 직접 부른다
        List<String> out = new java.util.ArrayList<>();
        if (repairAppliance != null) out.add("가전: " + repairAppliance);
        if (repairSymptom != null) out.add("증상: " + repairSymptom);
        if (repairDetail != null && !repairDetail.isBlank()) out.add(repairDetail);
        if (photoCount > 0) out.add("사진 " + photoCount + "장 첨부");
        if (request != null && !request.isBlank()) out.add(request);
        return out;
    }
    public long getBaseAmount() { return baseAmount; }
    public long getOptionsAmount() { return optionsAmount; }
    public long getTotalAmount() { return totalAmount; }
    public long getPlatformFee() { return platformFee; }
    public int getDurationMinutes() { return durationMinutes; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public BookingStatus getStatus() { return status; }
    public String getPaymentStatus() { return paymentStatus; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getMatchingFailedAt() { return matchingFailedAt; }
    public Instant getLateNotifiedAt() { return lateNotifiedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public String getCancelReason() { return cancelReason; }
    public int getDispatchRound() { return dispatchRound; }
    public int getMatchingFromRound() { return matchingFromRound; }
    public Instant getDispatchHeldAt() { return dispatchHeldAt; }
    public UUID getDispatchHeldManagerId() { return dispatchHeldManagerId; }
    public boolean isDispatchHeldRescheduled() { return dispatchHeldRescheduled; }
    /** 밤이라 새 콜을 아침으로 미뤄 둔 예약인가 */
    public boolean isDispatchHeld() { return dispatchHeldAt != null; }
    public Instant getCreatedAt() { return createdAt; }

    /** 정기청소 회차인가 (frequency = "recurring") */
    public boolean isRecurring() {
        return "recurring".equals(frequency);
    }

    /** 매니저 수령액 = total − platform_fee (오퍼·정산은 이 값만 쓴다, §6.4) */
    public long managerPay() {
        return totalAmount - platformFee;
    }

    /** 취소·변경 기한 안인가: starts_at − now ≥ deadlineHours (§6.1) */
    public boolean isBeforeDeadline(Instant now, int deadlineHours) {
        // '지금 + 기한 시간' 이 시작 시각보다 뒤가 아니면(같거나 앞) 기한 안. deadlineHours 는 app_settings 의 cancel_deadline_hours(시드 값 24)
        return !now.plus(Duration.ofHours(deadlineHours)).isAfter(startsAt);
    }

    // ── 전이 (§6.1) ──
    // 각 메서드는 지금 상태가 허용된 출발 상태인지 먼저 확인하고(require 등), 아니면 INVALID_STATE 를 던진다.
    // 상태 이력(booking_status_history) 기록·푸시·오퍼 정리는 여기서 하지 않고 부른 Service 가 한다.

    /** requested → assigning (매칭 시작). 정기청소 회차는 SeriesDispatcher 가 방문 7일 전에 부른다 */
    public void beginAssigning() {
        // 부르는 곳: BookingService.startAssigning — 1회 예약·정기 첫 회차는 생성 직후, 나머지 회차는 BookingSeriesService.dispatchDue(매일 09:00)나
        // 일정 변경으로 매칭 시점(방문 dispatch_lead_days 일 전) 안에 들어왔을 때
        require(BookingStatus.REQUESTED);
        status = BookingStatus.ASSIGNING;
    }

    /** assigning → scheduled (매니저 수락). 체크리스트 복사·live_sessions 는 B4 의 OfferService 가 같은 tx 에서 */
    public void assign(UUID managerId, Instant now) {
        // 부르는 곳: OfferService.doAccept (매니저 행 → 예약 행 → 오퍼 행 순서로 잠근 뒤)
        require(BookingStatus.ASSIGNING);
        this.managerId = managerId;
        this.assignedAt = now;
        this.matchingFailedAt = null; // 매칭 실패 표시를 지운다 — 고객 카드의 매니저 칸이 실패 안내에서 매니저 이름으로 바뀐다
        status = BookingStatus.SCHEDULED;
    }

    /**
     * scheduled → assigning (매니저가 방문을 취소). 예약은 살아 있고 다시 매칭한다 (docs/manager-policy.md).
     * 이미 시작한 작업은 이 길로 돌릴 수 없다(require 가 막는다).
     */
    public void releaseByManager() {
        // 부르는 곳: JobService.doCancelByManager. 회차 올리기·새 콜은 이어서 OfferService.onReleased → restartAfterRelease 가 한다
        require(BookingStatus.SCHEDULED);
        this.managerId = null;
        this.assignedAt = null;
        status = BookingStatus.ASSIGNING;
    }

    /** 매칭 실패 표시. 처음 한 번만 true (고객 푸시를 회차마다 반복하지 않게) */
    public boolean markMatchingFailed(Instant now) {
        // 부르는 곳: OfferService.matchingFailed (후보가 한 명도 없거나 최대 회차까지 모두 거절·만료). 상태는 assigning 그대로 둔다
        if (matchingFailedAt != null) return false;
        matchingFailedAt = now;
        return true;
    }

    /** 배정됐는데 약속 시간 + minutes 가 지나도 시작하지 않았다 — 처음 한 번만 true (고객에게 한 번만 알린다) */
    public boolean markLateNotified(Instant now, int minutes) {
        // LateStartNotifier 가 예약을 잠근 뒤 부른다. minutes = 지각 기준 분(ManagerPenaltyService.tardyMinutes).
        // 아직 배정 상태(scheduled)이고, 알린 적 없고, 약속 시각 + minutes 가 지났을 때만 표시한다
        if (status != BookingStatus.SCHEDULED || lateNotifiedAt != null || now.isBefore(startsAt.plusSeconds(minutes * 60L))) return false;
        lateNotifiedAt = now;
        return true;
    }

    /** 운영자·자동 재매칭: 매칭 중인 예약을 새 회차로 — 표시를 지워 다시 실패하면 또 알린다 (자체 점검 #5) */
    public void restartMatching() {
        // 운영자 재매칭(OfferService.redispatchByAdmin)과 자동 재매칭(OfferService.retryFailedMatches)이 부른다.
        // matchingFromRound 는 바꾸지 않는다 — 재매칭(redispatch)·자동 재매칭이 이 구간에서 이미 콜을 받은 매니저를 계속 '받은 적' 으로 센다
        require(BookingStatus.ASSIGNING);
        matchingFailedAt = null;
        dispatchRound++;
    }

    /** scheduled → in_progress (매니저 "청소 시작", 당일). 다른 활성 작업 검사는 Service + 부분 유니크 인덱스 */
    public void start(Instant now, LocalDate todaySeoul) {
        // 부르는 곳: JobService.doStart (매니저 작업 시작). todaySeoul = 서울 달력의 오늘 — 방문일과 같아야 시작된다
        require(BookingStatus.SCHEDULED);
        if (!serviceDate.equals(todaySeoul)) throw new ApiException(ErrorCode.INVALID_STATE, "작업 당일에만 시작할 수 있어요");
        startedAt = now;
        status = BookingStatus.IN_PROGRESS;
    }

    /** in_progress → paused. job_pauses 행은 Service 가 */
    // 부르는 곳: JobService.pause — 영상이 나가고 있을 수 있으면 여기 오기 전에 INVALID_STATE 로 막힌다. 되돌리기는 resume(JobService.resume)
    public void pause() {
        require(BookingStatus.IN_PROGRESS);
        status = BookingStatus.PAUSED;
    }

    /** paused → in_progress */
    public void resume() {
        require(BookingStatus.PAUSED);
        status = BookingStatus.IN_PROGRESS;
    }

    /** in_progress/paused → done. 정산은 Service 가 같은 tx 에서, 라이브 정지는 매니저 앱이 이어서 부르는 live/stop — 없거나 실패하면 LiveReconciler(1분마다) */
    public void complete(Instant now) {
        // 부르는 곳: JobService.finish (매니저 '청소 완료'와 운영자 완료가 같이 쓴다). 일시정지 중이어도 바로 완료할 수 있다
        if (!BookingStatus.ACTIVE.contains(status)) throw invalid();
        endedAt = now;
        status = BookingStatus.DONE;
    }

    /**
     * requested/assigning/scheduled → cancelled (고객). 기한 검사(§6.1)는 여기서 — 시스템 취소(StaleBookingCleaner)는 cancelBySystem.
     */
    public void cancel(String reason, Instant now, int deadlineHours) {
        // 부르는 곳: BookingService.cancelOne (회차 하나 취소와 정기청소 전체 취소가 같이 쓴다). 기한이 지났으면 BOOKING_NOT_CANCELLABLE —
        // 문구에 지금 설정된 기한 시간을 넣는다(고정 글자 '24시간' 이면 운영자가 기한을 바꿨을 때 사실과 달랐다, 문제 목록 25번)
        if (!BookingStatus.BEFORE_START.contains(status)) throw invalid();
        if (!isBeforeDeadline(now, deadlineHours)) throw new ApiException(ErrorCode.BOOKING_NOT_CANCELLABLE, "방문 " + deadlineHours + "시간 전까지만 취소할 수 있어요");
        cancelledAt = now;
        cancelReason = reason;
        status = BookingStatus.CANCELLED;
    }

    /** 시스템 취소(방문일 경과 등). 기한 검사 없음 */
    public void cancelBySystem(String reason, Instant now) {
        // 부르는 곳: StaleBookingCleaner — 매칭 안 된 채 방문일이 지남(unassigned), 배정됐지만 시작하지 않은 채 방문 다음 날까지 지남(not_started)
        if (!BookingStatus.BEFORE_START.contains(status)) throw invalid();
        cancelledAt = now;
        cancelReason = reason;
        status = BookingStatus.CANCELLED;
    }

    /**
     * 일정 변경 (§6.1). scheduled 였으면 assigning 으로 되돌리고 매니저를 비운다(오퍼 정리는 BookingDispatcher.onRescheduled).
     * @return 변경 전 매니저 (없으면 null) — 우선 오퍼·푸시 대상
     */
    public UUID reschedule(LocalDate date, LocalTime time, Instant now, int deadlineHours) {
        // 부르는 곳: BookingService.reschedule (고객 일정 변경, 예약을 잠근 뒤). 기한이 지났으면 BOOKING_NOT_CHANGEABLE (문구에 설정된 기한 시간)
        if (!BookingStatus.BEFORE_START.contains(status)) throw invalid();
        if (!isBeforeDeadline(now, deadlineHours)) throw new ApiException(ErrorCode.BOOKING_NOT_CHANGEABLE, "방문 " + deadlineHours + "시간 전까지만 변경할 수 있어요");
        UUID previous = managerId;
        serviceDate = date;
        serviceTime = time;
        startsAt = AppClock.toInstant(date, time);
        endsAt = startsAt.plus(Duration.ofMinutes(durationMinutes));
        if (status == BookingStatus.SCHEDULED) {
            managerId = null;
            assignedAt = null;
            status = BookingStatus.ASSIGNING;
        }
        // 위에서 scheduled → assigning 으로 바뀐 예약도 여기에 들어온다. requested(아직 매칭 전인 정기 회차)는 회차를 올리지 않는다
        if (status == BookingStatus.ASSIGNING) {
            dispatchRound++;
            matchingFromRound = dispatchRound; // 새 일정 — 옛 일정의 콜은 '받은 적' 으로 세지 않는다
            matchingFailedAt = null; // 새 일정으로 매칭을 다시 시작한다 — 남겨 두면 다시 실패해도 고객 알림이 안 나가고 운영자 조회에 섞였다 (자체 점검 #19)
        }
        return previous;
    }

    /** 재매칭 회차 +1 (거절·만료 후 다시 보낼 때, B4) */
    public void nextDispatchRound() {
        // 부르는 곳: OfferService.redispatch(이번 회차 콜이 모두 닫힘)와 아래 restartAfterRelease
        require(BookingStatus.ASSIGNING);
        dispatchRound++;
    }

    /**
     * 밤이라 새 콜을 아침으로 미룬다 (OfferService, 문제 목록 2번). 매칭 중(assigning)일 때만 — 아니면 INVALID_STATE.
     * preferredManagerId = 아침에 먼저 우선 콜을 줄 매니저(없으면 null), rescheduled = 그 매니저가 일정 변경 전 이 회차를 맡았던 사람인가.
     * 이미 미뤄 둔 우선 매니저가 있고 이번에 우선 매니저가 없으면 앞의 것을 남긴다 — 밤사이 일정을 두 번 바꿔도 원래 매니저가
     * 아침에 마지막 일정으로 우선 콜과 '일정이 변경됐어요' 안내를 한 번 받는다
     */
    public void holdDispatch(Instant now, UUID preferredManagerId, boolean rescheduled) {
        require(BookingStatus.ASSIGNING);
        if (dispatchHeldAt == null) dispatchHeldAt = now; // 처음 미룬 시각을 남긴다 (운영자 조회용)
        if (preferredManagerId != null) {
            dispatchHeldManagerId = preferredManagerId;
            dispatchHeldRescheduled = rescheduled;
        }
    }

    /** 미룬 매칭을 지운다 — 아침에 보낼 때, 급해져서 지금 보낼 때, 운영자 재매칭·예약 취소 때 (OfferService) */
    public void clearDispatchHold() {
        dispatchHeldAt = null;
        dispatchHeldManagerId = null;
        dispatchHeldRescheduled = false;
    }

    /** 매니저가 방문을 취소해 다시 매칭한다(OfferService.onReleased) — 새 회차이자 새 매칭 구간: 그 전 콜(다른 매니저 수락으로 닫힌 것 포함)은 '받은 적' 으로 세지 않는다 */
    public void restartAfterRelease() {
        nextDispatchRound();
        matchingFromRound = dispatchRound;
    }

    /** 지금 상태가 expected 가 아니면 INVALID_STATE 를 던진다 (출발 상태가 하나뿐인 전이용) */
    private void require(BookingStatus expected) {
        if (status != expected) throw invalid();
    }

    /** 잘못된 전이 오류. 기본 문구는 ErrorCode.INVALID_STATE 의 것 */
    private ApiException invalid() {
        return new ApiException(ErrorCode.INVALID_STATE);
    }
}
