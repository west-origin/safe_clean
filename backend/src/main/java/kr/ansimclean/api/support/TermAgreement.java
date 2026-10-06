package kr.ansimclean.api.support;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** term_agreements — 누가 어떤 약관(버전)에 언제 동의했는지 */
// 기본 키가 (user_id, term_id) 두 열이라 @IdClass 로 키 클래스(아래 Key)를 따로 둔다 — 한 사용자는 약관 하나에 한 줄.
// 약관(Term)과 달리 audience 가 키에 없다 — 가입 때 그 계정 역할의 약관만 받기 때문이다 (recordAgreements 의 audience = profiles.role).
// 가입 완료 때 TermsService.recordAgreements 가 동의한 약관마다 저장하고, 탈퇴 5년 뒤 TermsService.forgetAgreements 가 지운다.
@Entity
@Table(name = "term_agreements")
@IdClass(TermAgreement.Key.class)
public class TermAgreement {
    @Id
    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Id
    @Column(name = "term_id", columnDefinition = "text")
    private String termId;

    @Column(nullable = false)
    private int version;

    @Column(name = "agreed_at", nullable = false)
    private Instant agreedAt;

    protected TermAgreement() {} // JPA 전용 빈 생성자 — 새 기록은 of(…)로 만든다

    /** 동의 한 건을 만든다 (저장은 부르는 쪽 TermsService.recordAgreements). version 은 그때의 약관 판 번호 */
    public static TermAgreement of(UUID userId, String termId, int version, Instant now) {
        TermAgreement a = new TermAgreement();
        a.userId = userId;
        a.termId = termId;
        a.version = version;
        a.agreedAt = now;
        return a;
    }

    public UUID getUserId() { return userId; }
    public String getTermId() { return termId; }
    public int getVersion() { return version; }

    /** 두 열 기본 키 (userId, termId). 필드 이름이 위 @Id 필드와 같아야 JPA 가 짝을 맞춘다 */
    public static class Key implements Serializable {
        private UUID userId;
        private String termId;

        public Key() {} // JPA 가 쓰는 빈 생성자

        public Key(UUID userId, String termId) {
            this.userId = userId;
            this.termId = termId;
        }

        // equals·hashCode: 두 값(userId·termId)이 같으면 같은 키로 본다 — JPA 가 키로 행을 찾고 구분할 때 쓴다
        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userId, k.userId) && Objects.equals(termId, k.termId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, termId);
        }
    }
}
