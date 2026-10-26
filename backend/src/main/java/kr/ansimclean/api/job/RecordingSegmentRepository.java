package kr.ansimclean.api.job;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RecordingSegmentRepository extends JpaRepository<RecordingSegment, UUID> {
    Optional<RecordingSegment> findFirstByLiveSessionIdOrderByEndAtDesc(UUID sessionId);

    /** 체크 해제는 학습 라벨을 무효화하되 원래 시간 경계를 보존한다. */
    @Modifying
    @Query("update RecordingSegment s set s.reviewStatus = 'revoked', s.updatedAt = :at where s.liveSessionId = :session and s.itemId = :item and s.reviewStatus <> 'revoked'")
    void revoke(@Param("session") UUID session, @Param("item") UUID item, @Param("at") Instant at);
}
