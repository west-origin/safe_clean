package kr.ansimclean.api.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// 예약 카테고리 한 행. CatalogService.requireCategory 가 key 로 찾아 카테고리 검사·라벨 표시에 쓰고,
// GET /catalog/categories/{category} 응답(CategoryResponse)을 만든다. 값은 Flyway 마이그레이션으로 넣는다.
// 카테고리를 더할 때 가격표(catalog_prices)나 app_settings.duration_hours 칸을 빠뜨리면 견적·예약이 INTERNAL 로 실패하고,
// 체크리스트·짧은 라벨을 빠뜨리면 대체값(home 목록·전체 라벨)이 나간다 (PricingService·CatalogService 참고).
/** service_categories — 예약 카테고리 (V002 의 청소 home · office · appliance · move-in, V012 의 수리 repair). 읽기 전용 참조 데이터 */
@Entity
@Table(name = "service_categories")
public class ServiceCategory {
    @Id
    @Column(name = "key", columnDefinition = "text")
    private String key; // URL·예약에 쓰는 카테고리 이름 (예: 'home', 'move-in')

    @Column(columnDefinition = "text", nullable = false)
    private String label; // 화면에 보이는 이름 (예: '가정/원룸 청소')

    @Column(columnDefinition = "text", nullable = false)
    private String promo; // 배너 종류 'recurring'·'placeholder' (DB check 제약). 고객 앱 주기 선택 화면이 배너를 고른다

    @Column(name = "promo_text", columnDefinition = "text")
    private String promoText; // placeholder 카테고리의 배너 자리 설명 글자, 없으면 null. 지금 고객 앱 화면은 쓰지 않는다

    @Column(nullable = false)
    private int sort;

    /** JPA 가 DB 행으로 객체를 만들 때 쓰는 빈 생성자 */
    protected ServiceCategory() {}

    public String getKey() { return key; }
    public String getLabel() { return label; }
    public String getPromo() { return promo; }
    public String getPromoText() { return promoText; }
    public int getSort() { return sort; }
}
