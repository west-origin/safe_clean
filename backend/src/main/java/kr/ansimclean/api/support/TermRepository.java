package kr.ansimclean.api.support;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * terms 테이블 조회. 키 형은 두 열짜리 Term.Key (id, audience) — 본문은 findById(new Term.Key(id, audience)) 로 찾는다.
 * 쓰는 곳: TermsService
 */
public interface TermRepository extends JpaRepository<Term, Term.Key> {
    /** 대상(customer|manager)의 약관 전부, sort 순 (목록 응답·가입 때 동의 검사) */
    List<Term> findByAudienceOrderBySort(String audience);
}
