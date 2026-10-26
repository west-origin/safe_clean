package kr.ansimclean.api.job;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** 체크 완료마다 이전 체크 경계~현재 시각을 해당 공간으로 라벨링한다. 실제 방 이동 감지 결과가 아니다. */
@Entity
@Table(name = "recording_segments")
public class RecordingSegment {
    @Id private UUID id;
    @Column(name = "live_session_id", nullable = false) private UUID liveSessionId;
    @Column(name = "checklist_event_id", nullable = false) private UUID checklistEventId;
    @Column(name = "item_id", nullable = false) private UUID itemId;
    @Column(name = "room_code", nullable = false) private String roomCode;
    @Column(name = "room_name", nullable = false) private String roomName;
    @Column(name = "start_at", nullable = false) private Instant startAt;
    @Column(name = "end_at", nullable = false) private Instant endAt;
    // DB 트리거가 실제 녹화 시작 시각·파일 길이를 확인해 계산한다. 아직 확인 전이면 null.
    @Column(name = "start_ms", insertable = false, updatable = false) private Long startMs;
    @Column(name = "end_ms", insertable = false, updatable = false) private Long endMs;
    @Column(name = "label_source", nullable = false) private String labelSource;
    @Column(name = "review_status", nullable = false) private String reviewStatus;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected RecordingSegment() {}
    static RecordingSegment of(UUID session, JobChecklistEvent event, JobChecklistItem item, Instant start, Instant end, UUID manager) {
        RecordingSegment s = new RecordingSegment();
        s.id = UUID.randomUUID(); s.liveSessionId = session; s.checklistEventId = event.getId(); s.itemId = item.getId();
        s.roomName = item.getGroupName(); s.roomCode = roomCode(s.roomName);
        s.startAt = start; s.endAt = end; s.labelSource = "checklist"; s.reviewStatus = "pending";
        s.createdBy = manager; s.createdAt = end; s.updatedAt = end;
        return s;
    }
    static String roomCode(String room) {
        if (room.contains("거실")) return "living_room";
        if (room.contains("주방")) return "kitchen";
        if (room.contains("욕실") || room.contains("화장실")) return "bathroom";
        if (room.contains("침실") || room.endsWith("방")) return "bedroom";
        return "other";
    }
    public Instant getEndAt() { return endAt; }
    public Instant getStartAt() { return startAt; }
    public String getRoomCode() { return roomCode; }
    public String getRoomName() { return roomName; }
    public UUID getChecklistEventId() { return checklistEventId; }
}
