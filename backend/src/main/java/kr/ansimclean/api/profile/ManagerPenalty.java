package kr.ansimclean.api.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * manager_penalties — 매니저 방문 취소·미방문 원장 (docs/manager-policy.md, 사용자 승인 2026-09-22).
 * append-only: 점수를 깎을 때도 지우지 않고 음수 행(category=recovery)을 더한다. 누적 점수 = active 행의 합.
 *
 * 새 행은 ManagerPenaltyService 가 만든다 — record(예: JobService 의 방문 취소·지각, StaleBookingCleaner 의 미방문)와
 * recover(매일 ManagerPenaltyCleaner 의 무사고 회복).
 * 주의: 위 '누적 점수 = active 행의 합' 은 줄인 말이다. ManagerPenaltyService.points 는 active 가 아니거나 만료 시각이 지난 행을 빼고,
 * 오래된 순으로 더하면서 한 줄마다 0 아래로 내려가지 않게 막는다 (남은 벌점보다 큰 회복이 다음 벌점을 깎지 않게).
 * 예외: 'append-only' 는 점수 계산 이야기다 — 매니저가 탈퇴하면(5년 파기 때 남아 있으면 그때도) 이 매니저의 행을 모두 지운다
 * (ManagerPenaltyService.forget).
 */
@Entity
@Table(name = "manager_penalties")
public class ManagerPenalty {
    // category 값 (DB check 제약이 허용하는 값과 같다 — 늘리면 새 Flyway 마이그레이션도 필요).
    // 아래 시간 숫자는 기본값이고 실제 값은 app_settings.manager_penalty 에서 온다.
    // cancel_early 는 무료 한도 안이어도 0점 행으로 남는다 — 이번 달 몇 번 썼는지 세려면 필요하다 (ManagerPenaltyService.record)
    public static final String CANCEL_EARLY = "cancel_early";   // 방문 24시간 전까지 (점수는 무료 한도 초과분만 — 한도 안은 0점 행)
    public static final String CANCEL_LATE = "cancel_late";     // 24~4시간 전
    public static final String NO_SHOW = "no_show";             // 4시간 이내·미방문
    public static final String TARDY = "tardy";                 // 30분 초과 지각
    public static final String RECOVERY = "recovery";           // 무사고 3개월 회복 (음수)

    // status 값: 적용 중(점수에 들어감) · 만료(expire) · 면제(waive). 만료·면제된 행도 원장에는 남는다
    public static final String ACTIVE = "active";
    public static final String EXPIRED = "expired";
    public static final String WAIVED = "waived";

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "manager_id", columnDefinition = "uuid", nullable = false)
    private UUID managerId;

    // 어느 예약의 방문 때문인가. 회복 행은 null
    @Column(name = "booking_id", columnDefinition = "uuid")
    private UUID bookingId;

    // 정기청소 회차였으면 그 시리즈 id — 다음 회차 우선 배정에서 이 매니저를 뺄 때 본다 (ManagerPenaltyService.droppedSeries)
    @Column(name = "series_id", columnDefinition = "uuid")
    private UUID seriesId;

    @Column(columnDefinition = "text", nullable = false)
    private String category;

    // 점수. BigDecimal = 소수를 오차 없이 다루는 숫자 (지각 0.5점 같은 값). 회복 행은 음수, 무료 취소는 0
    @Column(nullable = false)
    private BigDecimal points;

    // 사유 코드 — 방문 취소면 매니저가 고른 사유, 미방문이면 StaleBookingCleaner 가 넣는 코드. 지각·회복은 null
    @Column(columnDefinition = "text")
    private String reason;

    // 상세 문구 — 매니저가 직접 쓴 내용이거나 "N분 늦게 시작" 같은 설명. 패널티 내역 응답(ManagerPenaltyService.screen)에 들어간다
    @Column(columnDefinition = "text")
    private String detail;

    @Column(columnDefinition = "text", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // 이 시각이 지나면 점수에서 빠진다 (기본 12개월 뒤. 회복 행은 남은 벌점 중 가장 늦은 만료보다 늦지 않게 — recover)
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "waived_at")
    private Instant waivedAt;

    @Column(name = "waived_note", columnDefinition = "text")
    private String waivedNote;

    // JPA 가 DB 행을 읽어 객체를 만들 때 쓰는 빈 생성자
    protected ManagerPenalty() {}

    /** 새 원장 행 (status=active, id 는 여기서 UUID 생성). 저장은 부르는 쪽(ManagerPenaltyService)이 한다 */
    public static ManagerPenalty of(UUID managerId, UUID bookingId, UUID seriesId, String category, BigDecimal points,
                                    String reason, String detail, Instant now, Instant expiresAt) {
        ManagerPenalty p = new ManagerPenalty();
        p.id = UUID.randomUUID();
        p.managerId = managerId;
        p.bookingId = bookingId;
        p.seriesId = seriesId;
        p.category = category;
        p.points = points;
        p.reason = reason;
        p.detail = detail;
        p.status = ACTIVE;
        p.createdAt = now;
        p.expiresAt = expiresAt;
        return p;
    }

    /** 증빙이 인정되면 점수를 빼지 않고 이 행을 면제로 (원장은 남는다) */
    public void waive(String note, Instant now) { // 부르는 곳: ManagerPenaltyService.waive (운영자 면제)
        this.status = WAIVED;
        this.waivedAt = now;
        this.waivedNote = note;
    }

    /** 만료 시각이 지난 active 행을 만료로 표시한다 (매일 ManagerPenaltyCleaner → ManagerPenaltyService.expireOld) */
    public void expire() {
        this.status = EXPIRED;
    }

    public UUID getId() { return id; }
    public UUID getManagerId() { return managerId; }
    public UUID getBookingId() { return bookingId; }
    public UUID getSeriesId() { return seriesId; }
    public String getCategory() { return category; }
    public BigDecimal getPoints() { return points; }
    public String getReason() { return reason; }
    public String getDetail() { return detail; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
