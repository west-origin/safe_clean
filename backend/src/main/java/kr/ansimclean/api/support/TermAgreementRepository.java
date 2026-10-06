package kr.ansimclean.api.support;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * term_agreements 테이블. 키 형은 두 열짜리 TermAgreement.Key 다.
 * 저장은 기본 메서드 save 로 한다 (TermsService.recordAgreements). 쓰는 곳: TermsService
 */
public interface TermAgreementRepository extends JpaRepository<TermAgreement, TermAgreement.Key> {
    /** 탈퇴 + 5년 파기 */
    // 이름 규칙 deleteBy…: 스프링이 그 사용자의 행을 읽어 하나씩 지우고 지운 개수를 돌려준다 (TermsService.forgetAgreements)
    long deleteByUserId(java.util.UUID userId);
}
