package kr.ansimclean.api.schedule;

import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.format.KoreanFormat;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.schedule.dto.AvailabilityResponse;
import kr.ansimclean.api.schedule.dto.AvailabilityStatus;
import kr.ansimclean.api.schedule.dto.HoursRequest;
import kr.ansimclean.api.schedule.dto.UnavailableRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 근무시간·근무불가 (Manager/Schedule/Availability) + 매칭이 쓰는 "이 시간에 일할 수 있는가" (§6.2-1) + 콜 화면의 근무 상태 (§6.6).
 * 가입 시 기본값: 월~금 09:00–18:00, 토·일 휴무 (Figma).
 * 쓰는 곳 예: OfferService(매칭 후보 검사 isAvailable · 콜 목록 하단 카드 status),
 * AuthService(가입 완료 때 기본 근무시간 · 탈퇴 때 forget).
 * 휴무·근무 불가가 이미 맡은 작업과 겹치는지는 ManagerWorkLookup(구현: BookingService)으로 묻는다.
 */
@Service
// 클래스 전체 @Transactional (CODE_GUIDE.md 3-3). noRollbackFor 는 없어 검사에 걸리면(ApiException) 그 요청의 변경이 모두 되돌아간다.
// createDefaultHours·forget 은 가입 완료·탈퇴(파기) 트랜잭션 안에서 불려 그 묶음에 합쳐진다 — 여기서 실패하면 그 가입 완료·탈퇴도 함께 되돌아간다
@Transactional
public class AvailabilityService {
    // 가입 때 만드는 기본 근무시간 09:00~18:00
    static final LocalTime DEFAULT_START = LocalTime.of(9, 0);
    static final LocalTime DEFAULT_END = LocalTime.of(18, 0);
    static final String[] DAY = { "월", "화", "수", "목", "금", "토", "일" }; // 요일 번호(0=월 … 6=일) → 화면 요일 글자
    // 아래는 응답에 그대로 나가는 화면 문구. RECEIVING·OUT_OF_HOURS 는 콜 목록 하단 카드의 상태
    static final String OFF = "휴무";
    static final String ALL_DAY = "하루 종일";
    static final String PERSONAL = "개인 일정";
    static final String RECEIVING = "콜 수신 중";
    static final String OUT_OF_HOURS = "근무 시간 외";

    private final ManagerHoursRepository hours; // 요일별 기본 근무시간 (manager_hours)
    private final ManagerUnavailableRepository unavailable; // 근무 불가 (manager_unavailable)
    private final ManagerWorkLookup bookings; // 이미 맡은 작업 조회 — 실제로는 BookingService 가 들어온다
    private final AppClock clock; // 서울 시각의 '오늘'·'지금'

    /** 휴무·근무 불가가 이미 맡은 작업과 겹치면 거절한다 — 매니저는 막았다고 믿고 고객은 그 날 아무도 오지 않는 일이 없게 (검증 검토) */
    static final String CONFLICT = "그 시간에 맡은 작업이 있어요. 작업 일정을 먼저 조정해주세요";
    // 반대 방향(휴무·근무 불가를 넣기 전에 받아 둔 콜)은 콜 목록에 남지만, 수락할 때 OfferService.doAccept 가 isAvailable 로 다시 보고 막는다 (문제 목록 7번)
    /** 이미 맡은 작업을 찾는 범위 — 예약 가능 기간(booking_horizon_days, 기본 60)보다 넉넉히. 28일이면 그 뒤에 수락한 작업과 겹쳐도 막지 못했다 (전체 재점검 S1) */
    static final int LOOKAHEAD_DAYS = 366;

    private final kr.ansimclean.api.profile.ProfileService profiles; // 이 파일에서는 탈퇴와 줄 서기(holdLive)에만 쓴다

    public AvailabilityService(ManagerHoursRepository hours, ManagerUnavailableRepository unavailable, ManagerWorkLookup bookings, AppClock clock,
                               kr.ansimclean.api.profile.ProfileService profiles) {
        this.profiles = profiles;
        this.hours = hours;
        this.unavailable = unavailable;
        this.bookings = bookings;
        this.clock = clock;
    }

    /** 가입 완료 때(AuthService.completeSignup, 매니저만) 7요일 기본 근무시간 행을 만든다. 이미 있으면 아무것도 하지 않는다 */
    public void createDefaultHours(UUID managerId) {
        if (hours.existsByManagerId(managerId)) return; // 다시 불려도 이미 있는 근무시간을 기본값으로 덮어쓰지 않는다
        for (int weekday = 0; weekday <= 6; weekday++) {
            boolean off = weekday >= 5; // 5=토, 6=일
            hours.save(ManagerHours.of(managerId, weekday, DEFAULT_START, DEFAULT_END, off));
        }
    }

    /** weekday 0=월 … 6=일 */
    static int weekdayOf(LocalDate d) {
        return d.getDayOfWeek().getValue() - 1; // 자바의 요일 번호는 월=1 … 일=7 이라 1을 뺀다
    }

    // ── 화면 ──

    /** 근무 가능 시간 화면 데이터: 요일 7줄 + 근무 불가 목록(날짜순). 반복(매주·매월)은 첫 날짜가 지나도 계속 보인다 */
    @Transactional(readOnly = true)
    public AvailabilityResponse availability(UUID managerId) {
        LocalDate today = clock.today();
        List<AvailabilityResponse.UnavailableItem> items = unavailable.findByManagerIdOrderByDate(managerId).stream()
                .filter(u -> !ManagerUnavailable.NONE.equals(u.getRepeat()) || !u.getDate().isBefore(today)) // 지난 1회성은 숨긴다
                .map(AvailabilityService::toItem).toList();
        return new AvailabilityResponse(defaultHours(managerId), items);
    }

    /** 기본 근무시간 수정. 7행 전부 오지 않아도 온 요일만 바꾼다 */
    // 같은 요일이 두 번·시각 형식 오류·시작 ≥ 종료는 400. 이미 맡은 작업의 시작 시각이 새 근무시간 밖(또는 휴무)이 되는 요일은 409.
    // 한 줄이라도 걸리면 예외로 트랜잭션 전체가 되돌아가 앞서 바꾼 요일도 저장되지 않는다. 응답은 바뀐 뒤의 7줄
    public List<AvailabilityResponse.DayHours> updateHours(UUID managerId, List<HoursRequest> rows) {
        profiles.holdLive(managerId); // 탈퇴와 줄을 선다 — 탈퇴(근무시간·근무 불가를 그 자리에서 지운다) 뒤에 커밋된 쓰기가 남지 않게. 탈퇴가 먼저면 401 (자체 점검 후속 검토)
        Set<Integer> seen = new HashSet<>();
        for (HoursRequest r : rows) {
            if (!seen.add(r.weekday())) throw ApiException.validation("같은 요일이 두 번 있어요"); // add 는 이미 있던 값이면 false
            // 휴무면 시각은 읽지 않고 null 로 둔다
            LocalTime start = r.off() ? null : parseTime(r.start());
            LocalTime end = r.off() ? null : parseTime(r.end());
            if (!r.off() && !start.isBefore(end)) throw ApiException.validation("종료 시각은 시작 시각보다 늦어야 해요");
            requireNoWorkOutside(managerId, r.weekday(), r.off(), start, end);
            // 그 요일 행이 없으면 새로 저장하고, 있으면 값을 바꾼다 (바꾼 값은 커밋 때 저장된다, CODE_GUIDE.md 3-3)
            ManagerHours h = hours.findById(new ManagerHours.Key(managerId, (short) r.weekday()))
                    .orElseGet(() -> hours.save(ManagerHours.of(managerId, r.weekday(), start, end, r.off())));
            h.update(start, end, r.off());
        }
        return defaultHours(managerId);
    }

    /** 탈퇴 + 5년 파기 — 근무시간과 근무 불가(사유는 자유 입력)를 지운다 (DeletedAccountPurger) */
    // 탈퇴 트랜잭션 안에서(AuthService.deleteAccount, 매니저만) 지우고, 파기(DeletedAccountPurger.purgeOne)가 한 번 더 부른다
    // — 남은 행이 없으면 지울 것도 없다
    public void forget(UUID managerId) {
        unavailable.deleteByManagerId(managerId);
        hours.deleteByManagerId(managerId);
    }

    /** 근무 불가 삭제 — 내 것만 (남의 id 는 404). 잘못 넣은 날짜·'매주' 를 되돌릴 유일한 길 (검증 검토) */
    public void removeUnavailable(UUID managerId, UUID id) {
        // filter 가 남의 행을 '없음' 으로 바꾼다 → 남의 id 도 없는 id 와 똑같이 404 (존재를 드러내지 않는다)
        ManagerUnavailable u = unavailable.findById(id).filter(x -> x.getManagerId().equals(managerId)).orElseThrow(ApiException::notFound);
        unavailable.delete(u);
    }

    /**
     * 앞으로 LOOKAHEAD_DAYS 안에 맡은 작업(scheduled/in_progress/paused)이 이 근무 불가와 겹치면 INVALID_STATE.
     * 1회성(none)이고 date 가 오늘보다 앞이면 검사하지 않는다 — 그 날 하루만 막는 규칙(ManagerUnavailable.appliesOn)이라 앞으로 막을 날이 없다.
     * 예전에는 nth 가 주 단위로 넘어가 오늘 이후 첫 같은 요일을 검사해, 막지도 않을 날의 작업 때문에 409 가 났다 (문제 목록 12번).
     * 지난 날짜 입력은 거절하지 않고 저장한다 — 목록(availability)에서는 지난 1회성을 숨긴다
     */
    private void requireNoWorkIn(UUID managerId, LocalDate date, boolean allDay, LocalTime start, LocalTime end, String repeat) {
        LocalDate today = clock.today();
        if (ManagerUnavailable.NONE.equals(repeat) && date.isBefore(today)) return; // 지난 1회성 — 막는 날이 없다
        // 매번 원래 날짜에서 i번째를 구한다 — 누적해서 더하면 31일 → 2월 28일 → 3월 28일 … 로 밀려 실제로 막는 날(appliesOn)과 다른 날을 봤다 (자체 점검 #10)
        // 끝 조건이 없는 반복문 — 검사 범위(오늘 + LOOKAHEAD_DAYS)를 넘거나, 1회성(none)을 한 번 검사하면 break 로 나온다
        for (int i = 0; ; i++) {
            LocalDate d = nth(date, repeat, i);
            if (d.isAfter(today.plusDays(LOOKAHEAD_DAYS))) break;
            if (d.isBefore(today) || (ManagerUnavailable.MONTHLY.equals(repeat) && d.getDayOfMonth() != date.getDayOfMonth())) continue; // 31일이 없는 달은 막지 않는다 (appliesOn 과 같게)
            // 근무 불가 구간을 서울 시각 기준 Instant 로 바꾼다. 하루 종일이면 그 날 0시 ~ 다음 날 0시
            Instant from = (allDay ? d.atStartOfDay(AppClock.ZONE) : d.atTime(start).atZone(AppClock.ZONE)).toInstant();
            Instant to = (allDay ? d.plusDays(1).atStartOfDay(AppClock.ZONE) : d.atTime(end).atZone(AppClock.ZONE)).toInstant();
            if (bookings.hasWorkBetween(managerId, from, to)) throw new ApiException(ErrorCode.INVALID_STATE, CONFLICT);
            if (ManagerUnavailable.NONE.equals(repeat)) break;
        }
    }

    /**
     * 첫 날짜에서 i번째 반복 날짜: 매월이면 i달 뒤, 그 밖이면 i주 뒤.
     * plusMonths 는 없는 날을 그 달 마지막 날로 맞춘다(1월 31일 + 1달 = 2월 28일) — 그래서 부르는 쪽이 일자를 다시 비교한다
     */
    private static LocalDate nth(LocalDate first, String repeat, int i) {
        return ManagerUnavailable.MONTHLY.equals(repeat) ? first.plusMonths(i) : first.plusWeeks(i);
    }

    /** 앞으로 LOOKAHEAD_DAYS 안 그 요일의 맡은 작업이 새 근무시간 밖(또는 휴무)이면 INVALID_STATE */
    private void requireNoWorkOutside(UUID managerId, int weekday, boolean off, LocalTime start, LocalTime end) {
        LocalDate today = clock.today();
        for (ManagerWorkLookup.WorkSlot b : bookings.upcomingWork(managerId, today, today.plusDays(LOOKAHEAD_DAYS))) {
            if (weekdayOf(b.date()) != weekday) continue; // 바꾸는 요일의 작업만 본다
            // 매칭과 같은 기준 — 시작 시각만 새 근무시간 안이면 된다. 끝나는 시각까지 보면 근무 종료를 넘겨 맡은 작업 때문에 근무시간을 못 바꿨다 (자체 점검 #16 후속)
            boolean outside = off || !startsWithinHours(b.start(), start, end);
            if (outside) throw new ApiException(ErrorCode.INVALID_STATE, CONFLICT);
        }
    }

    /** 근무 불가 시간 추가 */
    // 날짜·시각 형식이 틀리거나 시작 ≥ 종료면 400, 이미 맡은 작업과 겹치면 409(CONFLICT 문구). 저장한 뒤 목록 한 줄 모양으로 돌려준다
    public AvailabilityResponse.UnavailableItem addUnavailable(UUID managerId, UnavailableRequest req) {
        profiles.holdLive(managerId); // 탈퇴와 줄을 선다 — 사유는 자유 입력이라 탈퇴 뒤에 남으면 5년 파기까지 간다. 탈퇴가 먼저면 401 (자체 점검 후속 검토)
        LocalDate date;
        // UnavailableRequest 의 @Pattern 은 숫자 모양만 본다. 2026-13-40 처럼 없는 날짜는 여기서 걸러 400
        try {
            date = LocalDate.parse(req.date());
        } catch (DateTimeParseException e) {
            throw ApiException.validation("date 는 YYYY-MM-DD 형식이에요");
        }
        // 하루 종일이면 시각은 null 그대로 둔다
        LocalTime start = null;
        LocalTime end = null;
        if (!req.allDay()) {
            start = parseTime(req.start());
            end = parseTime(req.end());
            if (!start.isBefore(end)) throw ApiException.validation("종료 시각은 시작 시각보다 늦어야 해요");
        }
        String reason = req.reason() == null || req.reason().isBlank() ? null : req.reason().trim(); // 빈 사유는 null — 목록 라벨이 기본 문구가 된다(toItem)
        requireNoWorkIn(managerId, date, req.allDay(), start, end, req.repeat());
        ManagerUnavailable u = unavailable.save(ManagerUnavailable.create(managerId, date, req.allDay(), start, end, req.repeat(), reason, clock.now()));
        return toItem(u);
    }

    // ── 매칭·콜 화면 ──

    /**
     * 그 날 [start, end) 작업의 콜을 받을 수 있는가 (§6.2-1): **시작 시각이 요일 근무시간 [시작, 종료] 안**(종료 시각에 시작하는 것도 포함) ∧ 작업 전체가 근무 불가와 안 겹침.
     * 끝나는 시각은 근무 종료를 넘어도 된다 — 매니저가 콜을 보고 수락할지 정한다 (사용자 결정 2026-09-27, 자체 점검 #16 후속).
     * 예전에는 작업 전체가 근무시간 안이어야 해서, 기본값(09~18시) 매니저에게는 18시를 넘겨 끝나는 예약(가정 16·17시 등)의 콜이 아예 가지 않았다
     */
    // 예: OfferService.worksThen (예약마다 콜을 보낼 매니저 후보를 고를 때와, 콜을 수락할 때 다시 볼 때)
    // 콜을 보내는 '지금' 시각은 여기서 보지 않는다 — 밤(서울 21시~08시)에는 OfferService 가 새 콜을 아침으로 미루고(문제 목록 2번),
    // 낮에는 콜 목록 하단 카드(status)가 '근무 시간 외' 여도 콜이 간다(작업 시작 시각이 근무시간 안이면)
    @Transactional(readOnly = true)
    public boolean isAvailable(UUID managerId, LocalDate date, LocalTime start, LocalTime end) {
        // 그 요일 행이 없거나 휴무면 콜을 받지 않는다
        Optional<ManagerHours> h = hours.findById(new ManagerHours.Key(managerId, (short) weekdayOf(date)));
        if (h.isEmpty() || h.get().isOff()) return false;
        if (!startsWithinHours(start, h.get().getStartTime(), h.get().getEndTime())) return false;
        return unavailable.findByManagerIdOrderByDate(managerId).stream().noneMatch(u -> u.appliesOn(date) && u.blocks(start, end));
    }

    /**
     * 시작 시각이 근무시간 [from, to] 안인가 — 매칭(isAvailable)과 근무시간 변경 검사(requireNoWorkOutside)가 같은 기준을 쓴다.
     * 끝 시각도 넣는다: 기본값 09~18시 매니저에게 마지막 슬롯(18:00) 예약이 아무에게도 가지 않았다 — 사용자에게 물은 예(가정 16·17·18시) 그대로 매니저가 보고 정한다
     */
    static boolean startsWithinHours(LocalTime start, LocalTime from, LocalTime to) {
        return !start.isBefore(from) && !start.isAfter(to);
    }

    /** 지금 콜을 받을 수 있는 시간인가 — 콜 목록 하단 카드 (§6.6) */
    // nowSeoul 은 부르는 쪽(OfferService.calls — 승인됐고 정지가 아닌 매니저일 때만)이 서울 시각으로 넘긴다.
    // 오늘이 휴무 요일이거나 '하루 종일' 근무 불가가 걸리면 "오늘 휴무". 시간 단위 근무 불가는 여기서 보지 않는다
    // 이 카드의 '콜 수신 중'·'근무 시간 외' 는 지금 시각이 이 매니저의 근무시간 안인지만 알린다 — 매칭(isAvailable)은 작업 시작 시각을 본다.
    // 밤(서울 21시~08시)에는 새 콜 자체를 아침 08:05 로 미루므로(급한 예약 빼고, OfferService) 밤의 '근무 시간 외' 와 콜이 대체로 맞는다 (문제 목록 2번)
    @Transactional(readOnly = true)
    public AvailabilityStatus status(UUID managerId, ZonedDateTime nowSeoul) {
        LocalDate today = nowSeoul.toLocalDate();
        Optional<ManagerHours> h = hours.findById(new ManagerHours.Key(managerId, (short) weekdayOf(today)));
        boolean offToday = h.isEmpty() || h.get().isOff()
                || unavailable.findByManagerIdOrderByDate(managerId).stream().anyMatch(u -> u.isAllDay() && u.appliesOn(today));
        if (offToday) return new AvailabilityStatus(OUT_OF_HOURS, "오늘 " + OFF);
        LocalTime now = nowSeoul.toLocalTime();
        // 지금이 [시작, 종료) 안이면 '콜 수신 중'. 종료 시각 정각부터는 '근무 시간 외'
        boolean within = !now.isBefore(h.get().getStartTime()) && now.isBefore(h.get().getEndTime());
        return new AvailabilityStatus(within ? RECEIVING : OUT_OF_HOURS, "오늘 " + KoreanFormat.timeRange(h.get().getStartTime(), h.get().getEndTime()) + " 근무 설정됨");
    }

    /**
     * 화면용 요일 7줄(월~일). 행이 없는 요일은 휴무로 보여 준다.
     * hours 는 "09:00 - 18:00" 또는 "휴무" 문구, start/end 는 "HH:mm"(휴무면 null)
     */
    private List<AvailabilityResponse.DayHours> defaultHours(UUID managerId) {
        Map<Integer, ManagerHours> byDay = hours.findByManagerIdOrderByWeekday(managerId).stream().collect(Collectors.toMap(ManagerHours::getWeekday, h -> h));
        List<AvailabilityResponse.DayHours> out = new ArrayList<>();
        for (int d = 0; d <= 6; d++) {
            ManagerHours h = byDay.get(d);
            boolean off = h == null || h.isOff();
            out.add(new AvailabilityResponse.DayHours(DAY[d], off ? OFF : KoreanFormat.timeRange(h.getStartTime(), h.getEndTime()), off,
                    off ? null : KoreanFormat.hhmm(h.getStartTime()), off ? null : KoreanFormat.hhmm(h.getEndTime())));
        }
        return out;
    }

    /**
     * 근무 불가 행 → 목록 한 줄. 라벨은 사유가 있으면 사유, 없으면 하루 종일 '휴무' · 시간 단위 '개인 일정'.
     * date 는 "9월 18일", time 은 "14:00 - 18:00" 또는 "하루 종일", repeat 은 서버 값(none|weekly|monthly) 그대로
     */
    static AvailabilityResponse.UnavailableItem toItem(ManagerUnavailable u) {
        String label = u.getReason() != null ? u.getReason() : (u.isAllDay() ? OFF : PERSONAL);
        String time = u.isAllDay() ? ALL_DAY : KoreanFormat.timeRange(u.getStartTime(), u.getEndTime());
        return new AvailabilityResponse.UnavailableItem(u.getId(), KoreanFormat.dateShort(u.getDate()), label, time, u.getRepeat());
    }

    /** "09:00" 같은 시각 문자열 → LocalTime. 비었거나(null) 형식이 틀리면 400(VALIDATION) */
    static LocalTime parseTime(String s) {
        try {
            return LocalTime.parse(s);
        } catch (DateTimeParseException | NullPointerException e) { // null 을 넘기면 parse 가 NullPointerException 을 던진다
            throw ApiException.validation("시각은 HH:mm 형식이에요");
        }
    }
}
