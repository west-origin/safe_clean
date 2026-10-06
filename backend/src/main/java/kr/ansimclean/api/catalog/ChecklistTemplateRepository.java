package kr.ansimclean.api.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** checklist_templates 테이블 접근 (서버 코드에서는 CatalogService 만 쓴다). 기본키 형은 복합 키 ChecklistTemplate.Key */
public interface ChecklistTemplateRepository extends JpaRepository<ChecklistTemplate, ChecklistTemplate.Key> {
    /** 한 카테고리의 항목을 sort 순서로. 없으면 빈 목록 (그때 CatalogService 가 home 템플릿으로 대신한다) */
    List<ChecklistTemplate> findByCategoryKeyOrderBySort(String categoryKey);
}
