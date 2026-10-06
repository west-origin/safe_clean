package kr.ansimclean.api.profile;

import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.time.AppClock;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 1분 캐시 — 읽는 도중 비워지면(탈퇴 커밋) 읽은 옛 값을 넣지 않는다 (자체 점검 #54) */
// CurrentUserCache(요청마다 사용자 정보를 DB 대신 1분 캐시에서 꺼내는 곳) 단위 테스트. 스프링 없이 객체를 직접 만든다.
// 확인하는 것: ① 1분 안에는 DB(ProfileService)를 다시 읽지 않는다
// ② 읽는 도중 evict(탈퇴 등으로 캐시 비우기)가 끼어들면 방금 읽은 옛 값을 캐시에 넣지 않는다 — 탈퇴한 계정 토큰이 1분간 통과하던 문제
class CurrentUserCacheTest {
    static final UUID USER = UUID.randomUUID();
    ProfileService profiles = mock(ProfileService.class);
    // 시계를 한 시각에 고정한다(Clock.fixed) — 시간이 흐르지 않으니 두 번째 resolve 도 늘 1분 안이다.
    // SessionRevocationService 는 이 테스트에서 쓰지 않아 빈 가짜만 넣는다
    CurrentUserCache cache = new CurrentUserCache(profiles, new AppClock(Clock.fixed(Instant.parse("2026-09-27T05:00:00Z"), AppClock.ZONE)), mock(SessionRevocationService.class));

    // 같은 사용자를 두 번 resolve → ProfileService.currentUser 는 한 번만 불려야 한다 (두 번째는 캐시에서)
    @Test
    void 캐시된_값은_1분_동안_다시_읽지_않는다() {
        when(profiles.currentUser(USER)).thenReturn(Optional.of(new CurrentUser(USER, "customer", true)));
        cache.resolve(USER);
        cache.resolve(USER);
        verify(profiles, times(1)).currentUser(USER);
    }

    // 준비: 첫 조회는 thenAnswer(불릴 때 람다를 실행)로 '조회 도중 evict' 를 흉내 내고 살아 있는 사용자를 돌려준다.
    // 두 번째 조회는 thenReturn 으로 empty(탈퇴됨). 확인: 두 번째 resolve 가 캐시가 아니라 DB 를 다시 읽어 empty 를 받는다
    @Test
    void 읽는_사이_탈퇴가_커밋돼_비워지면_읽은_살아_있음을_캐시하지_않는다() {
        when(profiles.currentUser(USER)).thenAnswer(inv -> {
            cache.evict(USER); // 조회가 끝나기 전에 탈퇴 트랜잭션이 커밋되고 캐시를 비웠다
            return Optional.of(new CurrentUser(USER, "customer", true));
        }).thenReturn(Optional.empty());
        assertThat(cache.resolve(USER)).isPresent(); // 이 요청은 이미 통과했다
        assertThat(cache.resolve(USER)).isEmpty(); // 다음 요청은 DB 를 다시 읽는다 — 예전: 1분 동안 '살아 있음'
        verify(profiles, times(2)).currentUser(USER);
    }
}
