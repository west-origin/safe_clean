package kr.ansimclean.api.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * catalog_prices 테이블 접근 (서버 코드에서는 PricingService 만 쓴다). 따로 선언한 메서드는 없고,
 * JpaRepository 가 자동으로 만들어 주는 findById(new CatalogPrice.Key(카테고리, 주기)) 만 쓴다.
 */
public interface CatalogPriceRepository extends JpaRepository<CatalogPrice, CatalogPrice.Key> {
}
