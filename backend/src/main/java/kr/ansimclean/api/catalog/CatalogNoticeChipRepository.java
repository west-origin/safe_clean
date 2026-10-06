package kr.ansimclean.api.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * catalog_notice_chips 테이블 접근 (서버 코드에서는 CatalogService 만 쓴다).
 * 기본키 형이 CatalogOption.Key 인 것은 CatalogNoticeChip 이 그 키 클래스를 함께 쓰기 때문이다.
 */
public interface CatalogNoticeChipRepository extends JpaRepository<CatalogNoticeChip, CatalogOption.Key> {
    /** 한 카테고리의 칩을 sort 순서로 (화면 순서). 모르는 카테고리면 빈 목록 */
    List<CatalogNoticeChip> findByCategoryKeyOrderBySort(String categoryKey);
}
