package kr.ansimclean.api.booking;

import java.util.Set;

// 예약 한 건이 가질 수 있는 상태 목록. enum 은 정해진 값만 가질 수 있는 자바 타입이다.
// 흐름: requested(생성) → assigning(매칭 중) → scheduled(매니저 수락) → in_progress ⇄ paused → done(일시정지 중에도 바로 완료 가능).
// 시작 전(requested·assigning·scheduled)에는 cancelled 로 갈 수 있고, 일정 변경·매니저 방문 취소는 scheduled → assigning 으로 되돌린다.
// 실제로 상태를 바꾸는 곳은 Booking 엔티티의 전이 메서드(예: beginAssigning·cancel)뿐이다.
// DB 에는 소문자 문자열로 저장되고, 둘 사이 변환은 BookingStatusConverter 가 한다.
/** bookings.status — 값은 DB 문자열과 같다 (BACKEND_PLAN.md §6.1 상태기계) */
public enum BookingStatus {
    // 괄호 안 문자열 = DB 에 저장되는 값
    REQUESTED("requested"), // 만들어졌지만 매칭 전 (정기청소의 2회차 이후는 방문 며칠 전까지 여기 머문다)
    ASSIGNING("assigning"), // 매니저들에게 콜을 보내 수락을 기다리는 중
    SCHEDULED("scheduled"), // 매니저가 수락해 방문이 확정됨
    IN_PROGRESS("in_progress"), // 매니저가 "청소 시작" 을 누름
    PAUSED("paused"), // 작업 일시정지 중
    DONE("done"), // 청소 완료
    CANCELLED("cancelled"); // 고객 취소 또는 시스템 취소

    /** 고객이 취소·변경할 수 있는 상태 (시작 전) */
    public static final Set<BookingStatus> BEFORE_START = Set.of(REQUESTED, ASSIGNING, SCHEDULED);
    /** 아직 끝나지 않은 상태 (탈퇴 차단·"예정" 탭) */
    public static final Set<BookingStatus> OPEN = Set.of(REQUESTED, ASSIGNING, SCHEDULED, IN_PROGRESS, PAUSED);
    /** 매니저가 작업 중 */
    public static final Set<BookingStatus> ACTIVE = Set.of(IN_PROGRESS, PAUSED);

    private final String db;

    /** enum 값마다 한 번 불려 DB 문자열을 기억해 둔다 */
    BookingStatus(String db) {
        this.db = db;
    }

    /** DB 에 저장하는 문자열 (예: IN_PROGRESS → "in_progress") */
    public String db() {
        return db;
    }

    /** DB 문자열 → enum. 목록에 없는 값이면 IllegalArgumentException (bookings.status 의 DB check 제약이 이 enum 과 같은 값만 허용한다) */
    public static BookingStatus fromDb(String value) {
        for (BookingStatus s : values()) if (s.db.equals(value)) return s;
        throw new IllegalArgumentException("알 수 없는 예약 상태: " + value);
    }
}
