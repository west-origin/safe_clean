package kr.ansimclean.api.common.security;

import java.util.Optional;
import java.util.UUID;

/**
 * JWT 의 sub(사용자 id) → CurrentUser. B2 에서 profile 패키지가 profiles 테이블로 구현한다(1분 캐시).
 * B1 의 기본 구현(모두 프로필 없음)은 없어졌다. resolve 가 empty 면 403 이 아니라 JwtToCurrentUserConverter 가 401 로 거절한다.
 * 지금 구현: profile 패키지의 CurrentUserCache (ProfileService.currentUser 를 1분 캐시). 부르는 곳: JwtToCurrentUserConverter.
 */
public interface CurrentUserResolver {
    // 사용자 id → CurrentUser. 프로필이 없거나 탈퇴했으면 empty → JwtToCurrentUserConverter 가 401 로 거절한다
    Optional<CurrentUser> resolve(UUID userId);

    /** 서버가 끊은 Supabase 세션(JWT session_id)인가 — 로그아웃·비밀번호 재설정 뒤 남은 토큰을 거부한다 (감사 M-03). 기본: 아니다 */
    default boolean isSessionRevoked(String sessionId) {
        return false;
    }
}
