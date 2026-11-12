package kr.ansimclean.api.profile;

import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.CurrentUserResolver;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * 요청마다 profiles(+ 매니저면 manager_profiles)를 읽어 CurrentUser 를 만든다. **캐시하지 않는다** — 역할·계정 상태 변경(정지·퇴사·역할 변경)이
 * 다음 요청부터 바로 반영돼야 한다(rbac.md §1-4, R4). 예전의 1분 캐시(CurrentUserCache)는 이 규칙 때문에 없앴다. 비용은 요청당 PK 조회 1~2회.
 * 부르는 곳: 인증 단계의 JwtToCurrentUserConverter 가 CurrentUserResolver 인터페이스로 resolve·isSessionRevoked 를 부른다.
 */
@Component
public class DbCurrentUserResolver implements CurrentUserResolver {
    private final ProfileService profiles;
    private final SessionRevocationService revocations;

    // @Lazy = 진짜 객체 대신 대리 객체를 넣어 두고 처음 쓸 때 연결한다 — 보안 설정(SecurityConfig)이 일찍 만들어질 때
    // 서비스 쪽 빈들이 아직 준비되지 않아도 생성 순서가 꼬이지 않게 (예전 CurrentUserCache 와 같은 방식)
    public DbCurrentUserResolver(@Lazy ProfileService profiles, @Lazy SessionRevocationService revocations) {
        this.profiles = profiles;
        this.revocations = revocations;
    }

    /** 사용자 id → CurrentUser(역할·가입 완료·매니저 상태). empty(프로필 없음·탈퇴)면 JwtToCurrentUserConverter 가 401 로 거절한다 */
    @Override
    public Optional<CurrentUser> resolve(UUID userId) {
        return profiles.currentUser(userId);
    }

    /** 서버가 끊은 세션인가 — SessionRevocationService 에 넘긴다 (그쪽이 메모리 목록을 1분마다 DB 에서 다시 읽는다) */
    @Override
    public boolean isSessionRevoked(String sessionId) {
        return revocations.isRevoked(sessionId);
    }
}
