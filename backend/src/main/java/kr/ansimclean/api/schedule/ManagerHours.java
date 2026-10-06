package kr.ansimclean.api.schedule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

// 매니저 한 명당 요일 7행(월~일). 가입 완료 때 AvailabilityService.createDefaultHours 가 만들고,
// 근무 가능 시간 화면의 수정(updateHours)이 바꾸고, 매칭(isAvailable)·콜 목록 하단 카드(status)가 읽는다.
// 기본 키가 (매니저 id, 요일) 두 칸이라 두 칸을 묶은 Key 클래스를 아래에 따로 둔다.
/** manager_hours — 요일별 기본 근무시간. weekday 0=월 … 6=일 */
@Entity
@Table(name = "manager_hours")
// @IdClass: 기본 키가 여러 칸일 때 그 칸들을 묶은 클래스(Key)를 알려 준다. 아래 @Id 두 개가 그 칸이다
@IdClass(ManagerHours.Key.class)
public class ManagerHours {
    @Id
    @Column(name = "manager_id", columnDefinition = "uuid")
    private UUID managerId;

    @Id
    @Column(nullable = false)
    private short weekday;

    // 휴무 요일이면 start_time·end_time 은 null
    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(nullable = false)
    private boolean off;

    // JPA 가 DB 행을 객체로 만들 때 쓰는 빈 생성자. 코드에서는 of(...) 로 만든다
    protected ManagerHours() {}

    /** 새 행 하나. 휴무면 시각을 null 로 둔다 */
    public static ManagerHours of(UUID managerId, int weekday, LocalTime start, LocalTime end, boolean off) {
        ManagerHours h = new ManagerHours();
        h.managerId = managerId;
        h.weekday = (short) weekday;
        h.startTime = off ? null : start;
        h.endTime = off ? null : end;
        h.off = off;
        return h;
    }

    public UUID getManagerId() { return managerId; }
    public int getWeekday() { return weekday; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public boolean isOff() { return off; }

    /** 근무시간 수정 (AvailabilityService.updateHours). 휴무면 시각은 null */
    public void update(LocalTime start, LocalTime end, boolean off) {
        this.startTime = off ? null : start;
        this.endTime = off ? null : end;
        this.off = off;
    }

    /**
     * 복합 기본 키 (manager_id, weekday). findById(new Key(매니저, 요일)) 처럼 쓴다.
     * 필드 이름·타입은 위 엔티티의 @Id 필드와 같아야 하고, 같은 키를 같다고 보도록 equals·hashCode 를 직접 둔다
     */
    public static class Key implements Serializable {
        private UUID managerId;
        private short weekday;

        public Key() {} // JPA 용 빈 생성자

        public Key(UUID managerId, short weekday) {
            this.managerId = managerId;
            this.weekday = weekday;
        }

        // o instanceof Key k: o 가 Key 이면 k 라는 이름으로 바로 꺼내 쓴다 (자바 패턴 매칭)
        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(managerId, k.managerId) && weekday == k.weekday;
        }

        @Override
        public int hashCode() {
            return Objects.hash(managerId, weekday);
        }
    }
}
