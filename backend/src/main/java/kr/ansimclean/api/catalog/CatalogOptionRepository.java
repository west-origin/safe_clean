package kr.ansimclean.api.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** catalog_options 테이블 접근 (서버 코드에서는 CatalogService 만 쓴다). 기본키 형은 복합 키 CatalogOption.Key */
public interface CatalogOptionRepository extends JpaRepository<CatalogOption, CatalogOption.Key> {
    /** 한 카테고리의 옵션(무료·유료 섞여서)을 sort 순서로. 무료·유료 나누기는 CatalogService 가 kind 로 한다 */
    List<CatalogOption> findByCategoryKeyOrderBySort(String categoryKey);
}
