package kr.ansimclean.api.common.activity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

// activity_logs 저장소. ActivityLogService 만 쓴다 (다른 기능은 서비스를 부른다)
public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {
    /** 한 대상의 이력, 최근 것부터 (어드민 조회·테스트용) */
    List<ActivityLog> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, String targetId);
}
