package kr.ansimclean.api.job;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * job_pauses 테이블을 읽고 쓰는 곳 (CODE_GUIDE.md 3-1). 운영 코드에서는 JobService 만 쓴다.
 */
public interface JobPauseRepository extends JpaRepository<JobPause, UUID> {
    /** 한 작업의 중단 구간 전부, 멈춘 시각 순 — JobService 가 합계(pausedSeconds)와 열린 구간(openPause)을 여기서 뽑는다 */
    List<JobPause> findByBookingIdOrderByPausedAt(UUID bookingId);
}
