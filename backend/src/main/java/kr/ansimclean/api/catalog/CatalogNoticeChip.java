package kr.ansimclean.api.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

// 고객이 예약 때 고르는 주의사항 칩 한 개. CatalogService 가 칩 목록 응답(notice-chips), 예약 생성 때의 key 검증,
// 콜 카드·작업 화면에 보일 key → 라벨 변환에 쓴다. 읽기 전용 참조 데이터이고 값은 Flyway 마이그레이션으로 넣는다.
// 기본키가 (category_key, key) 두 칸이라 @IdClass 로 키 클래스를 지정한다. 필드 이름이 CatalogOption.Key 와
// 똑같아서 그 클래스를 함께 쓴다(JPA 는 키 클래스의 필드 이름으로 @Id 필드와 짝을 맞춘다).
/** catalog_notice_chips — 카테고리별 주의사항 칩 (카테고리마다 개수가 다르다 — 예: 수리는 청소보다 적다). icon 은 앱 NoticeChip 아이콘 키 */
@Entity
@Table(name = "catalog_notice_chips")
@IdClass(CatalogOption.Key.class)
public class CatalogNoticeChip {
    @Id
    @Column(name = "category_key", columnDefinition = "text")
    private String categoryKey;

    @Id
    @Column(name = "key", columnDefinition = "text")
    private String key; // 예약의 notices 목록에 저장되는 값 (예: 'pet')

    @Column(columnDefinition = "text", nullable = false)
    private String label;

    @Column(columnDefinition = "text", nullable = false)
    private String icon;

    @Column(nullable = false)
    private int sort; // 화면에 보이는 순서 (작은 값이 먼저)

    /** JPA 가 DB 행으로 객체를 만들 때 쓰는 빈 생성자 */
    protected CatalogNoticeChip() {}

    public String getCategoryKey() { return categoryKey; }
    public String getKey() { return key; }
    public String getLabel() { return label; }
    public String getIcon() { return icon; }
    public int getSort() { return sort; }
}
