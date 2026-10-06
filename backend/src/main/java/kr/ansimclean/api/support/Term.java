package kr.ansimclean.api.support;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/** terms — 약관 (audience: customer | manager). 문안은 법무 검토 후 새 마이그레이션으로 교체 */
// 기본 키가 (id, audience) 두 열이다 — 같은 id(예: service·privacy)가 고객용·매니저용으로 따로 있을 수 있다(V003).
// 두 열 키는 @IdClass 로 키 클래스(아래 Key)를 따로 둔다.
// 읽는 곳: TermsService (목록·본문·가입 때 필수 약관 검사). 코드에서 새로 만들거나 저장하지 않는다.
@Entity
@Table(name = "terms")
@IdClass(Term.Key.class)
public class Term {
    @Id
    @Column(name = "id", columnDefinition = "text")
    private String id;

    @Id
    @Column(columnDefinition = "text")
    private String audience;

    @Column(columnDefinition = "text", nullable = false)
    private String label; // 동의 화면의 한 줄 "(필수) 서비스 이용약관". 본문 화면 제목은 title

    @Column(columnDefinition = "text", nullable = false)
    private String title;

    @Column(columnDefinition = "text", nullable = false)
    private String body;

    @Column(nullable = false)
    private boolean required; // true 면 가입 때 동의가 꼭 필요하다 (TermsService.recordAgreements 가 검사)

    @Column(nullable = false)
    private int version; // 문안 판 번호. 동의 기록(TermAgreement)에 그때의 값이 함께 저장된다

    @Column(nullable = false)
    private int sort; // 목록 순서 (작은 값이 위)

    protected Term() {} // JPA 가 DB 행을 객체로 바꿀 때 쓰는 빈 생성자

    public String getId() { return id; }
    public String getAudience() { return audience; }
    public String getLabel() { return label; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public boolean isRequired() { return required; }
    public int getVersion() { return version; }
    public int getSort() { return sort; }

    /**
     * 두 열 기본 키 (id, audience). 필드 이름이 위 @Id 필드와 같아야 JPA 가 짝을 맞춘다.
     * 본문 조회는 terms.findById(new Term.Key(id, audience)) (TermsService.term).
     */
    public static class Key implements Serializable {
        private String id;
        private String audience;

        public Key() {} // JPA 가 쓰는 빈 생성자

        public Key(String id, String audience) {
            this.id = id;
            this.audience = audience;
        }

        // equals·hashCode: 두 값(id·audience)이 같으면 같은 키로 본다 — JPA 가 키로 행을 찾고 구분할 때 쓴다.
        // o instanceof Key k: o 가 Key 면 k 라는 이름으로 바로 꺼내 쓴다
        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(id, k.id) && Objects.equals(audience, k.audience);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, audience);
        }
    }
}
