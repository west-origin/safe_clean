package kr.ansimclean.api.support;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 공지 (V004 seed). 어드민이 매니저 앱 공지 관리 화면(AdminNoticeService)으로 등록·수정·삭제한다 (rbac.md §10, 2026-11-12) */
// 읽는 곳: SupportService.notices·notice (고객 앱 공지사항·홈 공지 띠, 매니저 앱 프로필 › 공지사항). 쓰는 곳: AdminNoticeService
@Entity
@Table(name = "notices")
public class Notice {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(columnDefinition = "text", nullable = false)
    private String audience; // customer | manager | all

    @Column(columnDefinition = "text", nullable = false)
    private String title;

    @Column(columnDefinition = "text", nullable = false)
    private String body;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt; // 게시 시각(UTC). 화면 날짜 "2026.09.10" 은 KoreanFormat.noticeDate 가 서울 날짜로 만든다

    /** 대상 값 (DB check 제약과 같다) */
    public static final List<String> AUDIENCES = List.of("customer", "manager", "all");
    public static final int TITLE_MAX = 100;  // 제목 글자 수 상한 (코드에서 정함 — 목록 한 줄)
    public static final int BODY_MAX = 5000;  // 본문 글자 수 상한 (코드에서 정함)

    protected Notice() {} // JPA 가 DB 행을 객체로 바꿀 때 쓰는 빈 생성자

    /** 새 공지. 게시 시각 = 지금 (목록 맨 위). 값 검사는 AdminNoticeService 가 먼저 한다 */
    static Notice publish(String audience, String title, String body, Instant now) {
        Notice n = new Notice();
        n.id = UUID.randomUUID();
        n.audience = audience;
        n.title = title;
        n.body = body;
        n.publishedAt = now;
        return n;
    }

    /** 내용 고치기. 게시 시각(목록 순서·날짜)은 그대로 둔다 */
    void edit(String audience, String title, String body) {
        this.audience = audience;
        this.title = title;
        this.body = body;
    }

    public UUID getId() { return id; }
    public String getAudience() { return audience; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public Instant getPublishedAt() { return publishedAt; }

    /** 그 대상(customer|manager)이 볼 수 있는가 — all 은 둘 다 */
    public boolean visibleTo(String audience) {
        return "all".equals(this.audience) || this.audience.equals(audience);
    }
}
