package kr.ansimclean.api.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * catalog_frequency_copy 테이블 접근 (서버 코드에서는 CatalogService 만 쓴다). 기본키 형은 복합 키 CatalogFrequencyCopy.Key.
 */
public interface CatalogFrequencyCopyRepository extends JpaRepository<CatalogFrequencyCopy, CatalogFrequencyCopy.Key> {
    /** 한 카테고리의 주기별 문구 전부 (순서 없음 — 순서는 CatalogService.confirmCopy 가 recurring → oneTime 으로 정한다) */
    List<CatalogFrequencyCopy> findByCategoryKey(String categoryKey);
}
