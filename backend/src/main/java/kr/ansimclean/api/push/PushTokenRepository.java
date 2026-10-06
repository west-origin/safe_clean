package kr.ansimclean.api.push;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

// push_tokens 테이블을 읽고 지우는 곳. 다른 기능은 이것을 직접 쓰지 않고 PushService 를 거친다 (CODE_GUIDE.md 3-1).
// 기본 메서드(findById·deleteById 등)의 키 타입은 PushToken.Key (두 칼럼 키).
public interface PushTokenRepository extends JpaRepository<PushToken, PushToken.Key> {
    // user_id 가 같은 행 = 한 사용자의 모든 기기 — 예: PushService.send
    List<PushToken> findByUserId(UUID userId);

    // 아래 삭제 쿼리들은 JPQL(nativeQuery 가 아님)이라 테이블·칼럼이 아니라 엔티티 이름(PushToken)·필드 이름으로 쓴다.
    // 반환값은 지운 행 수
    /** 같은 토큰이 다른 사용자에게 남아 있으면(기기 공유·재로그인) 그쪽에서 지운다 — 앞 사람의 푸시가 새 사용자 기기로 가지 않게 */
    @Modifying
    @Query("delete from PushToken t where t.token = :token and t.userId <> :userId")
    int deleteByTokenAndUserIdNot(String token, UUID userId);

    // 이 토큰을 누구에게 등록돼 있든 지운다. 지금 backend/src 안에는 이 메서드를 부르는 곳이 없다
    @Modifying
    @Query("delete from PushToken t where t.token = :token")
    int deleteByToken(String token);

    // 한 사용자의 토큰 전부 — 예: PushService.forget (탈퇴·비밀번호 재설정)
    @Modifying
    @Query("delete from PushToken t where t.userId = :userId")
    int deleteByUserId(UUID userId);
}
