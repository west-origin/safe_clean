package kr.ansimclean.api.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** job_checklist_items — 배정 시 템플릿에서 복사, 작업 중 체크 (§6.1 assign) */
// 예약(booking_id) 하나에 여러 행. 매니저가 콜을 수락해 배정될 때 JobService.createChecklist 가
// 카테고리 템플릿(catalog.ChecklistTemplate)을 복사해 만들고, 매니저 앱 진행 중 작업 화면의 체크 토글이 done 을 바꾼다.
// 매니저 방문 취소·고객 일정 변경 때는 행을 지운다(JobService.clearChecklist) — 다음 매니저가 처음부터 시작하게.
@Entity
@Table(name = "job_checklist_items")
public class JobChecklistItem {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "booking_id", columnDefinition = "uuid", nullable = false)
    private UUID bookingId;

    @Column(name = "group_name", columnDefinition = "text", nullable = false)
    private String groupName; // 묶음 이름 (예: '거실') — JobService.activeJob 이 이 값으로 항목을 묶어 보낸다

    @Column(columnDefinition = "text", nullable = false)
    private String label; // 화면에 보이는 한 줄 문구 (템플릿에서 복사)

    @Column(nullable = false)
    private boolean done;

    @Column(name = "done_at")
    private Instant doneAt; // 체크한 시각(UTC). 체크를 풀면 null

    @Column(nullable = false)
    private int sort; // 표시 순서 (템플릿의 sort 그대로)

    // JPA 가 DB 행을 읽어 객체를 만들 때 쓰는 빈 생성자. 코드에서는 아래 of(…) 로 만든다
    protected JobChecklistItem() {}

    /** 새 항목 하나(done = false). id 는 여기서 무작위로 정한다. DB 저장은 부르는 쪽(JobService)이 save 로 한다 */
    public static JobChecklistItem of(UUID bookingId, String groupName, String label, int sort) {
        JobChecklistItem i = new JobChecklistItem();
        i.id = UUID.randomUUID();
        i.bookingId = bookingId;
        i.groupName = groupName;
        i.label = label;
        i.sort = sort;
        return i;
    }

    // 읽기용 getter. setter 는 없고 값은 of·mark 로만 바뀐다
    public UUID getId() { return id; }
    public UUID getBookingId() { return bookingId; }
    public String getGroupName() { return groupName; }
    public String getLabel() { return label; }
    public boolean isDone() { return done; }
    public Instant getDoneAt() { return doneAt; }
    public int getSort() { return sort; }

    /**
     * 체크(true)·해제(false). 같은 상태 재요청은 최초 시각을 보존한다. 해제 후 재체크는 새 완료 시각이다.
     * JobService.setChecklistItem 은 save 를 부르지 않는다 — 커밋 때 바뀐 필드가 저장된다 (CODE_GUIDE.md 3-3)
     */
    public void mark(boolean done, Instant now) {
        if (this.done == done) return;
        this.done = done;
        this.doneAt = done ? now : null;
    }
}
