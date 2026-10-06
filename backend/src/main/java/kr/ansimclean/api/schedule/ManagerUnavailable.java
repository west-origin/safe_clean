package kr.ansimclean.api.schedule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

// 매니저가 '근무 불가 시간 추가' 바텀시트에서 넣은 한 건. AvailabilityService 가 만들고 지운다.
// 매칭(isAvailable)은 이 날·이 시간과 겹치는 예약의 콜을 보내지 않고, 하루 종일이면 콜 목록 하단 카드가 '오늘 휴무' 가 된다(status).
/** manager_unavailable — 근무 불가 시간. repeat: none | weekly(같은 요일) | monthly(같은 일자) (§6.2-1) */
@Entity
@Table(name = "manager_unavailable")
public class ManagerUnavailable {
    // repeat 칸에 들어가는 값. 앱은 '반복 없음'·'매주'·'매월' 문구를 이 값으로 바꿔 보낸다(api/manager.js)
    public static final String NONE = "none";
    public static final String WEEKLY = "weekly";
    public static final String MONTHLY = "monthly";

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "manager_id", columnDefinition = "uuid", nullable = false)
    private UUID managerId;

    // 1회성이면 그 날, 반복이면 첫 날(이 날부터 반복한다)
    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "all_day", nullable = false)
    private boolean allDay;

    // 하루 종일이면 start_time·end_time 은 null
    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(columnDefinition = "text", nullable = false)
    private String repeat;

    // 매니저가 쓴 자유 입력 사유(없으면 null). 있으면 목록 라벨로 보인다
    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // JPA 가 DB 행을 객체로 만들 때 쓰는 빈 생성자. 코드에서는 create(...) 로 만든다
    protected ManagerUnavailable() {}

    /** 새 근무 불가 한 건. id 는 여기서 만들고, 하루 종일이면 시각을 null 로 둔다 */
    public static ManagerUnavailable create(UUID managerId, LocalDate date, boolean allDay, LocalTime start, LocalTime end, String repeat, String reason, Instant now) {
        ManagerUnavailable u = new ManagerUnavailable();
        u.id = UUID.randomUUID();
        u.managerId = managerId;
        u.date = date;
        u.allDay = allDay;
        u.startTime = allDay ? null : start;
        u.endTime = allDay ? null : end;
        u.repeat = repeat;
        u.reason = reason;
        u.createdAt = now;
        return u;
    }

    public UUID getId() { return id; }
    public UUID getManagerId() { return managerId; }
    public LocalDate getDate() { return date; }
    public boolean isAllDay() { return allDay; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public String getRepeat() { return repeat; }
    public String getReason() { return reason; }

    /** 이 날에 적용되는가 (반복은 첫 날짜 이후로만) */
    public boolean appliesOn(LocalDate d) {
        // 1회성은 date 그 날만 막는다 — 그래서 등록 전 검사(AvailabilityService.requireNoWorkIn)는 date 가 지난 1회성이면 겹침을 보지 않는다
        // switch 식: repeat 값에 맞는 줄의 결과를 돌려준다. default 는 1회성(none) — 그 날 하루만
        return switch (repeat) {
            case WEEKLY -> !d.isBefore(date) && d.getDayOfWeek() == date.getDayOfWeek();
            case MONTHLY -> !d.isBefore(date) && d.getDayOfMonth() == date.getDayOfMonth();
            default -> date.equals(d);
        };
    }

    /** [start, end) 작업 구간과 겹치는가 */
    public boolean blocks(LocalTime start, LocalTime end) {
        // 두 구간이 겹치는 조건: 이쪽 시작 < 저쪽 끝 이고 이쪽 끝 > 저쪽 시작. 끝이 딱 맞닿기만 하면 겹치지 않는다
        return allDay || (startTime.isBefore(end) && endTime.isAfter(start));
    }
}
