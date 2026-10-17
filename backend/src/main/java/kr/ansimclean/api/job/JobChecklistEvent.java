package kr.ansimclean.api.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/** 체크·해제 당시 항목과 매니저를 복사한 이력. JobService에서 추가만 한다 (V054). */
@Entity
@Immutable
@Table(name = "job_checklist_events")
public class JobChecklistEvent {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;
    @Column(name = "event_order", insertable = false, updatable = false)
    private Long eventOrder;
    @Column(name = "booking_id", columnDefinition = "uuid", nullable = false)
    private UUID bookingId;
    // 외래키를 두지 않아 일정 변경으로 원래 항목을 지워도 이력은 남는다.
    @Column(name = "item_id", columnDefinition = "uuid", nullable = false)
    private UUID itemId;
    @Column(name = "group_name", columnDefinition = "text", nullable = false)
    private String groupName;
    @Column(columnDefinition = "text", nullable = false)
    private String label;
    @Column(nullable = false)
    private boolean done;
    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;
    @Column(name = "manager_id", columnDefinition = "uuid", nullable = false)
    private UUID managerId;

    protected JobChecklistEvent() {}

    public static JobChecklistEvent of(JobChecklistItem item, boolean done, Instant at, UUID managerId) {
        JobChecklistEvent event = new JobChecklistEvent();
        event.id = UUID.randomUUID();
        event.bookingId = item.getBookingId();
        event.itemId = item.getId();
        event.groupName = item.getGroupName();
        event.label = item.getLabel();
        event.done = done;
        event.changedAt = at;
        event.managerId = managerId;
        return event;
    }

    public UUID getId() { return id; }
    public Long getEventOrder() { return eventOrder; }
    public UUID getBookingId() { return bookingId; }
    public UUID getItemId() { return itemId; }
    public String getGroupName() { return groupName; }
    public String getLabel() { return label; }
    public boolean isDone() { return done; }
    public Instant getChangedAt() { return changedAt; }
    public UUID getManagerId() { return managerId; }
}
