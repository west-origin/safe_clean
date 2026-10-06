package kr.ansimclean.api.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** job_pauses — 일시정지 구간 (경과 시간에서 뺀다, §6.3) */
// 매니저가 작업을 일시정지할 때마다 한 행(JobService.pause 가 open 으로 만들어 저장), 재개·완료 때 resumed_at 을 채워 닫는다.
// 한 작업에 여러 번 멈출 수 있어 행이 여러 개다. JobService 가 모든 구간 길이를 더해 경과 시간에서 뺀다(pausedSeconds).
@Entity
@Table(name = "job_pauses")
public class JobPause {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "booking_id", columnDefinition = "uuid", nullable = false)
    private UUID bookingId;

    @Column(columnDefinition = "text", nullable = false)
    private String reason; // 중단 사유 문구 (catalog_reasons 의 kind=pause 목록 중 하나 — JobService.pause 가 검사)

    @Column(name = "paused_at", nullable = false)
    private Instant pausedAt;

    @Column(name = "resumed_at")
    private Instant resumedAt; // null 이면 아직 멈춰 있는 구간

    // JPA 가 DB 행을 읽어 객체를 만들 때 쓰는 빈 생성자. 코드에서는 open(…) 으로 만든다
    protected JobPause() {}

    /** 지금 시작하는 중단 구간 하나 (resumedAt 은 비어 있다). DB 저장은 JobService.pause 가 한다 */
    public static JobPause open(UUID bookingId, String reason, Instant now) {
        JobPause p = new JobPause();
        p.id = UUID.randomUUID();
        p.bookingId = bookingId;
        p.reason = reason;
        p.pausedAt = now;
        return p;
    }

    // 읽기용 getter. 값은 open·close 로만 바뀐다
    public UUID getId() { return id; }
    public UUID getBookingId() { return bookingId; }
    public String getReason() { return reason; }
    public Instant getPausedAt() { return pausedAt; }
    public Instant getResumedAt() { return resumedAt; }

    /** 아직 재개하지 않은 구간인가 */
    public boolean isOpen() {
        return resumedAt == null;
    }

    /** 구간을 닫는다 (재개·완료 때). 이미 닫혔으면 처음 닫은 시각을 그대로 둔다 — 두 번 불러도 길이가 늘지 않게 */
    public void close(Instant now) {
        if (resumedAt == null) resumedAt = now;
    }

    /** 지금까지의 중단 길이 (열려 있으면 now 까지) */
    public Duration length(Instant now) {
        return Duration.between(pausedAt, resumedAt == null ? now : resumedAt);
    }
}
