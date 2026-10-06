package kr.ansimclean.api.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

// 사유 문구 한 줄. CatalogService 가 GET /catalog/reasons 응답(매니저 앱 거절·일시정지 시트)을 만들고,
// 콜 거절(OfferService)·작업 중단(JobService) 요청의 사유가 이 목록에 있는지 isReason 으로 확인한다.
// 따로 key 가 없어서 (kind, label) 두 칸이 기본키다 — 문구 자체가 요청에 실려 오는 값이다.
/** catalog_reasons — 콜 거절(reject)·촬영 중단(pause) 사유 (Figma 문구) */
@Entity
@Table(name = "catalog_reasons")
@IdClass(CatalogReason.Key.class)
public class CatalogReason {
    @Id
    @Column(columnDefinition = "text")
    private String kind; // 'reject' 또는 'pause' (DB check 제약)

    @Id
    @Column(columnDefinition = "text")
    private String label;

    @Column(nullable = false)
    private int sort; // 시트에 보이는 순서 (작은 값이 먼저)

    /** JPA 가 DB 행으로 객체를 만들 때 쓰는 빈 생성자 */
    protected CatalogReason() {}

    public String getKind() { return kind; }
    public String getLabel() { return label; }
    public int getSort() { return sort; }

    /** 복합 기본키 (kind, label). 필드 이름이 위 @Id 필드와 같아야 하고, equals·hashCode 는 두 칸 모두로 비교한다 */
    public static class Key implements Serializable {
        private String kind;
        private String label;

        public Key() {} // JPA 가 쓰는 빈 생성자

        public Key(String kind, String label) {
            this.kind = kind;
            this.label = label;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(kind, k.kind) && Objects.equals(label, k.label);
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, label);
        }
    }
}
