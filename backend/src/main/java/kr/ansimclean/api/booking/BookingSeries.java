package kr.ansimclean.api.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * booking_series — 정기청소 묶음 (BACKEND_PLAN.md §6.8).
 * **매월 같은 날짜에 1회씩 termMonths 개월** (사용자 결정 2026-09-22 — 그 전에는 '한 달 동안 매주'였다). 회차는 bookings(series_id).
 * 주말도 선택한 방문일을 유지한다. 해당 날짜가 없는 달만 그 달 마지막 날을 사용한다.
 * 고객이 '정기청소' 로 예약하면 BookingService.doCreate 가 이 행 하나와 회차별 예약(bookings) 행들을 같이 만든다.
 * 묶음 동작(전체 취소·회차 매칭·종료·종료 안내)은 BookingSeriesService, 남은 회차가 없을 때 닫는 일은 BookingService.endSeriesIfNothingLeft.
 */
@Entity
@Table(name = "booking_series")
public class BookingSeries {
    // status 칸에 들어가는 값 (booking_series 의 DB check 제약이 이 값들만 허용한다)
    public static final String ACTIVE = "active"; // 진행 중 — 탈퇴를 막는다
    public static final String ENDED = "ended"; // term_end 가 지났거나, 남은 회차가 없고 마친 회차가 있음
    public static final String CANCELLED = "cancelled"; // 고객 전체 취소, 또는 회차가 전부 취소됨

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "customer_id", columnDefinition = "uuid", nullable = false)
    private UUID customerId;

    @Column(name = "category_key", columnDefinition = "text", nullable = false)
    private String categoryKey;

    @Column(nullable = false)
    private short weekday; // 첫 방문 요일 (0=월 … 6=일). 매월 방식으로 바뀐 뒤로는 기록용이다

    @Column(name = "service_time", nullable = false)
    private LocalTime serviceTime; // 만들 때 모든 회차에 준 방문 시각 (회차 하나의 일정 변경은 그 bookings 행만 바꾼다)

    // 주소·요청사항·안내 칸은 고객 계정 파기(BookingAccountPurge) 때 BookingRepository.scrubCustomerSeries 가 지운다
    @Column(name = "road_address", columnDefinition = "text", nullable = false)
    private String roadAddress;

    @Column(name = "detail_address", columnDefinition = "text")
    private String detailAddress;

    // @JdbcTypeCode(ARRAY) + text[] = Postgres 배열 칸을 자바 List 로 읽고 쓴다
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "free_options", columnDefinition = "text[]", nullable = false)
    private List<String> freeOptions;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "paid_options", columnDefinition = "text[]", nullable = false)
    private List<String> paidOptions;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]", nullable = false)
    private List<String> notices;

    @Column(columnDefinition = "text")
    private String request; // 고객 요청사항 (자유 입력)

    @Column(columnDefinition = "text", nullable = false)
    private String status; // ACTIVE / ENDED / CANCELLED 중 하나

    @Column(name = "term_start", nullable = false)
    private LocalDate termStart; // 첫 방문일

    @Column(name = "term_end", nullable = false)
    private LocalDate termEnd; // 마지막 회차 날짜 (create 가 정한다)

    @Column(name = "visits_planned", nullable = false)
    private int visitsPlanned; // 만들 때 정한 회차 수

    @Column(name = "ending_notified_at")
    private Instant endingNotifiedAt; // '곧 끝나요' 안내를 보낸 시각 — 한 번만 보내기 위한 표시

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    /** JPA 가 DB 에서 읽은 행으로 객체를 만들 때 쓰는 빈 생성자. 코드에서는 create() 를 쓴다 */
    protected BookingSeries() {}

    // 새 시리즈 객체를 만든다(저장은 호출한 쪽의 seriesRepo.save).
    // 옵션·안내 목록은 List.copyOf 로 복사해 바깥 목록이 바뀌어도 영향이 없게 한다.
    /** 첫 방문일부터 매월 1회씩 termMonths 회 (term_end = 마지막 회차 날짜). weekday 는 첫 방문 요일 기록용 0=월 … 6=일 */
    public static BookingSeries create(UUID customerId, String categoryKey, LocalDate firstVisit, LocalTime time, int termMonths,
                                       String roadAddress, String detailAddress, List<String> freeOptions, List<String> paidOptions,
                                       List<String> notices, String request, Instant now) {
        BookingSeries s = new BookingSeries();
        s.id = UUID.randomUUID();
        s.customerId = customerId;
        s.categoryKey = categoryKey;
        s.weekday = (short) (firstVisit.getDayOfWeek().getValue() - 1);
        s.serviceTime = time;
        s.roadAddress = roadAddress;
        s.detailAddress = detailAddress;
        s.freeOptions = List.copyOf(freeOptions);
        s.paidOptions = List.copyOf(paidOptions);
        s.notices = List.copyOf(notices);
        s.request = request;
        s.status = ACTIVE;
        List<LocalDate> dates = monthlyDates(firstVisit, termMonths);
        s.termStart = firstVisit;
        s.termEnd = dates.get(dates.size() - 1); // 마지막 회차 날짜 — 그 회차를 마치면 시리즈가 끝난다
        s.visitsPlanned = dates.size();
        s.createdAt = now;
        return s;
    }

    /** 첫 방문일부터 매월 같은 날짜 n회. 주말도 유지하고, 없는 날짜만 그 달 마지막 날로 (java plusMonths 규칙) */
    public static List<LocalDate> monthlyDates(LocalDate firstVisit, int months) {
        List<LocalDate> dates = new ArrayList<>();
        // 매번 첫 방문일에 i 개월을 더한다. 앞 회차 날짜에 이어 더하면 1/31 → 2/28 → 3/28 처럼 한 번 줄어든 날이 계속 이어진다
        for (int i = 0; i < months; i++) dates.add(firstVisit.plusMonths(i));
        return dates;
    }

    /** 기간 안의 매월 같은 날짜 — 저장된 시리즈(term_start ~ term_end)에서 회차를 다시 만들 때 */
    public static List<LocalDate> visitDates(LocalDate termStart, LocalDate termEnd) {
        List<LocalDate> dates = new ArrayList<>();
        // visits_planned 를 쓰지 않고 term_end 를 넘을 때까지 센다 — create 가 term_end 를 마지막 회차 날짜로 두었고
        // 매번 첫 방문일 기준으로 더하므로 create 때와 같은 목록이 나온다
        for (int i = 0; ; i++) {
            LocalDate d = termStart.plusMonths(i);
            if (d.isAfter(termEnd)) return dates;
            dates.add(d);
        }
    }

    /** 이 시리즈의 회차 날짜 목록 — BookingService.doCreate 가 회차별 예약 행을 만들 때 쓴다 */
    public List<LocalDate> visitDates() {
        return visitDates(termStart, termEnd);
    }

    // 읽기용 getter (setter 없음). 자바에서는 create·cancel·end·markEndingNotified 로만 바뀌고,
    // 계정 파기 때는 BookingRepository.scrubCustomerSeries 가 SQL 로 주소·요청 칸을 직접 비운다
    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public String getCategoryKey() { return categoryKey; }
    public short getWeekday() { return weekday; }
    public LocalTime getServiceTime() { return serviceTime; }
    public String getRoadAddress() { return roadAddress; }
    public String getDetailAddress() { return detailAddress; }
    public List<String> getFreeOptions() { return freeOptions; }
    public List<String> getPaidOptions() { return paidOptions; }
    public List<String> getNotices() { return notices; }
    public String getRequest() { return request; }
    public String getStatus() { return status; }
    public LocalDate getTermStart() { return termStart; }
    public LocalDate getTermEnd() { return termEnd; }
    public int getVisitsPlanned() { return visitsPlanned; }
    public Instant getEndingNotifiedAt() { return endingNotifiedAt; }
    public Instant getCancelledAt() { return cancelledAt; }

    /** 아직 진행 중(active)인가 — 취소·종료·회차 매칭 전에 확인한다 */
    public boolean isActive() {
        return ACTIVE.equals(status);
    }

    // active → cancelled. active 가 아니면 INVALID_STATE 라서 부르는 쪽은 isActive() 를 먼저 본다.
    // 아래 설명의 고객 전체 취소(BookingSeriesService.cancelWhole) 말고도, 회차가 전부 취소돼 남은 것이 없을 때
    // BookingService.endSeriesIfNothingLeft 가 부른다
    /** 고객이 "정기청소 전체" 취소 */
    public void cancel(Instant now) {
        if (!isActive()) throw new ApiException(ErrorCode.INVALID_STATE);
        status = CANCELLED;
        cancelledAt = now;
    }

    // active → ended. 이미 끝났거나 취소된 시리즈면 아무것도 하지 않는다(예외 없음).
    // 아래 설명의 경우(BookingSeriesService.endExpired) 말고도, term_end 전이라도 남은 회차가 없고 마친 회차가 있으면
    // BookingService.endSeriesIfNothingLeft 가 부른다
    /** term_end 가 지났다 (SeriesDispatcher) */
    public void end() {
        if (isActive()) status = ENDED;
    }

    /** '곧 끝나요' 안내를 보냈다고 표시한다 — 이후 notifyEnding 조회(ending_notified_at is null)에서 빠진다 */
    public void markEndingNotified(Instant now) {
        endingNotifiedAt = now;
    }
}
