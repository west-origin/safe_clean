package kr.ansimclean.api.schedule;

import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.schedule.dto.AvailabilityResponse;
import kr.ansimclean.api.schedule.dto.AvailabilityStatus;
import kr.ansimclean.api.schedule.dto.HoursRequest;
import kr.ansimclean.api.schedule.dto.UnavailableRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 근무시간·근무불가 규칙 (§6.2-1 매칭 조건, §6.6 콜 수신 상태) */
// AvailabilityService(매니저 근무시간·근무 불가, 매칭의 '이 시간에 일할 수 있나', 콜 목록 하단 근무 상태) 단위 테스트.
// 저장소·맡은 작업 조회(ManagerWorkLookup)·ProfileService 는 Mockito 가짜다. 시계는 서울 2026-09-15(화) 14:00 에 고정한다.
// 확인하는 것: 매칭 가능 판정(시작 시각 기준), 반복 근무 불가 펼치기, 근무 상태 문구, 근무시간·근무 불가 저장 검증,
// 이미 맡은 작업과 겹치는 휴무·근무 불가 거절(CONFLICT 문구), 남의 근무 불가 삭제 404, 탈퇴와 겹친 쓰기 거절
class AvailabilityServiceTest {
    @Test
    void 주말_근무를_설정하면_토일_예약을_매칭하고_근무불가는_존중한다() {
        LocalDate saturday = LocalDate.of(2026, 9, 19);
        LocalDate sunday = saturday.plusDays(1);
        assertThat(service.isAvailable(M, saturday, LocalTime.of(10, 0), LocalTime.of(13, 0))).isFalse();
        service.updateHours(M, List.of(new HoursRequest(5, "09:00", "18:00", false), new HoursRequest(6, "09:00", "18:00", false)));
        assertThat(service.isAvailable(M, saturday, LocalTime.of(10, 0), LocalTime.of(13, 0))).isTrue();
        assertThat(service.isAvailable(M, sunday, LocalTime.of(10, 0), LocalTime.of(13, 0))).isTrue();
        when(unavailable.findByManagerIdOrderByDate(M)).thenReturn(List.of(ManagerUnavailable.create(M, saturday, true, null, null, "none", null, NOW)));
        assertThat(service.isAvailable(M, saturday, LocalTime.of(10, 0), LocalTime.of(13, 0))).isFalse();
        assertThat(service.isAvailable(M, sunday, LocalTime.of(10, 0), LocalTime.of(13, 0))).isTrue();
    }

    static final Instant NOW = Instant.parse("2026-09-15T05:00:00Z"); // 화 14:00
    static final AppClock CLOCK = new AppClock(Clock.fixed(NOW, AppClock.ZONE));
    static final UUID M = UUID.randomUUID(); // 테스트 매니저 id
    static final LocalDate THU = LocalDate.of(2026, 9, 17); // 이번 주 목요일 (근무일)

    // 따로 정하지 않은 가짜 메서드는 기본값(false·빈 목록·null)을 돌려준다 —
    // 예: hasWorkBetween 은 기본 false(맡은 작업 없음), upcomingWork 는 빈 목록, profiles.holdLive 는 null(던지지 않음 = 탈퇴 안 함)
    ManagerHoursRepository hours = mock(ManagerHoursRepository.class);
    ManagerUnavailableRepository unavailable = mock(ManagerUnavailableRepository.class);
    ManagerWorkLookup bookings = mock(ManagerWorkLookup.class);
    kr.ansimclean.api.profile.ProfileService profiles = mock(kr.ansimclean.api.profile.ProfileService.class);
    AvailabilityService service;

    /**
     * 각 테스트 전에: 가입 기본값과 같은 요일 7줄(월~금 09:00–18:00, 토·일 휴무)을 진짜 ManagerHours 객체로 만들어
     * 요일별 조회와 7줄 조회가 같은 객체를 돌려주게 한다 — 그래서 updateHours 로 바꾼 값이 응답 7줄에 그대로 보인다.
     * 근무 불가는 처음엔 없고, save 는 받은 객체를 그대로 돌려준다
     */
    @BeforeEach
    void setUp() {
        service = new AvailabilityService(hours, unavailable, bookings, CLOCK, profiles);
        List<ManagerHours> week = new java.util.ArrayList<>();
        for (int d = 0; d <= 6; d++) {
            ManagerHours h = ManagerHours.of(M, d, LocalTime.of(9, 0), LocalTime.of(18, 0), d >= 5);
            week.add(h);
            when(hours.findById(new ManagerHours.Key(M, (short) d))).thenReturn(Optional.of(h));
        }
        when(hours.findByManagerIdOrderByWeekday(M)).thenReturn(week);
        when(unavailable.findByManagerIdOrderByDate(M)).thenReturn(List.of());
        when(unavailable.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(hours.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // isAvailable(매니저, 날짜, 시작, 끝) = 매칭이 이 매니저에게 콜을 보낼 수 있는지. 목요일 근무 09:00–18:00 기준으로
    // 시작 시각만 [09:00, 18:00] 안이면 된다(끝이 18시를 넘어도 된다). 토요일은 휴무라 false
    @Test
    void 시작_시각이_근무시간_안이고_휴무가_아니어야_가능하고_끝나는_시각은_근무_종료를_넘어도_된다() { // 자체 점검 #16 후속 (사용자 결정 2026-09-27)
        assertThat(service.isAvailable(M, THU, LocalTime.of(10, 0), LocalTime.of(13, 0))).isTrue();
        assertThat(service.isAvailable(M, THU, LocalTime.of(17, 0), LocalTime.of(20, 0))).isTrue(); // 예전: false — 18시를 넘겨 끝나 콜이 아예 가지 않았다
        assertThat(service.isAvailable(M, THU, LocalTime.of(18, 0), LocalTime.of(21, 0))).isTrue(); // 근무 종료 시각에 시작해도 콜은 간다(마지막 슬롯 18:00)
        assertThat(service.isAvailable(M, THU, LocalTime.of(18, 30), LocalTime.of(21, 30))).isFalse(); // 근무 종료 뒤 시작 — 밖
        assertThat(service.isAvailable(M, THU, LocalTime.of(8, 0), LocalTime.of(11, 0))).isFalse(); // 근무 시작 전
        assertThat(service.isAvailable(M, LocalDate.of(2026, 9, 19), LocalTime.of(10, 0), LocalTime.of(13, 0))).isFalse(); // 토 휴무
    }

    // 목요일 19:00–21:00 근무 불가가 있을 때: 17–20시 작업은 겹쳐서 false, 15–18시 작업은 안 겹쳐서 true
    @Test
    void 근무_종료를_넘겨_끝나는_작업도_근무_불가와_겹치면_안_된다() { // 끝나는 시각을 넘긴 몫도 매니저가 막아 둔 시간은 존중한다
        ManagerUnavailable evening = ManagerUnavailable.create(M, THU, false, LocalTime.of(19, 0), LocalTime.of(21, 0), "none", null, NOW);
        when(unavailable.findByManagerIdOrderByDate(M)).thenReturn(List.of(evening));
        assertThat(service.isAvailable(M, THU, LocalTime.of(17, 0), LocalTime.of(20, 0))).isFalse();
        assertThat(service.isAvailable(M, THU, LocalTime.of(15, 0), LocalTime.of(18, 0))).isTrue();
    }

    // 근무 불가 세 종류(1회성 none · 매주 weekly · 매월 monthly)를 넣고, 날짜마다 적용되는지와 시간이 겹치는지를 본다.
    // 반복은 첫 날짜 이후에만 적용된다(9/3 은 9/10 매주 규칙의 시작 전이라 가능)
    @Test
    void 근무_불가는_반복_규칙대로_펼쳐서_겹침을_본다() {
        when(unavailable.findByManagerIdOrderByDate(M)).thenReturn(List.of(
                ManagerUnavailable.create(M, THU, false, LocalTime.of(12, 0), LocalTime.of(14, 0), "none", null, NOW),
                ManagerUnavailable.create(M, LocalDate.of(2026, 9, 10), false, LocalTime.of(9, 0), LocalTime.of(10, 0), "weekly", null, NOW),  // 목요일마다 9-10
                ManagerUnavailable.create(M, LocalDate.of(2026, 8, 24), true, null, null, "monthly", "휴무", NOW)));                            // 매월 24일
        assertThat(service.isAvailable(M, THU, LocalTime.of(10, 0), LocalTime.of(13, 0))).isFalse();  // 12-14 와 겹침
        assertThat(service.isAvailable(M, THU, LocalTime.of(14, 0), LocalTime.of(17, 0))).isTrue();
        assertThat(service.isAvailable(M, THU, LocalTime.of(9, 0), LocalTime.of(11, 0))).isFalse();   // 매주 목 9-10
        assertThat(service.isAvailable(M, LocalDate.of(2026, 9, 24), LocalTime.of(10, 0), LocalTime.of(13, 0))).isFalse(); // 매월 24일 하루 종일
        assertThat(service.isAvailable(M, LocalDate.of(2026, 9, 16), LocalTime.of(10, 0), LocalTime.of(13, 0))).isTrue();  // 1회성은 다른 날 무관
        assertThat(service.isAvailable(M, LocalDate.of(2026, 9, 3), LocalTime.of(9, 0), LocalTime.of(11, 0))).isTrue();    // 반복 시작 전
    }

    // status = 매니저 앱 콜 목록 하단 카드의 상태·설명 문구. 화 14시 → '콜 수신 중', 19시 → '근무 시간 외',
    // 4일 뒤 토요일(휴무 요일) → '오늘 휴무', 오늘에 '하루 종일' 근무 불가를 넣어도 → '오늘 휴무'
    @Test
    void 콜_수신_상태는_지금이_근무시간_안인가로() {
        ZonedDateTime tue14 = NOW.atZone(AppClock.ZONE);
        AvailabilityStatus in = service.status(M, tue14);
        assertThat(in.status()).isEqualTo("콜 수신 중");
        assertThat(in.detail()).isEqualTo("오늘 09:00 - 18:00 근무 설정됨");
        assertThat(service.status(M, tue14.withHour(19)).status()).isEqualTo("근무 시간 외");
        assertThat(service.status(M, tue14.plusDays(4)).detail()).isEqualTo("오늘 휴무"); // 토
        when(unavailable.findByManagerIdOrderByDate(M)).thenReturn(List.of(ManagerUnavailable.create(M, tue14.toLocalDate(), true, null, null, "none", null, NOW)));
        assertThat(service.status(M, tue14).detail()).isEqualTo("오늘 휴무");
    }

    // 월(0)을 10–17시로, 금(4)을 휴무로 바꾸면 응답은 늘 요일 7줄이고 바뀐 값이 보인다 (토(5)는 원래 휴무).
    // 400 이 나는 입력: 시작 ≥ 종료, 같은 요일 두 번, "9시" 처럼 HH:mm 이 아닌 시각.
    // extracting("day", "hours", "off") = 객체에서 이 이름의 값들을 꺼내 차례로 비교한다
    @Test
    void 근무시간_수정은_검증하고_7행을_돌려준다() {
        List<AvailabilityResponse.DayHours> rows = service.updateHours(M, List.of(new HoursRequest(0, "10:00", "17:00", false), new HoursRequest(4, null, null, true)));
        assertThat(rows).hasSize(7);
        assertThat(rows.get(0)).extracting("day", "hours", "off").containsExactly("월", "10:00 - 17:00", false);
        assertThat(rows.get(4)).extracting("day", "hours", "off").containsExactly("금", "휴무", true);
        assertThat(rows.get(5).off()).isTrue();

        assertThatThrownBy(() -> service.updateHours(M, List.of(new HoursRequest(0, "18:00", "09:00", false)))).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.VALIDATION);
        assertThatThrownBy(() -> service.updateHours(M, List.of(new HoursRequest(1, "09:00", "10:00", false), new HoursRequest(1, "11:00", "12:00", false)))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.updateHours(M, List.of(new HoursRequest(2, "9시", "10:00", false)))).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.VALIDATION);
    }

    // 추가 응답 한 줄의 문구: 날짜 "9월 23일", 라벨(사유가 없으면 하루 종일은 '휴무', 사유가 있으면 사유), 시간("하루 종일" 또는 "14:00 - 18:00").
    // 시작 ≥ 종료는 400. 목록 조회에서는 오늘(9/15) 전의 1회성만 숨기고, 첫 날짜가 지난 반복은 남긴다 → 3줄 중 2줄
    @Test
    void 근무_불가_추가는_카드_문구를_만들고_지난_1회성은_목록에서_숨긴다() {
        AvailabilityResponse.UnavailableItem allDay = service.addUnavailable(M, new UnavailableRequest("2026-09-23", true, null, null, "none", null));
        assertThat(allDay).extracting("date", "label", "time", "repeat").containsExactly("9월 23일", "휴무", "하루 종일", "none");
        AvailabilityResponse.UnavailableItem partial = service.addUnavailable(M, new UnavailableRequest("2026-09-18", false, "14:00", "18:00", "weekly", "개인 일정"));
        assertThat(partial).extracting("date", "label", "time", "repeat").containsExactly("9월 18일", "개인 일정", "14:00 - 18:00", "weekly");
        assertThatThrownBy(() -> service.addUnavailable(M, new UnavailableRequest("2026-09-18", false, "18:00", "14:00", "none", null))).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.VALIDATION);

        when(unavailable.findByManagerIdOrderByDate(M)).thenReturn(List.of(
                ManagerUnavailable.create(M, LocalDate.of(2026, 9, 1), true, null, null, "none", null, NOW),      // 지난 것
                ManagerUnavailable.create(M, LocalDate.of(2026, 9, 1), true, null, null, "weekly", null, NOW),    // 반복은 남는다
                ManagerUnavailable.create(M, LocalDate.of(2026, 9, 18), false, LocalTime.of(14, 0), LocalTime.of(18, 0), "none", null, NOW)));
        AvailabilityResponse res = service.availability(M);
        assertThat(res.unavailable()).hasSize(2);
        assertThat(res.defaultHours()).extracting(AvailabilityResponse.DayHours::day).containsExactly("월", "화", "수", "목", "금", "토", "일");
    }

    // 앞부분(근무 불가 추가): 가짜 hasWorkBetween 이 '구간 [from, to) 안에 9/17 10:00 이 들어가면 true' 로 답한다.
    // 그날 하루 종일·매주 목요일은 거절, 그날 오후만(14–18시)은 통과.
    // 뒷부분(근무시간 변경): 가짜 upcomingWork 가 목요일 10:00 작업 한 건을 돌려준다. 목요일을 휴무로 하거나
    // 근무 시작을 11시로 늦추면 거절, 작업 시작 10:00 이 새 근무시간 [시작, 종료] 안이면 통과
    @Test
    void 맡은_작업과_겹치는_근무_불가나_휴무는_거절한다() {
        // 9/17(목) 10:00 작업이 잡혀 있다
        when(bookings.hasWorkBetween(eq(M), any(), any())).thenAnswer(inv -> {
            Instant from = inv.getArgument(1);
            Instant to = inv.getArgument(2);
            Instant work = Instant.parse("2026-09-17T01:00:00Z"); // 목 10:00 KST
            return !from.isAfter(work) && to.isAfter(work);
        });
        assertThatThrownBy(() -> service.addUnavailable(M, new UnavailableRequest("2026-09-17", true, null, null, "none", null)))
                .isInstanceOf(ApiException.class).hasMessage(AvailabilityService.CONFLICT);
        assertThatThrownBy(() -> service.addUnavailable(M, new UnavailableRequest("2026-09-10", true, null, null, "weekly", null))) // 매주 목 — 다음 주가 겹친다
                .isInstanceOf(ApiException.class).hasMessage(AvailabilityService.CONFLICT);
        service.addUnavailable(M, new UnavailableRequest("2026-09-17", false, "14:00", "18:00", "none", null)); // 오후만 — 통과

        ManagerWorkLookup.WorkSlot thu = new ManagerWorkLookup.WorkSlot(THU, LocalTime.of(10, 0), Instant.parse("2026-09-17T04:00:00Z")); // 목 10:00~13:00 KST
        when(bookings.upcomingWork(eq(M), any(), any())).thenReturn(List.of(thu));
        assertThatThrownBy(() -> service.updateHours(M, List.of(new HoursRequest(3, null, null, true)))) // 목요일 휴무
                .isInstanceOf(ApiException.class).hasMessage(AvailabilityService.CONFLICT);
        assertThatThrownBy(() -> service.updateHours(M, List.of(new HoursRequest(3, "11:00", "18:00", false)))) // 10시 작업이 근무 시작 전
                .isInstanceOf(ApiException.class).hasMessage(AvailabilityService.CONFLICT);
        service.updateHours(M, List.of(new HoursRequest(3, "09:00", "18:00", false))); // 통과
        service.updateHours(M, List.of(new HoursRequest(3, "09:00", "12:00", false))); // 10시에 시작하는 작업 — 끝나는 13시가 근무 종료 뒤여도 통과 (매칭과 같은 기준, #16 후속)
        service.updateHours(M, List.of(new HoursRequest(3, "09:00", "10:00", false))); // 근무 종료 시각에 시작 — 매칭이 보내는 범위라 통과
        assertThatThrownBy(() -> service.updateHours(M, List.of(new HoursRequest(3, "09:00", "09:30", false)))) // 근무 종료 뒤 시작 — 밖
                .isInstanceOf(ApiException.class).hasMessage(AvailabilityService.CONFLICT);
    }

    // 51일 뒤 목요일 작업 하나만 있을 때 '매주 목요일 하루 종일' 을 추가하면, 검사 범위(LOOKAHEAD_DAYS)가 그 날까지 닿아 거절된다
    @Test
    void 한_달_넘게_뒤에_맡은_작업도_근무_불가와_겹치면_거절한다() { // 전체 재점검 S1 — 예전에는 28일 뒤까지만 봤다
        Instant work = Instant.parse("2026-11-05T01:00:00Z"); // 51일 뒤 목 10:00 KST (예약 가능 기간 60일 안)
        when(bookings.hasWorkBetween(eq(M), any(), any())).thenAnswer(inv -> !((Instant) inv.getArgument(1)).isAfter(work) && ((Instant) inv.getArgument(2)).isAfter(work));
        assertThatThrownBy(() -> service.addUnavailable(M, new UnavailableRequest("2026-09-17", true, null, null, "weekly", null))) // 매주 목
                .isInstanceOf(ApiException.class).hasMessage(AvailabilityService.CONFLICT);
    }

    // '매월 31일' 규칙을 10/31 부터 추가한다. 12/31 에 작업이 있으면 거절(11월은 31일이 없어 건너뛰고 12/31 을 본다).
    // 작업이 11/30 에만 있으면 통과 — 이 규칙은 11월에 아무 날도 막지 않으므로 겹치지 않는다
    @Test
    void 매월_31일_근무_불가는_31일에_맡은_작업만_보고_31일이_없는_달의_작업은_막지_않는다() { // 자체 점검 #10
        Instant work = Instant.parse("2026-12-31T01:00:00Z"); // 12/31 목 10:00 KST
        when(bookings.hasWorkBetween(eq(M), any(), any())).thenAnswer(inv -> !((Instant) inv.getArgument(1)).isAfter(work) && ((Instant) inv.getArgument(2)).isAfter(work));
        assertThatThrownBy(() -> service.addUnavailable(M, new UnavailableRequest("2026-10-31", true, null, null, "monthly", null))) // 10/31 → 11/30(없음) → 12/31
                .isInstanceOf(ApiException.class).hasMessage(AvailabilityService.CONFLICT); // 예전: 10/31 → 11/30 → 12/30 을 봐서 통과했다

        Instant nov30 = Instant.parse("2026-11-30T01:00:00Z"); // 11월엔 31일이 없어 이 규칙은 아무 날도 막지 않는다
        when(bookings.hasWorkBetween(eq(M), any(), any())).thenAnswer(inv -> !((Instant) inv.getArgument(1)).isAfter(nov30) && ((Instant) inv.getArgument(2)).isAfter(nov30));
        when(unavailable.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service.addUnavailable(M, new UnavailableRequest("2026-10-31", true, null, null, "monthly", null)); // 통과 (예전: 11/30 을 봐서 거절)
    }

    // 오늘(9/15 화) 16:00 에 맡은 작업이 있다. 지난 날짜(9/8 화)로 '반복 없음 · 하루 종일' 을 넣으면 그 날 하루만 막는 규칙이라
    // 오늘 작업과는 겹치지 않는다 → 409 없이 저장된다. 같은 날짜를 '매주' 로 넣으면 오늘도 막으므로 409 그대로다
    @Test
    void 지난_날짜의_1회성_근무_불가는_다른_날의_작업_때문에_거절하지_않는다() { // 문제 목록 12번 — 예전: 한 주 뒤(오늘)를 검사해 409
        Instant work = Instant.parse("2026-09-15T07:00:00Z"); // 오늘(화) 16:00 KST
        when(bookings.hasWorkBetween(eq(M), any(), any())).thenAnswer(inv -> !((Instant) inv.getArgument(1)).isAfter(work) && ((Instant) inv.getArgument(2)).isAfter(work));
        AvailabilityResponse.UnavailableItem saved = service.addUnavailable(M, new UnavailableRequest("2026-09-08", true, null, null, "none", null));
        assertThat(saved.date()).isEqualTo("9월 8일");
        assertThatThrownBy(() -> service.addUnavailable(M, new UnavailableRequest("2026-09-08", true, null, null, "weekly", null))) // 매주 화 — 오늘을 막는다
                .isInstanceOf(ApiException.class).hasMessage(AvailabilityService.CONFLICT);
    }

    // 내 근무 불가는 지우고, 다른 매니저의 것은 NOT_FOUND(존재를 드러내지 않는다)
    @Test
    void 근무_불가_삭제는_내_것만() {
        ManagerUnavailable mine = ManagerUnavailable.create(M, LocalDate.of(2026, 9, 23), true, null, null, "none", null, NOW);
        ManagerUnavailable theirs = ManagerUnavailable.create(UUID.randomUUID(), LocalDate.of(2026, 9, 23), true, null, null, "none", null, NOW);
        when(unavailable.findById(mine.getId())).thenReturn(Optional.of(mine));
        when(unavailable.findById(theirs.getId())).thenReturn(Optional.of(theirs));
        service.removeUnavailable(M, mine.getId());
        verify(unavailable).delete(mine);
        assertThatThrownBy(() -> service.removeUnavailable(M, theirs.getId())).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.NOT_FOUND);
    }

    // holdLive(탈퇴와 줄을 서는 조회)가 UNAUTHENTICATED 를 던지면 두 쓰기 모두 예외로 멈추고 어떤 행도 저장하지 않는다
    @Test
    void 탈퇴가_먼저_커밋됐으면_근무시간_변경과_근무_불가_추가는_401_이고_남기지_않는다() { // 탈퇴 때 지운 뒤 다시 생기지 않게 (자체 점검 후속 검토)
        when(profiles.holdLive(M)).thenThrow(new ApiException(ErrorCode.UNAUTHENTICATED));
        assertThatThrownBy(() -> service.addUnavailable(M, new UnavailableRequest("2026-09-24", true, null, null, "none", "개인 사정"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.updateHours(M, List.of(new HoursRequest(0, "10:00", "17:00", false)))).isInstanceOf(ApiException.class);
        org.mockito.Mockito.verify(unavailable, org.mockito.Mockito.never()).save(any());
        org.mockito.Mockito.verify(hours, org.mockito.Mockito.never()).save(any());
    }
}
