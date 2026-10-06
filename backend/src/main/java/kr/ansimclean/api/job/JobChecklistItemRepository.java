package kr.ansimclean.api.job;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * job_checklist_items 테이블을 읽고 쓰는 곳 (CODE_GUIDE.md 3-1).
 * 운영 코드에서는 이 패키지의 JobService 만 쓴다 (다른 기능은 JobService 를 부른다. 테스트는 직접 읽기도 한다).
 */
public interface JobChecklistItemRepository extends JpaRepository<JobChecklistItem, UUID> {
    /** 한 예약의 항목 전부, 표시 순서(sort)대로 — 진행 중 작업 화면(activeJob)과 완료 화면의 '몇 개 중 몇 개'(lastCompleted) */
    List<JobChecklistItem> findByBookingIdOrderBySort(UUID bookingId);

    /** 그 예약에 속한 항목일 때만 찾는다. 다른 작업의 itemId 면 비어 있어 404 가 된다 */
    Optional<JobChecklistItem> findByIdAndBookingId(UUID id, UUID bookingId);

    /** 한 예약의 항목을 모두 지우고 지운 행 수를 돌려준다 — 새로 복사하기 전(createChecklist)과 일정 변경·방문 취소로 배정이 풀릴 때(clearChecklist) */
    long deleteByBookingId(UUID bookingId);
}
