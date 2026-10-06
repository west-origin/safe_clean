package kr.ansimclean.api.profile;

import kr.ansimclean.api.catalog.AppSettingsService;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.time.AppClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 매니저 방문 취소·미방문 정책 (docs/manager-policy.md — 사용자 승인 2026-09-22) */
// ManagerPenaltyService(매니저 패널티 원장·누적 점수·콜 배정 정지·무사고 회복) 단위 테스트. 스프링·DB 없이 Mockito 가짜 저장소로 돌린다.
// 확인하는 것: 만료 기간(서울 달력 12개월), 기록 전 잠금 순서, 월 무료 취소 횟수, 취소 구간 판정,
// 점수 기준별 정지 일수, 무사고 회복, 점수가 0 아래로 내려가지 않는지, 면제 뒤 정지 해제.
// 가짜 저장소는 save 한 행을 saved 목록에 쌓고, 원장 조회(findByManagerIdOrderByCreatedAtDesc)는 그 목록을 돌려준다 —
// 원장을 메모리에서 흉내 낸다. 회복 대상·최근 기록 여부 같은 나머지 조회는 테스트마다 정한다
class ManagerPenaltyServiceTest {
    static final UUID MANAGER = UUID.randomUUID();
    static final Instant NOW = Instant.parse("2026-09-22T05:00:00Z"); // 서울 14:00
    // 시각을 NOW 에 고정한 시계 — 이 시계로 만든 서비스에서는 모든 기록의 생성 시각이 NOW 로 같다
    static final AppClock CLOCK = new AppClock(Clock.fixed(NOW, AppClock.ZONE));
    // app_settings.manager_penalty 에 들어 있을 설정 JSON 을 테스트가 직접 정한다 ("""…""" = 여러 줄 문자열).
    // 점수: 일찍 취소 1·늦은 취소 1·미방문 3·지각 0.5, 일찍 취소는 월 2회까지 0점, 방문 4시간 전부터 앱 취소 불가,
    // 기준: 3점 경고 · 5점 7일 정지 · 8점 30일 정지 · 10점 해지 심사
    static final String RULE = """
            {"freeCancelsPerMonth": 2,
             "points": {"cancel_early": 1, "cancel_late": 1, "no_show": 3, "tardy": 0.5},
             "cancelCutoffHours": 4, "lateCancelHours": 24, "tardyMinutes": 30,
             "expireMonths": 12, "recoveryMonths": 3,
             "thresholds": [{"points":3,"action":"warn","days":0},{"points":5,"action":"suspend","days":7},
                            {"points":8,"action":"suspend","days":30},{"points":10,"action":"review","days":0}]}""";

    // 따로 정하지 않은 가짜 메서드는 기본값(null·false·빈 목록·0)을 돌려준다 — 예: lockManager 는 아무 일도 하지 않고,
    // latestActivePositiveExpiry 는 null 이라 회복 행 만료가 '지금 + 12개월' 이 된다
    ManagerPenaltyRepository penalties = mock(ManagerPenaltyRepository.class);
    ManagerProfileRepository managers = mock(ManagerProfileRepository.class);
    AppSettingsService settings = mock(AppSettingsService.class);
    OutboxService outbox = mock(OutboxService.class);
    ManagerPenaltyService service;
    List<ManagerPenalty> saved = new ArrayList<>(); // 가짜 원장 — save 된 행과 테스트가 직접 넣은 행
    ManagerProfile profile; // 진짜 매니저 프로필 객체 — 정지(suspendedUntil)가 걸렸는지 여기서 바로 본다

    /**
     * 테스트마다 새로 준비한다 (BeforeEach = 각 테스트 전에 JUnit 이 부른다).
     * 설정 조회는 RULE JSON 을, 매니저 조회는 profile 을 돌려주고, save 는 saved 에 쌓고,
     * 원장 조회는 그 순간 saved 의 복사본을 돌려준다 (thenAnswer = 불릴 때마다 람다를 실행해 결과를 만든다)
     */
    @BeforeEach
    void setUp() {
        service = new ManagerPenaltyService(penalties, managers, settings, outbox, CLOCK);
        when(settings.node(ManagerPenaltyService.SETTING)).thenReturn(JsonMapper.builder().build().readTree(RULE));
        profile = ManagerProfile.create(MANAGER, NOW);
        when(managers.findById(MANAGER)).thenReturn(java.util.Optional.of(profile));
        when(penalties.save(any())).thenAnswer(inv -> {
            saved.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(penalties.findByManagerIdOrderByCreatedAtDesc(MANAGER)).thenAnswer(inv -> List.copyOf(saved));
    }

    /** 운영자 면제가 이 행을 찾게 한다: 잠그기 전에는 매니저 id 만(findManagerIdById), 잠근 뒤에는 행 전체(findForUpdate) */
    void waivable(ManagerPenalty p) {
        when(penalties.findManagerIdById(p.getId())).thenReturn(java.util.Optional.of(p.getManagerId()));
        when(penalties.findForUpdate(p.getId())).thenReturn(java.util.Optional.of(p));
    }

    // 미방문 한 건의 만료 시각이 '지금 + 12개월(서울 달력)' 인지, 그리고 months 가 달 끝을 맞게 넘기는지 본다.
    // 1월 31일 + 1개월 = 2월 28일 (30일을 더하는 방식이면 3월로 넘어간다)
    @Test
    void 유효_기간은_서울_달력으로_12개월() { // 자체 점검 #23 — 예전: 360일
        ManagerPenalty p = service.record(MANAGER, UUID.randomUUID(), null, ManagerPenalty.NO_SHOW, null, null);
        assertThat(p.getExpiresAt()).isEqualTo(NOW.atZone(AppClock.ZONE).plusMonths(12).toInstant());
        assertThat(ManagerPenaltyService.months(Instant.parse("2026-01-30T15:00:00Z"), 1)).isEqualTo(Instant.parse("2026-02-27T15:00:00Z")); // 1/31 00:00 KST → 2/28 00:00 KST
    }

    // 같은 매니저의 기록이 동시에 두 건 들어와도 무료 취소 횟수를 겹쳐 세지 않도록, 잠금(lockManager)이 원장 조회보다 먼저여야 한다.
    // InOrder = Mockito 로 '이 순서대로 불렸는지' 확인한다
    @Test
    void 기록은_같은_매니저끼리_줄을_선_뒤_횟수와_점수를_읽는다() { // 자체 점검 #24
        service.record(MANAGER, UUID.randomUUID(), null, ManagerPenalty.CANCEL_EARLY, "personal", null);
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(penalties);
        order.verify(penalties).lockManager(MANAGER);
        order.verify(penalties, org.mockito.Mockito.atLeastOnce()).findByManagerIdOrderByCreatedAtDesc(MANAGER);
    }

    // 같은 달의 일찍 취소 두 번은 0점으로만 적히고, 세 번째부터 설정표 점수(1점)가 붙는다.
    // isEqualByComparingTo = BigDecimal 을 크기로 비교한다 (0 과 0.0 을 같게 본다)
    @Test
    void 방문_24시간_전_취소는_월_2회까지_0점_3회째부터_1점() {
        service.record(MANAGER, UUID.randomUUID(), null, ManagerPenalty.CANCEL_EARLY, "personal", null);
        service.record(MANAGER, UUID.randomUUID(), null, ManagerPenalty.CANCEL_EARLY, "personal", null);
        assertThat(service.points(MANAGER)).isEqualByComparingTo("0");

        service.record(MANAGER, UUID.randomUUID(), null, ManagerPenalty.CANCEL_EARLY, "personal", null);
        assertThat(service.points(MANAGER)).isEqualByComparingTo("1");
    }

    // 방문까지 남은 시간: 24시간 이상 → 일찍 취소, 4~24시간 → 늦은 취소, 4시간 미만·이미 지남 → null(앱 취소 불가)
    @Test
    void 남은_시간으로_구간을_고르고_4시간_안이면_앱에서_취소할_수_없다() {
        Instant start = NOW.plusSeconds(30 * 3600); // 30시간 뒤
        assertThat(service.cancelCategory(start, NOW)).isEqualTo(ManagerPenalty.CANCEL_EARLY);
        assertThat(service.cancelCategory(NOW.plusSeconds(10 * 3600), NOW)).isEqualTo(ManagerPenalty.CANCEL_LATE);
        assertThat(service.cancelCategory(NOW.plusSeconds(3 * 3600), NOW)).isNull(); // 컷오프 안 — 고객센터
        assertThat(service.cancelCategory(NOW.minusSeconds(3600), NOW)).isNull();    // 이미 지난 방문
    }

    // 미방문(3점)을 세 번 쌓으며 기준을 넘을 때마다 정지 종료일이 서울 기준 오늘(9/22) + 7일 → + 30일 로 바뀌는지 본다.
    // 정지 종료일 당일까지 정지다 (ManagerProfile.isSuspended)
    @Test
    void 누적_5점이면_7일_8점이면_30일_콜_배정_정지() {
        service.record(MANAGER, UUID.randomUUID(), null, ManagerPenalty.NO_SHOW, null, null);   // 3
        assertThat(profile.getSuspendedUntil()).isNull();                                       // 경고 단계(3점)는 정지 없음

        service.record(MANAGER, UUID.randomUUID(), null, ManagerPenalty.NO_SHOW, null, null);   // 6 → 5점 넘음
        assertThat(profile.getSuspendedUntil()).isEqualTo(LocalDate.of(2026, 9, 29));           // 7일
        assertThat(profile.isSuspended(LocalDate.of(2026, 9, 25))).isTrue();

        service.record(MANAGER, UUID.randomUUID(), null, ManagerPenalty.NO_SHOW, null, null);   // 9 → 8점 넘음
        assertThat(profile.getSuspendedUntil()).isEqualTo(LocalDate.of(2026, 10, 22));          // 30일
    }

    // 늦은 취소(1점)를 다섯 번 적는다. 매니저 푸시 이벤트(ManagerPenalized)에는 이번 기록으로 넘은 단계가 실린다 —
    // 3번째(3점) = 경고(warn) + 다음 정지 단계 5점·7일, 5번째(5점) = 정지(suspend) + 정지 마지막 날 9/29, 나머지는 단계 없음(null).
    // PushHandler 는 이 값으로 경고·정지 전용 푸시를 고른다 (문제 목록 14번)
    @Test
    void 기준을_넘은_기록의_푸시_이벤트에는_넘은_단계를_싣는다() {
        for (int i = 0; i < 5; i++) service.record(MANAGER, UUID.randomUUID(), null, ManagerPenalty.CANCEL_LATE, "sick", null);
        org.mockito.ArgumentCaptor<Object> captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        org.mockito.Mockito.verify(outbox, org.mockito.Mockito.times(5)).publish(captor.capture());
        List<kr.ansimclean.api.common.events.ManagerPenalized> events = captor.getAllValues().stream().map(kr.ansimclean.api.common.events.ManagerPenalized.class::cast).toList();

        assertThat(events).extracting(kr.ansimclean.api.common.events.ManagerPenalized::stage).containsExactly(null, null, "warn", null, "suspend");
        assertThat(events.get(2)).extracting("suspendedUntil", "nextSuspendPoints", "nextSuspendDays").containsExactly(null, 5, 7);
        assertThat(events.get(4)).extracting("suspendedUntil", "nextSuspendPoints", "nextSuspendDays").containsExactly("2026-09-29", null, null);
        assertThat(events.get(1).stage()).isNull();
    }

    // 한 기록이 여러 단계를 함께 넘는다: 앞선 기록으로 7점(또는 7.5점, 5점 정지는 이미 끝남)인 매니저가 미방문 3점을 받아 10점(10.5점)이 됐다 →
    // 8점(30일 정지)과 10점(해지 심사)을 함께 넘었다. 가장 긴 정지(30일, 10/22 까지)를 걸고 푸시 단계는 정지(suspend) — 예전에는 가장 높은 단계(review)
    // 하나만 골라 정지도 정지 전용 푸시도 없이 일반 알림만 갔다 (합친 38건 검토 R4). 해지 심사 로그도 남는다(여기서는 로그를 확인하지 않는다)
    @Test
    void 한_기록이_정지와_해지_심사를_함께_넘으면_가장_긴_정지를_걸고_정지_푸시를_보낸다() {
        for (String before : List.of("7", "7.5")) {
            saved.clear();
            profile = ManagerProfile.create(MANAGER, NOW);
            when(managers.findById(MANAGER)).thenReturn(java.util.Optional.of(profile));
            org.mockito.Mockito.clearInvocations(outbox);
            saved.add(ManagerPenalty.of(MANAGER, UUID.randomUUID(), null, ManagerPenalty.NO_SHOW, new BigDecimal(before), null, null,
                    NOW.minus(java.time.Duration.ofDays(40)), NOW.plus(java.time.Duration.ofDays(300)))); // 앞선 기록들의 합 (한 줄로 줄였다)

            service.record(MANAGER, UUID.randomUUID(), null, ManagerPenalty.NO_SHOW, null, null); // +3 → 10 / 10.5

            assertThat(profile.getSuspendedUntil()).as("앞선 점수 %s", before).isEqualTo(LocalDate.of(2026, 10, 22)); // 오늘 9/22 + 30일
            org.mockito.ArgumentCaptor<kr.ansimclean.api.common.events.ManagerPenalized> event = org.mockito.ArgumentCaptor.forClass(kr.ansimclean.api.common.events.ManagerPenalized.class);
            org.mockito.Mockito.verify(outbox).publish(event.capture());
            assertThat(event.getValue()).extracting("stage", "suspendedUntil").containsExactly("suspend", "2026-10-22");
        }
    }

    // 한 기록이 경고와 정지를 함께 넘으면(2점 → 미방문 3점 → 5점) 정지가 걸리고 푸시 단계는 정지다 — 정지 > 경고
    @Test
    void 경고와_정지를_함께_넘으면_정지_푸시가_간다() {
        saved.add(ManagerPenalty.of(MANAGER, UUID.randomUUID(), null, ManagerPenalty.CANCEL_LATE, new BigDecimal("2"), null, null,
                NOW.minus(java.time.Duration.ofDays(10)), NOW.plus(java.time.Duration.ofDays(300))));
        service.record(MANAGER, UUID.randomUUID(), null, ManagerPenalty.NO_SHOW, null, null); // 5
        assertThat(profile.getSuspendedUntil()).isEqualTo(LocalDate.of(2026, 9, 29));
        org.mockito.ArgumentCaptor<kr.ansimclean.api.common.events.ManagerPenalized> event = org.mockito.ArgumentCaptor.forClass(kr.ansimclean.api.common.events.ManagerPenalized.class);
        org.mockito.Mockito.verify(outbox).publish(event.capture());
        assertThat(event.getValue()).extracting("stage", "suspendedUntil", "nextSuspendPoints").containsExactly("suspend", "2026-09-29", null);
    }

    // 1단계: 최근 3개월 안에 기록이 있으면(exists… = true) 회복하지 않는다 → 0건.
    // 2단계: 3점을 쌓고 최근 기록이 없다고 바꾸면 회복 행(-1점) 한 줄이 적혀 누적이 2점이 된다.
    // recover() 는 매일 정리 스케줄러(ManagerPenaltyCleaner)가 부르는 메서드이고 결과는 회복한 매니저 수다.
    // 1단계의 0건은 '최근 기록' 때문이다 — 이름의 '점수가 없으면' 갈래(남은 점수 0 이하)는 이 테스트가 따로 확인하지 않는다
    @Test
    void 무사고_3개월이면_1점_회복하고_점수가_없으면_아무것도_하지_않는다() {
        when(penalties.managersWithActivePoints(any())).thenReturn(List.of(MANAGER));
        when(penalties.existsByManagerIdAndCreatedAtAfter(eq(MANAGER), any())).thenReturn(true); // 최근에 일이 있었다
        assertThat(service.recover()).isZero();

        service.record(MANAGER, UUID.randomUUID(), null, ManagerPenalty.NO_SHOW, null, null); // 3점
        when(penalties.existsByManagerIdAndCreatedAtAfter(eq(MANAGER), any())).thenReturn(false);
        assertThat(service.recover()).isEqualTo(1);
        assertThat(saved.get(saved.size() - 1).getCategory()).isEqualTo(ManagerPenalty.RECOVERY);
        assertThat(service.points(MANAGER)).isEqualByComparingTo("2"); // 3 − 1
    }

    // 벌점 없이 회복 행(-1점)만 원장에 직접 넣어도 누적 점수는 -1 이 아니라 0 이다
    @Test
    void 점수는_0_아래로_내려가지_않는다() {
        saved.add(ManagerPenalty.of(MANAGER, null, null, ManagerPenalty.RECOVERY, BigDecimal.ONE.negate(), null, null, NOW, NOW.plusSeconds(1)));
        assertThat(service.points(MANAGER)).isEqualByComparingTo("0");
    }

    // 지각 0.5점만 남은 상태에서 회복하면 회복 행은 -1 이 아니라 -0.5 로 적힌다.
    // 그 뒤 3 + 1 + 1 을 쌓으면 누적이 정확히 5점이 되어 정지가 걸려야 한다 (남은 -0.5 가 다음 벌점을 깎지 않는다)
    @Test
    void 남은_점수보다_큰_회복은_남은_만큼만_깎고_적립되지_않아_다음_벌점이_그대로_쌓인다() { // 자체 점검 #9
        when(penalties.managersWithActivePoints(any())).thenReturn(List.of(MANAGER));
        when(penalties.existsByManagerIdAndCreatedAtAfter(eq(MANAGER), any())).thenReturn(false);
        service.record(MANAGER, UUID.randomUUID(), null, ManagerPenalty.TARDY, null, null); // 0.5
        assertThat(service.recover()).isEqualTo(1);
        assertThat(saved.get(saved.size() - 1).getPoints()).isEqualByComparingTo("-0.5");
        assertThat(service.points(MANAGER)).isEqualByComparingTo("0");

        service.record(MANAGER, UUID.randomUUID(), null, ManagerPenalty.NO_SHOW, null, null);    // 3
        service.record(MANAGER, UUID.randomUUID(), null, ManagerPenalty.CANCEL_LATE, null, null); // 4
        service.record(MANAGER, UUID.randomUUID(), null, ManagerPenalty.CANCEL_LATE, null, null); // 5 → 정지 (예전: 4.5 라 정지 없음)
        assertThat(service.points(MANAGER)).isEqualByComparingTo("5");
        assertThat(profile.getSuspendedUntil()).isNotNull();
    }

    // 준비: 미방문 3점 → 그 뒤 회복 -1 → 운영자가 미방문을 면제(waive). 남은 것은 회복 -1 뿐이라 누적 0.
    // 확인: 새 미방문 3점을 넣으면 누적이 3점이어야 한다. 행을 생긴 순서대로 더하며 매번 0 에서 막기 때문이다
    @Test
    void 면제한_벌점을_상쇄하던_회복은_다음_벌점을_깎지_않는다() { // 자체 점검 #9 (면제 경로)
        ManagerPenalty noShow = service.record(MANAGER, UUID.randomUUID(), null, ManagerPenalty.NO_SHOW, null, null); // 3
        saved.add(ManagerPenalty.of(MANAGER, null, null, ManagerPenalty.RECOVERY, BigDecimal.ONE.negate(), null, null, NOW.plusSeconds(1), NOW.plus(java.time.Duration.ofDays(300))));
        waivable(noShow);
        service.waive(noShow.getId(), "진단서 확인");
        assertThat(service.points(MANAGER)).isEqualByComparingTo("0");
        ManagerPenalty next = ManagerPenalty.of(MANAGER, UUID.randomUUID(), null, ManagerPenalty.NO_SHOW, new BigDecimal("3"), null, null, NOW.plusSeconds(2), NOW.plus(java.time.Duration.ofDays(300)));
        saved.add(next);
        assertThat(service.points(MANAGER)).isEqualByComparingTo("3"); // 예전: 2 (남은 -1 이 깎았다)
    }

    // 시각을 옮겨야 해서 MutableClock(테스트용 옮길 수 있는 시계)으로 서비스를 따로 만든다.
    // 6점으로 9/29 까지 정지된 뒤, 5일 뒤(9/27)에 0점 무료 취소·0.5점 지각을 적어도 새 기준(8점)을 넘지 않았으니
    // 정지 종료일이 그대로여야 한다. 누적이 이미 5점 이상이라는 이유만으로 정지를 다시 걸면 종료일이 늘어난다
    @Test
    void 정지는_문턱을_넘을_때만_걸린다_이미_넘은_뒤의_0점_기록이_정지를_늘리지_않는다() { // 전체 재점검 P1
        kr.ansimclean.api.MutableClock mutable = new kr.ansimclean.api.MutableClock(NOW, AppClock.ZONE);
        ManagerPenaltyService svc = new ManagerPenaltyService(penalties, managers, settings, outbox, new AppClock(mutable));
        svc.record(MANAGER, UUID.randomUUID(), null, ManagerPenalty.NO_SHOW, null, null);   // 3
        svc.record(MANAGER, UUID.randomUUID(), null, ManagerPenalty.NO_SHOW, null, null);   // 6 → 5 넘음: 9/29 까지
        assertThat(profile.getSuspendedUntil()).isEqualTo(LocalDate.of(2026, 9, 29));

        mutable.set(NOW.plus(java.time.Duration.ofDays(5)));                               // 9/27
        svc.record(MANAGER, UUID.randomUUID(), null, ManagerPenalty.CANCEL_EARLY, "personal", null); // 무료 취소 0점
        svc.record(MANAGER, UUID.randomUUID(), null, ManagerPenalty.TARDY, null, null);     // 6.5 — 문턱(8)을 넘지 않았다
        assertThat(profile.getSuspendedUntil()).isEqualTo(LocalDate.of(2026, 9, 29));       // 예전에는 10/4 로 다시 늘었다
    }

    // 준비: 200일 전에 생겨 160일 뒤 만료되는 미방문 3점. 남은 양수 벌점 중 가장 늦은 만료(latestActivePositiveExpiry)도 그 시각으로 둔다.
    // 확인: 새 회복 행의 만료가 '지금 + 12개월' 이 아니라 그 벌점의 만료와 같다
    @Test
    void 회복_행은_상쇄할_벌점보다_오래_남지_않는다() { // 전체 재점검 P2
        ManagerPenalty noShow = ManagerPenalty.of(MANAGER, UUID.randomUUID(), null, ManagerPenalty.NO_SHOW, new BigDecimal("3"), null, null,
                NOW.minus(java.time.Duration.ofDays(200)), NOW.plus(java.time.Duration.ofDays(160)));
        saved.add(noShow);
        when(penalties.managersWithActivePoints(any())).thenReturn(List.of(MANAGER));
        when(penalties.existsByManagerIdAndCreatedAtAfter(eq(MANAGER), any())).thenReturn(false);
        when(penalties.latestActivePositiveExpiry(eq(MANAGER), any())).thenReturn(noShow.getExpiresAt());
        assertThat(service.recover()).isEqualTo(1);
        ManagerPenalty recovery = saved.get(saved.size() - 1);
        assertThat(recovery.getExpiresAt()).isEqualTo(noShow.getExpiresAt()); // 예전: 지금+360일 — 벌점이 먼저 만료되면 남은 -1 이 다음 벌점을 깎았다
    }

    // 미방문 두 번(6점)으로 정지된 뒤 두 번째를 면제하면 누적 3점 — 가장 낮은 정지 기준(5점) 아래라 정지가 풀린다
    @Test
    void 면제로_점수가_정지_기준_아래가_되면_정지도_풀린다() { // 전체 재점검 P3
        service.record(MANAGER, UUID.randomUUID(), null, ManagerPenalty.NO_SHOW, null, null);                    // 3
        ManagerPenalty second = service.record(MANAGER, UUID.randomUUID(), null, ManagerPenalty.NO_SHOW, null, null); // 6 → 정지
        assertThat(profile.isSuspended(LocalDate.of(2026, 9, 25))).isTrue();
        waivable(second);
        service.waive(second.getId(), "진단서 확인");
        assertThat(profile.isSuspended(LocalDate.of(2026, 9, 25))).isFalse(); // 3점 — 5점 기준 아래
    }

    // 운영자 면제 API(POST /admin/penalties/{id}/waive)가 받는 응답: 면제 뒤 누적 점수 글자와 지금 걸린 정지의 종료일(없으면 null).
    // 6점으로 정지 → 두 번째 미방문 면제 → { points "3", suspendedUntil null }. 같은 기록을 다시 면제해도 바꾸지 않고 같은 응답이다
    @Test
    void 운영자_면제는_누적_점수와_정지_상태를_돌려주고_다시_불러도_같다() { // 문제 목록 3번
        service.record(MANAGER, UUID.randomUUID(), null, ManagerPenalty.NO_SHOW, null, null);                          // 3
        ManagerPenalty second = service.record(MANAGER, UUID.randomUUID(), null, ManagerPenalty.NO_SHOW, null, null);  // 6 → 9/29 까지 정지
        waivable(second);

        kr.ansimclean.api.profile.dto.PenaltyWaiveResponse res = service.waive(second.getId(), "진단서 확인");
        assertThat(res).extracting("penaltyId", "managerId", "points", "suspendedUntil").containsExactly(second.getId(), MANAGER, "3", null);
        assertThat(second.getStatus()).isEqualTo(ManagerPenalty.WAIVED);

        assertThat(service.waive(second.getId(), "다른 메모")).isEqualTo(res); // 이미 면제 — 그대로
    }

    // 면제해도 누적 점수가 가장 낮은 정지 기준(5점) 이상이면 정지는 그대로다 — 응답에 정지 종료일이 보인다.
    // 9점으로 10/22 까지(30일) 정지 → 세 번째 미방문 면제 → 6점, 정지 종료일 그대로 (30일이 7일로 줄지는 않는다 — 문제 목록 3번의 별도 결정 거리)
    @Test
    void 면제_뒤에도_정지_기준_이상이면_정지_종료일을_돌려준다() {
        service.record(MANAGER, UUID.randomUUID(), null, ManagerPenalty.NO_SHOW, null, null);
        service.record(MANAGER, UUID.randomUUID(), null, ManagerPenalty.NO_SHOW, null, null);
        ManagerPenalty third = service.record(MANAGER, UUID.randomUUID(), null, ManagerPenalty.NO_SHOW, null, null); // 9 → 10/22 까지
        waivable(third);

        assertThat(service.waive(third.getId(), "진단서 확인")).extracting("points", "suspendedUntil").containsExactly("6", "2026-10-22");
        assertThat(profile.getSuspendedUntil()).isEqualTo(LocalDate.of(2026, 10, 22));
    }

    // 회복 기록(음수)을 면제하면 점수가 오히려 늘고, 기간이 끝난 기록은 이미 점수에 없다 — 둘 다 409 이고 상태를 바꾸지 않는다. 없는 id 는 404
    @Test
    void 회복_기록과_기간이_끝난_기록은_면제할_수_없다() {
        ManagerPenalty recovery = ManagerPenalty.of(MANAGER, null, null, ManagerPenalty.RECOVERY, BigDecimal.ONE.negate(), null, null, NOW, NOW.plusSeconds(60));
        ManagerPenalty expired = ManagerPenalty.of(MANAGER, UUID.randomUUID(), null, ManagerPenalty.NO_SHOW, new BigDecimal("3"), null, null, NOW, NOW.plusSeconds(60));
        expired.expire();
        waivable(recovery);
        waivable(expired);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.waive(recovery.getId(), "메모"))
                .isInstanceOf(kr.ansimclean.api.common.error.ApiException.class).extracting("code").isEqualTo(kr.ansimclean.api.common.error.ErrorCode.INVALID_STATE);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.waive(expired.getId(), "메모"))
                .isInstanceOf(kr.ansimclean.api.common.error.ApiException.class).extracting("code").isEqualTo(kr.ansimclean.api.common.error.ErrorCode.INVALID_STATE);
        assertThat(recovery.getStatus()).isEqualTo(ManagerPenalty.ACTIVE);
        assertThat(expired.getStatus()).isEqualTo(ManagerPenalty.EXPIRED);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.waive(UUID.randomUUID(), "메모"))
                .isInstanceOf(kr.ansimclean.api.common.error.ApiException.class).extracting("code").isEqualTo(kr.ansimclean.api.common.error.ErrorCode.NOT_FOUND);
    }

    // 정기청소 한 회차를 늦게 취소(1점)한 뒤 운영자가 면제해도 그 시리즈의 다음 회차 우선 제안에서는 계속 빠진다 —
    // 정책 '한 회차라도 취소하면 그 고객의 다음 회차 우선 제안에서 빠집니다' 에는 면제 조건이 없고 면제는 점수에만 영향을 준다.
    // 같은 예약 후보 제외(bookingDroppers)도 상태를 보지 않는다. 가짜 저장소의 exists 조회는 원장 목록(saved)을 조건대로 훑어 답한다
    @Test
    void 면제된_취소도_그_시리즈의_다음_회차_우선_제안에서_뺀다() { // 문제 목록 27번 (사용자 결정 A)
        UUID series = UUID.randomUUID();
        when(penalties.existsByManagerIdAndSeriesIdAndCategoryIn(eq(MANAGER), any(), any())).thenAnswer(inv -> saved.stream().anyMatch(p ->
                p.getSeriesId() != null && p.getSeriesId().equals(inv.getArgument(1)) && ((java.util.Collection<?>) inv.getArgument(2)).contains(p.getCategory())));
        ManagerPenalty cancel = service.record(MANAGER, UUID.randomUUID(), series, ManagerPenalty.CANCEL_LATE, "sick", null);
        waivable(cancel);
        service.waive(cancel.getId(), "진단서 확인");

        assertThat(service.droppedSeries(MANAGER, series)).isTrue();                // 예전: 면제되면 false 라 다음 회차 우선 콜이 다시 갔다
        assertThat(service.droppedSeries(MANAGER, UUID.randomUUID())).isFalse();    // 다른 시리즈는 상관없다
        assertThat(service.droppedSeries(MANAGER, null)).isFalse();                 // 단건 예약
    }

    // 같은 면제 요청이 두 번 거의 동시에 왔다(runbook: 다시 보내도 안전). 두 번째 요청이 잠그기 전에 읽었다면 그 사본은 아직 '적용 중' 이고(stale),
    // 첫 요청이 커밋한 뒤 DB 의 행은 이미 면제다(fresh). 면제는 매니저를 잠근 뒤 행을 다시 읽어(findForUpdate) 그 상태로 판단하므로 두 번째 요청은
    // 아무것도 바꾸지 않는다 — 예전에는 잠그기 전 사본으로 판단해 메모·시각을 두 번째 값으로 덮어썼다. 그사이 탈퇴가 행을 지웠으면 404 (합친 38건 검토 R5)
    @Test
    void 면제는_매니저를_잠근_뒤_다시_읽은_행으로_판단한다() {
        ManagerPenalty stale = ManagerPenalty.of(MANAGER, UUID.randomUUID(), null, ManagerPenalty.NO_SHOW, new BigDecimal("3"), null, null, NOW, NOW.plusSeconds(3600));
        ManagerPenalty fresh = ManagerPenalty.of(MANAGER, stale.getBookingId(), null, ManagerPenalty.NO_SHOW, new BigDecimal("3"), null, null, NOW, NOW.plusSeconds(3600));
        fresh.waive("진단서 확인", NOW); // 첫 요청이 커밋한 상태
        when(penalties.findById(stale.getId())).thenReturn(java.util.Optional.of(stale)); // 잠그기 전에 읽으면 받는 옛 사본
        when(penalties.findManagerIdById(stale.getId())).thenReturn(java.util.Optional.of(MANAGER));
        when(penalties.findForUpdate(stale.getId())).thenReturn(java.util.Optional.of(fresh));

        service.waive(stale.getId(), "다른 메모");

        assertThat(stale.getStatus()).isEqualTo(ManagerPenalty.ACTIVE); // 옛 사본으로 면제를 다시 적지 않았다
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(penalties);
        order.verify(penalties).lockManager(MANAGER);
        order.verify(penalties).findForUpdate(stale.getId());

        when(penalties.findForUpdate(stale.getId())).thenReturn(java.util.Optional.empty()); // 그사이 탈퇴(forget)가 지웠다
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.waive(stale.getId(), "메모"))
                .isInstanceOf(kr.ansimclean.api.common.error.ApiException.class).extracting("code").isEqualTo(kr.ansimclean.api.common.error.ErrorCode.NOT_FOUND);
    }

    // 면제도 기록(record)처럼 같은 매니저의 잠금을 잡은 뒤 원장을 읽는다 — 동시에 들어온 새 벌점이 건 정지를 옛 합계로 풀어 버리지 않게
    @Test
    void 면제는_같은_매니저의_기록과_줄을_선_뒤_점수를_읽는다() {
        ManagerPenalty noShow = ManagerPenalty.of(MANAGER, UUID.randomUUID(), null, ManagerPenalty.NO_SHOW, new BigDecimal("3"), null, null, NOW, NOW.plusSeconds(3600));
        saved.add(noShow);
        waivable(noShow);
        service.waive(noShow.getId(), "진단서 확인");
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(penalties);
        order.verify(penalties).lockManager(MANAGER);
        order.verify(penalties, org.mockito.Mockito.atLeastOnce()).findByManagerIdOrderByCreatedAtDesc(MANAGER);
    }
}
