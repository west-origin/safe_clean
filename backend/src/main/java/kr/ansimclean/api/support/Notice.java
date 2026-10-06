package kr.ansimclean.api.support;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** 공지 (V004 seed). 운영자가 SQL 로 추가한다 — 앱 API 는 읽기만 */
// 읽는 곳: SupportService.notices·notice (고객 앱 공지사항, 매니저 앱 프로필 › 공지사항)
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

    protected Notice() {} // JPA 가 DB 행을 객체로 바꿀 때 쓰는 빈 생성자 — 코드에서 만들지 않는다

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
