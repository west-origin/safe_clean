package kr.ansimclean.api.common.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * 외부 효과(예: 푸시·LiveKit 호출) 한 건. 업무 트랜잭션과 **같이 커밋**되므로 롤백되면 같이 사라지고,
 * 커밋 직후 서버가 죽어도 행이 남아 OutboxDispatcher 가 다시 보낸다 (BACKEND_PLAN.md §2.4).
 * 인증번호 문자는 아웃박스를 거치지 않는다 — OtpService 가 트랜잭션 밖에서 바로 보내고 실패를 그 자리에서 알린다.
 * outbox_events 테이블 한 행. 행은 OutboxService.publish 가 만들고,
 * 상태는 OutboxDispatcher 가 markSent/markFailed 로 바꾼다. status: pending → sent | failed.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    public static final int MAX_ATTEMPTS = 6; // 이 횟수만큼 실패하면 failed 로 멈추고 더 시도하지 않는다

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    // 이벤트 종류 = 핸들러를 고르는 키 (보통 이벤트 record 의 클래스 이름, 예: BookingAssigned)
    @Column(columnDefinition = "text", nullable = false)
    private String type;

    // @JdbcTypeCode(SqlTypes.JSON): 자바 String 을 Postgres jsonb 컬럼에 JSON 으로 저장한다. 핸들러가 이 문자열을 읽는다
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String payload;

    // pending(보낼 차례를 기다림) | sent(보냄) | failed(한도까지 실패)
    @Column(columnDefinition = "text", nullable = false)
    private String status;

    // 지금까지 실패한 횟수
    @Column(nullable = false)
    private int attempts;

    // 이 시각이 지나야 보낸다 (OutboxEventRepository.findDueIds 의 조건)
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    // 마지막 실패 내용 (500자까지)
    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    // JPA 가 DB 행을 객체로 만들 때 쓰는 빈 생성자. 코드에서는 pending() 으로 만든다
    protected OutboxEvent() {}

    /** 새 이벤트 행 — 바로 보낼 차례(nextAttemptAt = now)인 pending 상태. 저장은 부르는 쪽(OutboxService)이 한다 */
    public static OutboxEvent pending(String type, String payloadJson, Instant now) {
        OutboxEvent e = new OutboxEvent();
        e.id = UUID.randomUUID();
        e.type = type;
        e.payload = payloadJson;
        e.status = "pending";
        e.attempts = 0;
        e.nextAttemptAt = now;
        e.createdAt = now;
        return e;
    }

    // 읽기 전용 getter. 상태를 바꾸는 것은 아래 mark* 메서드뿐이다
    public UUID getId() { return id; }
    public String getType() { return type; }
    public String getPayload() { return payload; }
    public String getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLastError() { return lastError; }

    /** 보냄으로 표시하고 이전 실패 내용을 지운다 */
    public void markSent(Instant now) {
        this.status = "sent";
        this.sentAt = now;
        this.lastError = null;
    }

    /** 실패 → 지수 백오프로 다시 시도. 한도를 넘기면 failed (운영자가 본다) */
    public void markFailed(String error, Instant now) {
        this.attempts++;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500)); // 앞 500자만 남긴다
        if (attempts >= MAX_ATTEMPTS) {
            this.status = "failed";
        } else {
            // 2의 attempts 제곱 분 뒤: 1회 실패 → 2분, 2회 → 4분 … 5회 → 32분
            this.nextAttemptAt = now.plus(Duration.ofMinutes((long) Math.pow(2, attempts)));
        }
    }
}
