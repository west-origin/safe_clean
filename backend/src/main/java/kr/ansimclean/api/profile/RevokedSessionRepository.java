package kr.ansimclean.api.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

// revoked_sessions 테이블 접근 (키 = session_id 문자열). SessionRevocationService 만 쓴다.
public interface RevokedSessionRepository extends JpaRepository<RevokedSession, String> {
    // 아직 만료되지 않은 끊은 세션 전부 (expires_at > now). 1분마다 메모리 목록을 다시 채울 때 쓴다 (SessionRevocationService.reload)
    List<RevokedSession> findByExpiresAtAfter(Instant now);

    // 만료된 행(expires_at < now)을 한 번에 지우고 지운 건수를 돌려준다.
    // 트랜잭션 안에서 불러야 한다 (SessionRevocationService.purgeExpired 가 @Transactional)
    @Modifying
    @Query("delete from RevokedSession r where r.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
