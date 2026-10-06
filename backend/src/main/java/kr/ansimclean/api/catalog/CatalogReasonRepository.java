package kr.ansimclean.api.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** catalog_reasons 테이블 접근 (서버 코드에서는 CatalogService 만 쓴다). 기본키 형은 복합 키 CatalogReason.Key */
public interface CatalogReasonRepository extends JpaRepository<CatalogReason, CatalogReason.Key> {
    /** 한 종류(reject·pause)의 사유를 sort 순서로 (시트에 보이는 순서) */
    List<CatalogReason> findByKindOrderBySort(String kind);
}
