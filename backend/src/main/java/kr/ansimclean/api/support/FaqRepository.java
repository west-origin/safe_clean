package kr.ansimclean.api.support;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** faqs 테이블 조회. 쓰는 곳: SupportService.faq */
public interface FaqRepository extends JpaRepository<Faq, UUID> {
    /** 대상(customer|manager)의 FAQ 를 sort 오름차순으로 */
    List<Faq> findByAudienceOrderBySort(String audience);
}
