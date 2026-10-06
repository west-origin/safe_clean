package kr.ansimclean.api.support;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/** notices 테이블 조회. 쓰는 곳: SupportService.notices·notice */
public interface NoticeRepository extends JpaRepository<Notice, UUID> {
    /** 대상 + all, 최신순 (api.md §5.5) */
    // @Query 의 글은 JPQL(엔티티·필드 이름으로 쓰는 쿼리). 게시 시각이 같으면 id 순으로 둬서 목록 순서가 매번 같다
    @Query("select n from Notice n where n.audience = :audience or n.audience = 'all' order by n.publishedAt desc, n.id")
    List<Notice> findVisible(String audience);
}
