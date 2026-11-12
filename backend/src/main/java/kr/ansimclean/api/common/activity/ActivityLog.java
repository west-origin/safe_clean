package kr.ansimclean.api.common.activity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * activity_logs 한 행 (rbac.md §11, V066). 만들기만 하고 바꾸지 않는다 — @Immutable 이라 JPA 도 UPDATE 를 보내지 않고,
 * DB 트리거가 UPDATE·DELETE·TRUNCATE 를 막는다. 행은 ActivityLogService.record 만 만든다.
 */
@Entity
@Immutable
@Table(name = "activity_logs")
public class ActivityLog {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "actor_id", columnDefinition = "uuid")
    private UUID actorId; // null = 시스템

    @Column(name = "actor_role", columnDefinition = "text")
    private String actorRole;

    @Column(columnDefinition = "text", nullable = false)
    private String action;

    @Column(name = "target_type", columnDefinition = "text", nullable = false)
    private String targetType;

    @Column(name = "target_id", columnDefinition = "text")
    private String targetId;

    // jsonb 칸을 자바에서는 JSON 글자로 다룬다 (OutboxEvent.payload 와 같은 방식)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_data", columnDefinition = "jsonb")
    private String beforeData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_data", columnDefinition = "jsonb")
    private String afterData;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ActivityLog() {} // JPA 용

    static ActivityLog of(UUID actorId, String actorRole, String action, String targetType, String targetId,
                          String beforeJson, String afterJson, String reason, Instant now) {
        ActivityLog l = new ActivityLog();
        l.id = UUID.randomUUID();
        l.actorId = actorId;
        l.actorRole = actorRole;
        l.action = action;
        l.targetType = targetType;
        l.targetId = targetId;
        l.beforeData = beforeJson;
        l.afterData = afterJson;
        l.reason = reason;
        l.createdAt = now;
        return l;
    }

    public UUID getId() { return id; }
    public UUID getActorId() { return actorId; }
    public String getActorRole() { return actorRole; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public String getBeforeData() { return beforeData; }
    public String getAfterData() { return afterData; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
