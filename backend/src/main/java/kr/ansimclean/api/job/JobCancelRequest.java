package kr.ansimclean.api.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;

import java.time.Instant;
import java.util.UUID;

/**
 * manager_cancel_requests — 매니저의 방문 취소 요청 한 건 (V068, docs/domain.md '매니저 방문 취소 승인·재배정').
 * 매니저가 만들고(JobService.cancelByManager), 어드민이 승인·거절한다(offer/ReassignService).
 * 승인 전에 작업이 시작되거나 고객이 취소·일정 변경하면 자동으로 닫힌다(close).
 * 상태 흐름: pending → approved | rejected | closed. 상태는 아래 메서드로만 바뀐다.
 */
@Entity
@Table(name = "manager_cancel_requests")
public class JobCancelRequest {
    // status 칸 값 — DB check 제약과 같다
    public static final String PENDING = "pending";   // 어드민 결정을 기다린다 (예약당 하나 — 부분 유니크 인덱스)
    public static final String APPROVED = "approved"; // 승인 — 배정을 풀고 재배정을 시작했다
    public static final String REJECTED = "rejected"; // 거절 — 예약은 그 매니저에게 그대로
    public static final String CLOSED = "closed";     // 결정 전에 쓸모가 없어졌다 (decisionNote = 아래 CLOSED_* 사유)

    // 자동 종료 사유 (decision_note 에 남는다)
    public static final String CLOSED_JOB_STARTED = "job_started";             // 매니저가 그대로 청소를 시작했다
    public static final String CLOSED_BOOKING_CANCELLED = "booking_cancelled"; // 고객 취소·시스템 취소
    public static final String CLOSED_RESCHEDULED = "rescheduled";             // 고객이 일정을 바꿔 매칭을 새로 시작했다

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "booking_id", columnDefinition = "uuid", nullable = false)
    private UUID bookingId;

    @Column(name = "manager_id", columnDefinition = "uuid", nullable = false)
    private UUID managerId;

    @Column(columnDefinition = "text", nullable = false)
    private String reason; // 취소 사유 key (app_settings.manager_cancel_reasons)

    @Column(columnDefinition = "text")
    private String detail; // 매니저가 적은 상세 (선택)

    @Column(columnDefinition = "text", nullable = false)
    private String status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "decided_at")
    private Instant decidedAt; // 승인·거절·자동 종료 시각

    @Column(name = "decided_by", columnDefinition = "uuid")
    private UUID decidedBy; // 승인·거절한 어드민 (자동 종료는 null)

    @Column(name = "decision_note", columnDefinition = "text")
    private String decisionNote; // 거절 메모 또는 자동 종료 사유

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // JPA 가 DB 행을 객체로 만들 때 쓰는 빈 생성자. 코드에서는 아래 pending() 으로만 만든다
    protected JobCancelRequest() {}

    /** 새 대기 요청 (저장은 부르는 쪽이) */
    public static JobCancelRequest pending(UUID bookingId, UUID managerId, String reason, String detail, Instant now) {
        JobCancelRequest r = new JobCancelRequest();
        r.id = UUID.randomUUID();
        r.bookingId = bookingId;
        r.managerId = managerId;
        r.reason = reason;
        r.detail = detail;
        r.status = PENDING;
        r.requestedAt = now;
        r.createdAt = now;
        return r;
    }

    public UUID getId() { return id; }
    public UUID getBookingId() { return bookingId; }
    public UUID getManagerId() { return managerId; }
    public String getReason() { return reason; }
    public String getDetail() { return detail; }
    public String getStatus() { return status; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public UUID getDecidedBy() { return decidedBy; }
    public String getDecisionNote() { return decisionNote; }

    public boolean isPending() { return PENDING.equals(status); }
    public boolean isApproved() { return APPROVED.equals(status); }

    /** 어드민 승인. 대기 중이 아니면 INVALID_STATE (이미 승인한 요청은 부르는 쪽이 먼저 걸러 같은 응답을 준다) */
    public void approve(UUID adminId, Instant now) {
        requirePending();
        status = APPROVED;
        decidedAt = now;
        decidedBy = adminId;
    }

    /** 어드민 거절. note = 거절 메모(선택) */
    public void reject(UUID adminId, String note, Instant now) {
        requirePending();
        status = REJECTED;
        decidedAt = now;
        decidedBy = adminId;
        decisionNote = note;
    }

    /** 결정 전에 쓸모가 없어졌다 — 대기 중일 때만 닫고 true. why = CLOSED_* */
    public boolean close(String why, Instant now) {
        if (!isPending()) return false;
        status = CLOSED;
        decidedAt = now;
        decisionNote = why;
        return true;
    }

    private void requirePending() {
        if (!isPending()) throw new ApiException(ErrorCode.INVALID_STATE, "이미 처리된 취소 요청이에요");
    }
}
