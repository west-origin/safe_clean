package kr.ansimclean.api.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * service_categories 테이블 접근 (서버 코드에서는 CatalogService 만 쓴다). 기본키 형은 카테고리 key(String).
 * 따로 선언한 메서드는 없고 JpaRepository 가 만들어 주는 findById 만 쓴다.
 */
public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, String> {}
