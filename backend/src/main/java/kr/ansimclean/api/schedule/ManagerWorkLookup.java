package kr.ansimclean.api.schedule;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * 매니저가 맡은 작업(scheduled/in_progress/paused)을 근무 일정 쪽에서 본다 — 휴무·근무 불가가 작업과 겹치지 않게.
 * 구현은 booking.BookingService (booking → schedule 방향. 반대면 auth → schedule → booking → auth 순환)
 * interface 는 메서드 이름·모양만 정해 두고, 실제 동작은 구현 클래스(BookingService)가 채운다.
 * AvailabilityService 가 쓴다: 근무 불가 추가 전 hasWorkBetween, 기본 근무시간 변경 전 upcomingWork.
 */
public interface ManagerWorkLookup {
    /** [from, to) 와 겹치는 맡은 작업이 있는가 */
    boolean hasWorkBetween(UUID managerId, Instant from, Instant to);

    /** from~to 날짜의 맡은 작업 (시작 시각·끝 시각) */
    List<WorkSlot> upcomingWork(UUID managerId, LocalDate from, LocalDate to);

    /**
     * 맡은 작업 한 건: 방문일·약속 시작 시각·끝나는 시각(Instant).
     * 지금 AvailabilityService 는 date·start 만 쓴다 (근무시간 검사는 시작 시각만 보므로)
     */
    record WorkSlot(LocalDate date, LocalTime start, Instant endsAt) {}
}
