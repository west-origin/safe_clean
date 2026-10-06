package kr.ansimclean.api.support;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** 1:1 문의 (고객 설정 › 1:1 문의, 매니저 프로필 › 고객센터). 답변은 운영자가 관리자 API(AdminInquiryController)로 — SQL 로 직접 적으면 푸시가 안 나간다. 답변은 두 앱 문의 화면 위쪽 '내 문의'(MyInquiries)에서 본다 — Figma 에 없어 코드에서 정함 */
// 흐름:
//  - 접수: SupportService.submit 이 Inquiry.open 으로 만들어 저장한다 (status = open)
//  - 답변: 관리자 API(AdminInquiryController → SupportService.answer)가 answer 메서드로 적는다 (status = answered)
//  - 탈퇴: purge_after 에 탈퇴 + 3년을 적고(InquiryRepository.retainUntil), 매일 InquiryRetention 이 기한 지난 행을 지운다.
//    그래도 남은 문의(기한이 안 적힌 것)는 탈퇴 5년 파기(DeletedAccountPurger → SupportService.forgetInquiries)가 지운다
@Entity
@Table(name = "inquiries")
@org.hibernate.annotations.DynamicUpdate // 바뀐 열만 쓴다 — 운영자 답변이 읽을 때의 purge_after(NULL)까지 다시 써서, 그 사이 탈퇴가 적은 보관 기한을 지우지 않게 (자체 점검 후속 검토)
public class Inquiry {
    public static final int MAX_LENGTH = 2000; // 문의 글자 수 상한 (SupportService.submit, InquiryRequest 의 @Size 와 같다)

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
    private UUID userId;

    @Column(columnDefinition = "text", nullable = false)
    private String content;

    @Column(columnDefinition = "text", nullable = false)
    private String status = "open"; // open(답변 대기) | answered (DB check 제약)

    @Column(columnDefinition = "text")
    private String answer; // 답변 전에는 null

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "answered_at")
    private Instant answeredAt;

    /** 탈퇴한 사용자의 문의를 지울 시각(탈퇴 + 3년). 살아 있는 계정이면 null (V038, 자체 점검 #31) */
    @Column(name = "purge_after")
    private Instant purgeAfter;

    protected Inquiry() {} // JPA 전용 빈 생성자 — 새 문의는 open(…)으로 만든다

    /** 새 문의(status = open). id 는 여기서 만든다. 저장(save)은 부르는 쪽(SupportService.submit)이 한다 */
    public static Inquiry open(UUID userId, String content, Instant now) {
        Inquiry i = new Inquiry();
        i.id = UUID.randomUUID();
        i.userId = userId;
        i.content = content;
        i.createdAt = now;
        return i;
    }

    /** 운영자 답변 — 두 번 답하면 덮어쓴다(status 는 answered 그대로) */
    public void answer(String text, Instant now) {
        this.answer = text;
        this.answeredAt = now;
        this.status = "answered";
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getContent() { return content; }
    public String getStatus() { return status; }
    public String getAnswer() { return answer; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPurgeAfter() { return purgeAfter; }
    public Instant getAnsweredAt() { return answeredAt; }
}
