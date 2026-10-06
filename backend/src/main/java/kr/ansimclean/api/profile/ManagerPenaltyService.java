package kr.ansimclean.api.profile;

import kr.ansimclean.api.catalog.AppSettingsService;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.events.ManagerPenalized;
import kr.ansimclean.api.common.format.KoreanFormat;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.profile.dto.PenaltyResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 매니저 방문 취소·미방문 패널티 (docs/manager-policy.md — 사용자 승인 2026-09-22).
 * 숫자는 전부 app_settings.manager_penalty 에서 온다(코드에 상수 없음). 원장은 append-only, 누적 점수는 active 행의 합.
 * 위 줄의 예외: 회복 한 번의 1점(recover)과 내역 제목의 '24시간'(label)은 코드에 고정돼 있다.
 * 원장 행은 면제(waive)·만료(expireOld) 때 상태가 바뀌고, 매니저 탈퇴 때는 모두 지운다(forget).
 * 합은 만료 시각이 안 지난 active 행을 오래된 순으로 더하되 매번 0 아래로 내려가지 않게 한다(points).
 * 쓰는 곳 예: JobService(방문 취소·지각 기록), OfferService(취소한 매니저를 다시 배정하지 않기).
 * 흐름: record 가 원장에 한 줄 적는다 → 이번 기록으로 기준(thresholds)을 넘으면 콜 배정 정지 → 매니저 푸시는 아웃박스(ManagerPenalized, 넘은 단계 포함)로.
 * 점수는 BigDecimal(소수를 오차 없이 계산하는 자바 숫자)이다 — 지각 0.5점 같은 소수 점수가 있다.
 * record 는 부르는 쪽(JobService·StaleBookingCleaner)의 묶음에 합쳐진다 (CODE_GUIDE.md 3-3) —
 * 여기서 예외가 나면 그쪽의 방문 취소·미방문 처리도 함께 되돌려지고, lockManager 잠금도 그 묶음이 끝날 때 풀린다.
 */
@Service
@Transactional
public class ManagerPenaltyService {
    public static final String SETTING = "manager_penalty"; // app_settings 키: 점수표·무료 취소 횟수·기준·기간 (V022 에서 넣은 값)
    public static final String SETTING_REASONS = "manager_cancel_reasons"; // app_settings 키: 방문 취소 사유 목록 [{key, label}]
    private static final Logger log = LoggerFactory.getLogger(ManagerPenaltyService.class);

    private final ManagerPenaltyRepository penalties;
    private final ManagerProfileRepository managers;
    private final AppSettingsService settings;
    private final OutboxService outbox;
    private final AppClock clock;

    public ManagerPenaltyService(ManagerPenaltyRepository penalties, ManagerProfileRepository managers, AppSettingsService settings,
                                 OutboxService outbox, AppClock clock) {
        this.penalties = penalties;
        this.managers = managers;
        this.settings = settings;
        this.outbox = outbox;
        this.clock = clock;
    }

    // ── 규칙 ──

    /** 방문 시작까지 남은 시간으로 취소 구간을 고른다. 컷오프(기본 4시간) 안이면 앱에서 취소할 수 없다 */
    public String cancelCategory(Instant serviceStart, Instant now) {
        // 결과: cancel_early / cancel_late, 앱에서 취소할 수 없으면 null. JobService 는 null 이면 cutoffException 을 던진다
        // HOURS.between 은 남은 시간을 시간 단위로 버린다 — 4시간 30분 남았으면 4
        long hours = ChronoUnit.HOURS.between(now, serviceStart); // 이미 지났으면 음수
        // JsonNode = JSON 을 트리로 읽은 값. path(키) 는 키가 없어도 null 대신 빈 노드를 주고, 빈 노드의 asInt() 는 0 이다.
        // 그래서 설정에서 키가 빠지면 오류 없이 기준이 0 이 된다 (예: cancelCutoffHours 가 없으면 컷오프가 사라진다)
        JsonNode rule = settings.node(SETTING);
        if (hours < rule.path("cancelCutoffHours").asInt()) return null; // 앱 취소 불가 — 고객센터
        return hours >= rule.path("lateCancelHours").asInt() ? ManagerPenalty.CANCEL_EARLY : ManagerPenalty.CANCEL_LATE; // lateCancelHours(기본 24) 이상 남았으면 일찍 취소
    }

    /** 앱 취소 컷오프(시간, 설정 기본값 4). cutoffException 문구에 넣는다 */
    public int cancelCutoffHours() {
        return settings.node(SETTING).path("cancelCutoffHours").asInt();
    }

    /** 지각 기준(분, 설정 기본값 30). JobService.recordTardy(지각 패널티)와 LateStartNotifier(늦은 시작 알림)가 읽는다 */
    public int tardyMinutes() {
        return settings.node(SETTING).path("tardyMinutes").asInt();
    }

    /** 취소 사유 목록 (앱 시트) */
    @Transactional(readOnly = true)
    public JsonNode reasons() {
        // [{key, label}] 배열 그대로. GET /manager/cancel-reasons 가 앱에 주고, JobService 는 보낸 사유 key 가 여기 있는지 검사한다
        return settings.node(SETTING_REASONS);
    }

    // ── 기록 ──

    /**
     * 패널티 한 건. 점수 0이면(무료 취소 한도 안) 원장에만 0점으로 남긴다 — 한 달 몇 번 썼는지 세려면 이력이 필요하다.
     * 임계치를 넘으면 정지까지 여기서 처리한다.
     * 부르는 곳: JobService(방문 취소·지각), StaleBookingCleaner(미방문). 부작용: 원장 저장, 기준을 넘으면 매니저 정지,
     * 아웃박스에 ManagerPenalized 기록(이번에 넘은 단계 포함) → 커밋 뒤 PushHandler 가 매니저에게 푸시 하나를 보낸다 —
     * 경고 단계를 넘었으면 경고 전용, 정지 단계를 넘었으면 정지 전용(둘 다 알림 설정과 무관), 그 밖에는 0점이 아닐 때만 일반 알림.
     * 한 기록이 여러 단계를 넘으면 넘은 단계의 조치를 모두 하고(가장 긴 정지 + 해지 심사 로그), 푸시는 정지 > 경고 순으로 하나 (합친 38건 검토 R4).
     */
    public ManagerPenalty record(UUID managerId, UUID bookingId, UUID seriesId, String category, String reason, String detail) {
        // lockManager = Postgres 권고 잠금(pg_advisory_xact_lock). 바깥 묶음(부르는 쪽 트랜잭션)이 끝나면 저절로 풀린다
        penalties.lockManager(managerId); // 이 매니저의 앞 기록이 커밋된 뒤에 횟수·점수를 읽는다 (자체 점검 #24)
        Instant now = clock.now();
        JsonNode rule = settings.node(SETTING);
        BigDecimal points = pointsFor(managerId, category, rule, now);
        // 기록 전·후 누적 점수를 둘 다 구해 '이번 기록으로 넘은 기준' 만 적용한다 (applyThresholds)
        BigDecimal before = points(managerId);
        ManagerPenalty p = penalties.save(ManagerPenalty.of(managerId, bookingId, seriesId, category, points, reason, detail,
                now, months(now, rule.path("expireMonths").asInt())));
        // JPA 는 조회 쿼리 전에 저장 대기 중인 행을 DB 에 먼저 반영하므로 방금 저장한 행까지 합친 값이다
        BigDecimal total = points(managerId);
        List<JsonNode> crossed = crossedStages(before, total, rule);
        LocalDate suspendedUntil = applyThresholds(managerId, crossed, total, now);
        // 푸시가 고를 단계 하나: 정지 > 경고 > 그 밖(해지 심사). warn 이면 그 위의 첫 정지 단계(점수·일수)를 함께 실어 '5점이 되면 7일 동안 …' 을 설정값으로 쓰게 한다
        JsonNode pushed = pushStage(crossed);
        String stage = pushed == null ? null : pushed.path("action").asString();
        JsonNode nextSuspend = "warn".equals(stage) ? nextSuspendAbove(pushed.path("points").decimalValue(), rule) : null;
        // 점수는 소수라 글자로 싣는다 (예: "0.5"). 푸시는 커밋 뒤 아웃박스가 보낸다
        outbox.publish(new ManagerPenalized(managerId, bookingId, category, points.toPlainString(), total.toPlainString(),
                stage, suspendedUntil == null ? null : suspendedUntil.toString(),
                nextSuspend == null ? null : nextSuspend.path("points").asInt(), nextSuspend == null ? null : nextSuspend.path("days").asInt()));
        log.info("매니저 패널티 {} {} {}점 (누적 {}점)", managerId, category, points.toPlainString(), total.toPlainString());
        return p;
    }

    /** 서울 달력으로 N개월 뒤(음수면 앞) — '개월×30일' 이면 12개월이 360일이라 벌점이 닷새쯤 먼저 사라지고 회복도 빨랐다 (자체 점검 #23) */
    static Instant months(Instant t, int months) {
        return t.atZone(AppClock.ZONE).plusMonths(months).toInstant();
    }

    /** 무료 취소(월 freeCancelsPerMonth 회)는 0점. 그 밖에는 설정표의 점수 */
    private BigDecimal pointsFor(UUID managerId, String category, JsonNode rule, Instant now) {
        BigDecimal base = rule.path("points").path(category).decimalValue();
        if (!ManagerPenalty.CANCEL_EARLY.equals(category)) return base; // 늦은 취소·미방문·지각은 무료 한도가 없다
        // 이번 달(서울 달력의 같은 해·같은 달)에 이미 적힌 일찍 취소 횟수. 면제된 행은 빼고, 0점으로 적힌 무료 취소도 센다
        long thisMonth = penalties.findByManagerIdOrderByCreatedAtDesc(managerId).stream()
                .filter(p -> ManagerPenalty.CANCEL_EARLY.equals(p.getCategory()) && !ManagerPenalty.WAIVED.equals(p.getStatus()))
                .filter(p -> AppClock.toSeoul(p.getCreatedAt()).getMonth() == AppClock.toSeoul(now).getMonth()
                        && AppClock.toSeoul(p.getCreatedAt()).getYear() == AppClock.toSeoul(now).getYear())
                .count();
        return thisMonth < rule.path("freeCancelsPerMonth").asInt() ? BigDecimal.ZERO : base;
    }

    /**
     * 임계치: 경고(알림) · 정지(N일) · 해지 심사(운영자 로그). **이번 기록으로 넘은** 단계들을 점수 오름차순으로 돌려준다 —
     * 이미 넘은 뒤의 기록(0점 무료 취소·0.5점 지각)이 오늘부터 정지를 다시 거는 일이 없게 (전체 재점검 P1). 넘은 단계가 없으면 빈 목록.
     * 한 기록이 여러 단계를 함께 넘을 수 있다 — 예: 7점에서 미방문 3점으로 10점이면 8점(30일 정지)과 10점(해지 심사)을 함께 넘는다.
     * 예전에는 가장 높은 단계 하나만 골라, 해지 심사를 함께 넘은 기록이 정지를 건너뛰었다 (합친 38건 검토 R4)
     */
    private static List<JsonNode> crossedStages(BigDecimal before, BigDecimal total, JsonNode rule) {
        List<JsonNode> crossed = new ArrayList<>();
        // 설정(V022)의 thresholds 가 점수 오름차순이라 결과도 오름차순이다
        for (JsonNode t : rule.path("thresholds")) {
            BigDecimal at = t.path("points").decimalValue();
            if (before.compareTo(at) < 0 && total.compareTo(at) >= 0) crossed.add(t); // 기록 전에는 기준 아래, 지금은 기준 이상 = 이번에 넘었다
        }
        return crossed;
    }

    /**
     * 넘은 단계들의 조치를 모두 한다. 넘은 정지(suspend) 단계 중 가장 긴 정지를 걸고 정지 마지막 날을 돌려준다
     * (이미 더 긴 정지가 걸려 있으면 그 날 — ManagerProfile.suspendUntil). 해지 심사(review)는 운영자 로그를 남긴다.
     * warn 은 여기서 하는 일이 없다 — 경고 알림은 record 가 이벤트에 단계를 실어 PushHandler 가 보낸다. 정지 단계를 넘지 않았으면 null
     */
    private LocalDate applyThresholds(UUID managerId, List<JsonNode> crossed, BigDecimal total, Instant now) {
        JsonNode longest = null; // 넘은 정지 단계 중 정지 일수가 가장 긴 것
        for (JsonNode t : crossed) {
            String action = t.path("action").asString();
            if ("suspend".equals(action) && (longest == null || t.path("days").asInt() > longest.path("days").asInt())) longest = t;
            if ("review".equals(action)) {
                // 해지 심사는 자동으로 하는 일 없이 error 로그만 남긴다 — 운영자가 로그를 보고 판단한다
                log.error("매니저 계약 해지 심사 대상 — 운영자 확인 필요: {} ({}점)", managerId, total.toPlainString());
            }
        }
        if (longest == null) return null;
        LocalDate until = clock.today().plusDays(longest.path("days").asInt()); // 서울 기준 오늘 + days. 이 날까지(포함) 콜을 받지 않는다
        ManagerProfile m = managers.findById(managerId).orElse(null);
        if (m != null) m.suspendUntil(until, now);
        log.warn("매니저 콜 배정 정지 {} — {}점, {}까지", managerId, total.toPlainString(), until);
        return m != null && m.getSuspendedUntil() != null ? m.getSuspendedUntil() : until;
    }

    /**
     * 넘은 단계 중 푸시가 고를 하나: 정지 > 경고 > 그 밖(해지 심사 등 — PushHandler 는 일반 알림). 같은 종류가 여럿이면 가장 높은 것. 넘은 단계가 없으면 null.
     * 정지와 해지 심사를 함께 넘어도 정지 전용 푸시가 간다 (문제 목록 14번, 합친 38건 검토 R4)
     */
    private static JsonNode pushStage(List<JsonNode> crossed) {
        for (String action : List.of("suspend", "warn")) {
            JsonNode found = null;
            for (JsonNode t : crossed) {
                if (action.equals(t.path("action").asString())) found = t; // 오름차순이라 마지막으로 맞은 것이 가장 높다
            }
            if (found != null) return found;
        }
        return crossed.isEmpty() ? null : crossed.get(crossed.size() - 1);
    }

    /** 경고 단계(points) 위의 첫 정지 단계 — 경고 알림의 '5점이 되면 7일 동안 …' 에 쓴다. 없으면 null */
    private static JsonNode nextSuspendAbove(BigDecimal points, JsonNode rule) {
        for (JsonNode t : rule.path("thresholds")) {
            if ("suspend".equals(t.path("action").asString()) && t.path("points").decimalValue().compareTo(points) > 0) return t; // 오름차순이라 처음 맞는 것이 가장 가깝다
        }
        return null;
    }

    // ── 조회 ──

    /**
     * 지금 유효한 누적 점수. 생긴 순서대로 더하되 **매번** 0 아래로 내려가지 않게 막는다 —
     * 합계만 0에서 막으면 남은 점수보다 큰 회복(-1)이나 면제 뒤 남은 회복이 '적립금' 처럼 남아 다음 벌점을 깎았다 (자체 점검 #9)
     */
    // 서버 코드에서는 같은 클래스 안(예: record·screen)에서만 불려 아래 readOnly 는 적용되지 않는다 — 부른 메서드의 묶음 안에서 돈다
    @Transactional(readOnly = true)
    public BigDecimal points(UUID managerId) {
        Instant now = clock.now();
        BigDecimal total = BigDecimal.ZERO;
        // 저장소는 최신순으로 준다 — 정렬하려고 복사본을 만든다
        List<ManagerPenalty> rows = new ArrayList<>(penalties.findByManagerIdOrderByCreatedAtDesc(managerId));
        rows.sort(java.util.Comparator.comparing(ManagerPenalty::getCreatedAt)); // 오래된 것부터 (같은 시각이면 받은 순서 그대로)
        for (ManagerPenalty p : rows) {
            // 면제·만료 행, 그리고 만료 시각이 지났지만 매일 정리(expireOld)가 아직 안 돈 행은 뺀다
            if (!ManagerPenalty.ACTIVE.equals(p.getStatus()) || !p.getExpiresAt().isAfter(now)) continue;
            total = total.add(p.getPoints()).max(BigDecimal.ZERO); // 한 줄 더할 때마다 0 밑으로 내려가지 않게
        }
        return total;
    }

    /** 이 매니저가 이 정기청소 시리즈를 취소·미방문한 적이 있는가 → 다음 회차 우선 배정에서 뺀다 */
    @Transactional(readOnly = true)
    public boolean droppedSeries(UUID managerId, UUID seriesId) {
        // 부르는 곳: BookingSeriesService.previousManager, OfferService.dispatchPreferred. 단건 예약(seriesId 없음)은 false
        // 행의 상태는 보지 않는다 — 운영자가 면제한(또는 만료된) 취소도 센다. 정책 "한 회차라도 취소하면 우선 제안에서 빠집니다" 에는 조건이 없고
        // 면제는 점수에만 영향을 준다 (docs/manager-policy.md §1·§4, 사용자 결정 A — 문제 목록 27번). 같은 예약 후보 제외(bookingDroppers)와 같은 규칙이다
        // 지각은 '취소' 가 아니다 — 30분 늦은 한 번으로 그 고객의 담당에서 빠지지 않게 (docs/manager-policy.md §4 "한 회차라도 취소하면", 자체 점검 #7)
        return seriesId != null && penalties.existsByManagerIdAndSeriesIdAndCategoryIn(managerId, seriesId, DROPS);
    }

    /** 이 예약의 방문을 취소한 매니저들 — 같은 예약을 다시 받지 않는다 (docs/manager-policy.md "같은 매니저에게 다시 배정되지 않습니다", 자체 점검 #6) */
    @Transactional(readOnly = true)
    public Set<UUID> bookingDroppers(UUID bookingId) {
        // 부르는 곳: OfferService (콜 후보에서 빼기, 수락 막기). 저장소 쿼리가 상태를 보지 않아 면제·만료된 취소도 센다
        // 방문 취소(일찍·늦게)만 센다 — 미방문·지각은 넣지 않는다
        return new HashSet<>(penalties.managersByBookingAndCategory(bookingId, List.of(ManagerPenalty.CANCEL_EARLY, ManagerPenalty.CANCEL_LATE)));
    }

    private static final List<String> DROPS = List.of(ManagerPenalty.CANCEL_EARLY, ManagerPenalty.CANCEL_LATE, ManagerPenalty.NO_SHOW); // droppedSeries 가 보는 종류 — 지각·회복은 빠진다

    /** MY › 패널티 내역 화면 */
    @Transactional(readOnly = true)
    public PenaltyResponse screen(UUID managerId) {
        // GET /manager/penalties 응답. points = 누적 점수 글자(끝의 0 을 뗀다, 예 "1.5"), nextThreshold = 다음 단계 안내,
        // suspension = 정지 중일 때만 문구(아니면 null), items = 원장 전체(최신순, 면제·만료 행도 상태 문구와 함께)
        JsonNode rule = settings.node(SETTING);
        BigDecimal total = points(managerId);
        ManagerProfile m = managers.findById(managerId).orElseThrow(ApiException::notFound);
        List<PenaltyResponse.Item> items = new ArrayList<>();
        for (ManagerPenalty p : penalties.findByManagerIdOrderByCreatedAtDesc(managerId)) {
            // 날짜는 서울 시각으로 "9월 18일 오후 2:00" 모양 (KoreanFormat.dateTimeShort)
            items.add(new PenaltyResponse.Item(p.getId(), label(p.getCategory()), signed(p.getPoints()),
                    KoreanFormat.dateTimeShort(AppClock.toSeoul(p.getCreatedAt()).toLocalDate(), AppClock.toSeoul(p.getCreatedAt()).toLocalTime()), statusLabel(p), p.getDetail()));
        }
        String suspension = m.isSuspended(clock.today())
                ? "콜 배정 정지 중 · " + KoreanFormat.dateShort(m.getSuspendedUntil()) + " 까지" : null;
        return new PenaltyResponse(total.stripTrailingZeros().toPlainString(), nextThreshold(total, rule), suspension, items);
    }

    /** "5점이 되면 7일 동안 콜을 받을 수 없어요" — 다음 단계를 미리 알려 준다 */
    private String nextThreshold(BigDecimal total, JsonNode rule) {
        // 오름차순 기준 중 아직 도달하지 않은 첫 단계를 문구로. 모든 단계를 넘었으면 null.
        // warn 의 '경고 알림' 은 그 단계를 넘는 기록 때 PushHandler 가 알림 설정과 무관하게 보내는 경고 전용 푸시다 (문제 목록 14번)
        for (JsonNode t : rule.path("thresholds")) {
            if (total.compareTo(t.path("points").decimalValue()) < 0) {
                int points = t.path("points").asInt();
                return switch (t.path("action").asString()) {
                    case "warn" -> points + "점이 되면 경고 알림을 보내드려요";
                    case "suspend" -> points + "점이 되면 " + t.path("days").asInt() + "일 동안 콜을 받을 수 없어요";
                    default -> points + "점이 되면 계약 유지 여부를 검토해요";
                };
            }
        }
        return null;
    }

    /** 내역 한 줄의 점수 표시: 양수는 "+1점", 0·음수는 "0점"·"-1점". 끝의 0 은 뗀다 (0.50 → 0.5) */
    private static String signed(BigDecimal points) {
        String n = points.stripTrailingZeros().toPlainString();
        return points.signum() > 0 ? "+" + n + "점" : n + "점";
    }

    /** 원장 종류 → 내역 화면 제목. 괄호 안 '24시간' 은 고정 글자라 설정 lateCancelHours 를 바꾸면 여기도 같이 바꿔야 한다 */
    private static String label(String category) {
        return switch (category) {
            case ManagerPenalty.CANCEL_EARLY -> "방문 취소 (24시간 전)";
            case ManagerPenalty.CANCEL_LATE -> "방문 취소 (24시간 이내)";
            case ManagerPenalty.NO_SHOW -> "미방문";
            case ManagerPenalty.TARDY -> "지각";
            case ManagerPenalty.RECOVERY -> "무사고 회복";
            default -> category;
        };
    }

    /** 원장 행 상태 → 내역 화면 상태 문구 (active 는 '적용 중') */
    private static String statusLabel(ManagerPenalty p) {
        return switch (p.getStatus()) {
            case ManagerPenalty.WAIVED -> "면제됨";
            case ManagerPenalty.EXPIRED -> "기간 만료";
            default -> "적용 중";
        };
    }

    // ── 매일 (ManagerPenaltyCleaner) ──

    /** 만료 시각(expires_at)이 지난 active 행을 만료로 — 벌점은 기록 후 expireMonths(기본 12)개월, 회복 행은 더 일찍 끝날 수 있다(recover) */
    public int expireOld() {
        // 결과 = 바꾼 행 수 (ManagerPenaltyCleaner 가 로그에 쓴다). 늦게 돌아도 points() 는 만료 시각을 직접 봐서 합이 틀리지 않는다
        List<ManagerPenalty> due = penalties.findByStatusAndExpiresAtBefore(ManagerPenalty.ACTIVE, clock.now());
        due.forEach(ManagerPenalty::expire);
        return due.size();
    }

    /** 무사고 recoveryMonths 개월마다 1점 회복 (음수 행). 남은 점수가 없으면 아무것도 하지 않는다 */
    public int recover() {
        Instant now = clock.now();
        JsonNode rule = settings.node(SETTING);
        Instant since = months(now, -rule.path("recoveryMonths").asInt()); // 이 시각 뒤에 생긴 행이 하나도 없어야 '무사고'
        int count = 0;
        // 양수 벌점이 아직 유효한 매니저만 훑는다. 결과 = 회복 행을 적은 매니저 수
        for (UUID managerId : penalties.managersWithActivePoints(now)) {
            if (penalties.existsByManagerIdAndCreatedAtAfter(managerId, since)) continue; // 최근에 일이 있었다(회복 행 포함 — 3개월에 한 번)
            BigDecimal remaining = points(managerId);
            if (remaining.signum() <= 0) continue; // 앞의 회복 행들이 남은 벌점을 이미 다 상쇄했다
            // 상쇄할 벌점보다 오래 남으면 그 벌점이 만료된 뒤 새 벌점을 깎는다 → 남은 벌점 중 가장 늦은 만료에 맞춘다 (전체 재점검 P2)
            Instant expires = months(now, rule.path("expireMonths").asInt());
            Instant latest = penalties.latestActivePositiveExpiry(managerId, now);
            if (latest != null && latest.isBefore(expires)) expires = latest;
            BigDecimal amount = BigDecimal.ONE.min(remaining); // 0.5점 남았으면 0.5점만 — 내역 화면의 '-1' 과 실제 차감이 같게 (자체 점검 #9)
            penalties.save(ManagerPenalty.of(managerId, null, null, ManagerPenalty.RECOVERY, amount.negate(), null, null, now, expires)); // 회복 행: 음수 점수, 예약·시리즈 없음. 푸시는 보내지 않는다
            count++;
        }
        if (count > 0) log.info("무사고 회복 {}건", count);
        return count;
    }

    /** 이 매니저의 패널티 원장을 모두 지운다 — 매니저 탈퇴 때(AuthService.deleteAccount), 5년 파기 때 남은 행이 있으면 한 번 더(DeletedAccountPurger) */
    public void forget(UUID managerId) {
        // 탈퇴 때 지우는 이유: 매니저 동의의 '보유 기간: 매니저 탈퇴 시까지'(V039)를 지키려고.
        // 탈퇴 때 지웠으면 파기 때는 지울 행이 없다 — 이 규칙 전에 탈퇴해 남은 행만 파기 때 지워진다
        penalties.deleteByManagerId(managerId);
    }

    /**
     * 운영자 면제 (증빙 확인) — POST /admin/penalties/{id}/waive(AdminPenaltyController)가 부른다 (문제 목록 3번).
     * 원장 행을 waived 로 바꾸고, 면제 뒤 누적 점수가 가장 낮은 정지 기준 아래면 콜 배정 정지도 푼다 — 클래스의 @Transactional 로 한 묶음이라
     * 점수만 빠지고 정지가 남는 일이 없다 (예전에는 SQL 로 status 만 바꿔 suspended_until 이 남았다).
     * 이미 면제된 행은 바꾸지 않고 지금 상태를 돌려준다(다시 불러도 같다). 회복 행(음수 — 면제하면 점수가 늘어난다)과
     * 기간이 끝난 행(이미 점수에 없다)은 INVALID_STATE, 없는 id 는 NOT_FOUND.
     * 면제 뒤에도 정지 기준 이상이면 정지 기간은 그대로 둔다 — 더 낮은 단계의 기간으로 줄이지 않는다.
     * 잠금 순서: 패널티 잠금(매니저별) → 매니저 행 → 원장 행. 행의 상태는 잠근 뒤에 다시 읽어 판단한다 (합친 38건 검토 R5).
     */
    public kr.ansimclean.api.profile.dto.PenaltyWaiveResponse waive(UUID penaltyId, String note) {
        // 잠그기 전에는 매니저 id 만 읽는다 — 행을 먼저 올리면 잠근 뒤에도 그 옛 사본으로 판단해, 같은 요청을 두 번 보냈을 때 두 번째가 면제 메모·시각을
        // 덮어쓰거나, 그사이 탈퇴(forget)가 지운 행을 고치다 500 이 났다 (합친 38건 검토 R5)
        UUID managerId = penalties.findManagerIdById(penaltyId).orElseThrow(ApiException::notFound);
        // record 와 같은 매니저 잠금 — 동시에 들어온 새 벌점이 건 정지를 그 벌점이 빠진 옛 합계로 풀어 버리지 않게. 묶음이 끝나면 풀린다
        penalties.lockManager(managerId);
        // 매니저 행도 잠근다(SELECT … FOR UPDATE) — 탈퇴는 매니저 행 → 원장 행 순서로 잠그므로, 같은 순서를 따라 아래 원장 행 잠금과 엇갈려 교착하지 않게.
        // 이 메서드는 예약을 잠근 채 부르지 않아 콜 수락(매니저 → 예약)과도 엇갈리지 않는다
        managers.findForUpdate(managerId);
        // 잠근 뒤에 행을 읽는다 — 앞선 면제가 커밋한 상태를 보고, 탈퇴가 지우는 중이면 그 커밋을 기다렸다가 없으면 404
        ManagerPenalty p = penalties.findForUpdate(penaltyId).orElseThrow(ApiException::notFound);
        if (ManagerPenalty.WAIVED.equals(p.getStatus())) return waiveResult(p); // 이미 면제 — 메모·시각도 처음 것 그대로
        if (ManagerPenalty.RECOVERY.equals(p.getCategory())) throw new ApiException(ErrorCode.INVALID_STATE, "무사고 회복 기록은 면제할 수 없어요");
        if (!ManagerPenalty.ACTIVE.equals(p.getStatus())) throw new ApiException(ErrorCode.INVALID_STATE, "기간이 끝난 기록은 이미 점수에 들어가지 않아요");
        p.waive(note, clock.now()); // 행은 지우지 않고 상태만 waived 로 — points() 합계에서 빠진다
        // 면제한 점수 때문에 걸린 정지면 같이 푼다 — 문서는 '점수에 반영되지 않는다' 고 약속한다 (전체 재점검 P3)
        BigDecimal total = points(p.getManagerId());
        // 설정의 suspend 단계 중 가장 낮은 점수를 찾는다. 면제 뒤 누적 점수가 그보다 낮으면 정지를 풀고, 아직 이상이면 그대로 둔다
        BigDecimal lowestSuspend = null;
        for (JsonNode t : settings.node(SETTING).path("thresholds")) {
            if ("suspend".equals(t.path("action").asString()) && (lowestSuspend == null || t.path("points").decimalValue().compareTo(lowestSuspend) < 0)) lowestSuspend = t.path("points").decimalValue();
        }
        if (lowestSuspend != null && total.compareTo(lowestSuspend) < 0) managers.findById(p.getManagerId()).ifPresent(m -> m.liftSuspension(clock.now()));
        log.info("매니저 패널티 면제 {} {} (누적 {}점)", p.getManagerId(), p.getCategory(), total.toPlainString()); // 메모는 자유 입력이라 로그에 남기지 않는다
        return waiveResult(p);
    }

    /** 면제 API 응답: 지금 누적 점수(끝의 0 을 뗀 글자)와, 오늘 정지 중이면 정지 마지막 날 "YYYY-MM-DD"(아니면 null) */
    private kr.ansimclean.api.profile.dto.PenaltyWaiveResponse waiveResult(ManagerPenalty p) {
        String points = points(p.getManagerId()).stripTrailingZeros().toPlainString();
        String until = managers.findById(p.getManagerId()).filter(m -> m.isSuspended(clock.today())).map(m -> m.getSuspendedUntil().toString()).orElse(null);
        return new kr.ansimclean.api.profile.dto.PenaltyWaiveResponse(p.getId(), p.getManagerId(), points, until);
    }

    /** 앱이 취소 컷오프를 어겼을 때의 문구 */
    public ApiException cutoffException() {
        // 만들기만 하고 던지지는 않는다 — JobService.doCancelByManager 가 던지고, 응답은 409(INVALID_STATE) + 이 문구
        return new ApiException(ErrorCode.INVALID_STATE,
                "방문 " + cancelCutoffHours() + "시간 전부터는 앱에서 취소할 수 없어요. 고객센터로 전화해주세요");
    }
}
