package kr.ansimclean.api.job;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** 추가·조회만 공개한다. 호출자는 job 패키지의 JobService뿐이다. */
public interface JobChecklistEventRepository extends Repository<JobChecklistEvent, UUID> {
    JobChecklistEvent save(JobChecklistEvent event);

    // 시각이 같아도 DB 저장 순서로 마지막 체크·해제를 고른다. 폴링 때 전체 이력을 읽지 않는다.
    @Query(value = "select distinct on (item_id) * from job_checklist_events where booking_id = :bookingId order by item_id, event_order desc", nativeQuery = true)
    List<JobChecklistEvent> findLatestByBookingId(@Param("bookingId") UUID bookingId);
}
