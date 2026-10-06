package kr.ansimclean.api.support;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** 자주 묻는 질문 (V004 seed) */
// 앱 API 는 읽기만 한다 (SupportService.faq → GET /support/faq). 행은 마이그레이션 SQL 로만 넣고 고친다
// (처음 넣은 것은 V004, 문구 수정 예: V027). 코드에서 새로 만들거나 저장하지 않는다.
@Entity
@Table(name = "faqs")
public class Faq {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(columnDefinition = "text", nullable = false)
    private String audience; // customer | manager (DB check 제약) — 어느 앱의 FAQ 인지

    @Column(columnDefinition = "text", nullable = false)
    private String question;

    @Column(columnDefinition = "text", nullable = false)
    private String answer;

    @Column(nullable = false)
    private int sort; // 목록 순서 (작은 값이 위)

    protected Faq() {} // JPA 가 DB 행을 객체로 바꿀 때 쓰는 빈 생성자

    public UUID getId() { return id; }
    public String getAudience() { return audience; }
    public String getQuestion() { return question; }
    public String getAnswer() { return answer; }
    public int getSort() { return sort; }
}
