package kr.ansimclean.api.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

// 예약 옵션 한 개. CatalogService 가 옵션 목록 응답(options)·예약 때의 옵션 검증·key → 라벨 변환에 쓰고,
// PricingService 가 유료 옵션 가격을 더한다. 읽기 전용 참조 데이터이고 값은 Flyway 마이그레이션으로 넣는다.
// 기본키가 (category_key, key) 두 칸이라 @IdClass 로 아래 Key 클래스를 지정한다.
/** catalog_options — 카테고리별 무료(free)·유료(paid) 옵션. icon 은 앱 ASSETS.bookingOptionIcons[category][icon] */
@Entity
@Table(name = "catalog_options")
@IdClass(CatalogOption.Key.class)
public class CatalogOption {
    @Id
    @Column(name = "category_key", columnDefinition = "text")
    private String categoryKey;

    @Id
    @Column(name = "key", columnDefinition = "text")
    private String key;

    @Column(columnDefinition = "text", nullable = false)
    private String kind; // 'free' 또는 'paid' (DB check 제약)

    @Column(columnDefinition = "text", nullable = false)
    private String label;

    @Column(nullable = false)
    private long price; // 원. 무료 옵션은 0

    @Column(nullable = false)
    private short icon; // DB smallint. 앱의 옵션 아이콘 목록에서 몇 번째인지

    @Column(nullable = false)
    private int sort; // 화면에 보이는 순서 (작은 값이 먼저)

    /** JPA 가 DB 행으로 객체를 만들 때 쓰는 빈 생성자 */
    protected CatalogOption() {}

    public String getCategoryKey() { return categoryKey; }
    public String getKey() { return key; }
    public String getKind() { return kind; }
    public String getLabel() { return label; }
    public long getPrice() { return price; }
    public short getIcon() { return icon; }
    public int getSort() { return sort; }

    /** 무료 옵션인가. 옵션 검증(무료 칸에 유료 key 를 넣으면 거절)과 가격 합산(무료는 빼고 더한다)에서 쓴다 */
    public boolean isFree() { return "free".equals(kind); }

    /**
     * 복합 기본키 (category_key, key). 필드 이름이 위 @Id 필드 이름과 같아야 JPA 가 짝을 맞춘다.
     * CatalogNoticeChip 도 같은 두 칸이라 이 클래스를 키로 쓴다. equals·hashCode 는 두 칸 모두로 비교한다.
     */
    public static class Key implements Serializable {
        private String categoryKey;
        private String key;

        public Key() {} // JPA 가 쓰는 빈 생성자

        public Key(String categoryKey, String key) {
            this.categoryKey = categoryKey;
            this.key = key;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(categoryKey, k.categoryKey) && Objects.equals(key, k.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(categoryKey, key);
        }
    }
}
