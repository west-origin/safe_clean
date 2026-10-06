package kr.ansimclean.api.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

// 방문 한 번의 기본 금액 (정기청소도 회당 금액). PricingService 가 (카테고리, 주기) 로 한 행을 찾아 견적의 base 로 쓴다.
// 예약 때 이 값을 예약 행에 고정하므로(CODE_GUIDE.md 3-8) 가격을 바꿔도 이미 만든 예약·콜·정산 금액은 그대로다.
// 읽기 전용 참조 데이터이고 값은 Flyway 마이그레이션으로 넣는다(수리 카테고리는 0원 — 현장 진단 후 확정).
// 기본키가 (category_key, frequency) 두 칸이라 @IdClass 로 아래 Key 클래스를 지정한다.
/** catalog_prices — 카테고리 × 주기별 기본 금액 (원). 결정 §13-3 임시 가격표 */
@Entity
@Table(name = "catalog_prices")
@IdClass(CatalogPrice.Key.class)
public class CatalogPrice {
    @Id
    @Column(name = "category_key", columnDefinition = "text")
    private String categoryKey;

    @Id
    @Column(columnDefinition = "text")
    private String frequency; // 'recurring'(정기) 또는 'oneTime'(1회) (DB check 제약)

    @Column(name = "base_amount", nullable = false)
    private long baseAmount; // 원

    /** JPA 가 DB 행으로 객체를 만들 때 쓰는 빈 생성자 */
    protected CatalogPrice() {}

    public String getCategoryKey() { return categoryKey; }
    public String getFrequency() { return frequency; }
    public long getBaseAmount() { return baseAmount; }

    /**
     * 복합 기본키 (category_key, frequency). PricingService 가 new Key(카테고리, 주기) 로 findById 에 넘긴다.
     * 필드 이름이 위 @Id 필드와 같아야 하고, equals·hashCode 는 두 칸 모두로 비교한다.
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
