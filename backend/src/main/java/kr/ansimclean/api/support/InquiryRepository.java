package kr.ansimclean.api.support;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * inquiries 테이블 조회·수정. 쓰는 곳: SupportService.
 * {@code @Query} 의 글은 JPQL — 테이블·열 이름 대신 엔티티(Inquiry)·필드(purgeAfter) 이름으로 쓰는 쿼리다.
 * {@code @Modifying}: 행을 하나씩 읽지 않고 update/delete 문 하나로 여러 행을 바꾼다. 반환값은 바뀐 행 수.
 */
public interface InquiryRepository extends JpaRepository<Inquiry, UUID> {
    /** 한 사용자의 문의, 최신순 (SupportService.myInquiries) */
    List<Inquiry> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /** 탈퇴: 이 사용자의 문의에 지울 시각을 적는다(이미 적힌 것은 두지 않는다) */
    // purge_after 가 이미 있는 행은 조건(is null)에서 빠져 그대로 남는다. 부르는 곳: SupportService.retainAfterDeletion
    @Modifying
    @Query("update Inquiry i set i.purgeAfter = :until where i.userId = :userId and i.purgeAfter is null")
    int retainUntil(UUID userId, Instant until);

    /** 탈퇴 + 5년 파기 — 3년 보관(purge_after)이 어떤 이유로 남긴 것까지 */
    // 이름 규칙 deleteBy…: 스프링이 그 사용자의 행을 읽어 하나씩 지우고, 지운 개수를 돌려준다 (SupportService.forgetInquiries)
    long deleteByUserId(UUID userId);

    /** 보관 기간이 지난 문의 삭제 (InquiryRetention, 매일) */
    // purge_after 가 null 인 행(탈퇴하지 않은 사용자의 문의)은 지우지 않는다
    @Modifying
    @Query("delete from Inquiry i where i.purgeAfter is not null and i.purgeAfter < :now")
    int deletePurgeable(Instant now);
}
