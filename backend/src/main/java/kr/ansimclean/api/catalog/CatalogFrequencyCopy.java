package kr.ansimclean.api.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

// CatalogService.confirmCopy 가 카테고리의 행을 읽어 GET /catalog/categories/{category}/confirm-copy 의
// frequencyDescriptions 로 내보낸다. 읽기 전용 참조 데이터이고 값은 Flyway 마이그레이션으로 넣는다.
// @IdClass → 기본키가 두 칸(category_key + frequency)이라 그 두 칸을 묶는 Key 클래스를 지정한다.
/** catalog_frequency_copy — 확인 화면 "서비스 주기" 부가 문구 (카테고리 × 주기) */
@Entity
@Table(name = "catalog_frequency_copy")
@IdClass(CatalogFrequencyCopy.Key.class)
public class CatalogFrequencyCopy {
    @Id
    @Column(name = "category_key", columnDefinition = "text")
    private String categoryKey;

    @Id
    @Column(columnDefinition = "text")
    private String frequency; // 'recurring'(정기) 또는 'oneTime'(1회)

    @Column(columnDefinition = "text", nullable = false)
    private String description;

    /** JPA 가 DB 행으로 객체를 만들 때 쓰는 빈 생성자 */
    protected CatalogFrequencyCopy() {}

    public String getCategoryKey() { return categoryKey; }
    public String getFrequency() { return frequency; }
    public String getDescription() { return description; }

    /**
     * 복합 기본키 (category_key, frequency). 필드 이름이 위 @Id 필드 이름과 같아야 JPA 가 짝을 맞춘다.
     * JPA 가 키끼리 같은지 비교하므로 equals·hashCode 를 두 칸 모두로 정의한다.
     */
    public static class Key implements Serializable {
        private String categoryKey;
        private String frequency;

        public Key() {} // JPA 가 쓰는 빈 생성자

        public Key(String categoryKey, String frequency) {
            this.categoryKey = categoryKey;
            this.frequency = frequency;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(categoryKey, k.categoryKey) && Objects.equals(frequency, k.frequency);
        }

        @Override
        public int hashCode() {
            return Objects.hash(categoryKey, frequency);
        }
    }
}
