package kr.ansimclean.api.profile;

import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.CurrentUserResolver;
import kr.ansimclean.api.common.time.AppClock;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 요청마다 profiles 를 읽지 않도록 1분 캐시 (BACKEND_PLAN.md §7.2). 프로필이 바뀌면 ProfileService 가 evict 한다.
 * 부정 결과(프로필 없음)도 같은 1분 동안 캐시한다 — 가입 직후 첫 요청이 옛 값을 보지 않게 evict 로 처리한다.
 * 부르는 곳: 인증 단계의 JwtToCurrentUserConverter 가 요청마다 CurrentUserResolver 인터페이스로 resolve·isSessionRevoked 를 부른다.
 * 주의: 메모리 캐시라 evict 는 그 서버에만 듣는다. 서버를 여러 대 띄우면 다른 서버는 탈퇴·가입 완료를 최대 1분(TTL) 늦게 본다.
 * 붙은 @Primary = CurrentUserResolver 구현이 더 생겨도 스프링이 이것을 넣는다.
 */
@Component
@Primary
public class CurrentUserCache implements CurrentUserResolver {
    private static final Duration TTL = Duration.ofMinutes(1); // 한 번 읽은 결과를 재사용하는 시간 (있음·없음 모두 같은 1분)

    // 캐시 한 칸: 읽은 결과(empty = 프로필 없음·탈퇴)와 읽은 시각
    private record Entry(Optional<CurrentUser> user, Instant at) {}

    // 사용자 id → 캐시 한 칸. ConcurrentHashMap = 여러 요청 스레드가 동시에 읽고 써도 안전한 Map
    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();
    /** 사용자별 비운 횟수 — 읽는 도중 비워졌으면(탈퇴 커밋 등) 읽은 옛 값을 캐시하지 않는다 (자체 점검 #54) */
    private final Map<UUID, Long> generations = new ConcurrentHashMap<>();
    private final ProfileService profiles;
    private final AppClock clock;

    private final SessionRevocationService revocations;

    // @Lazy = 진짜 객체 대신 대리 객체를 넣어 두고 처음 쓸 때 연결한다.
    // ProfileService 는 이 캐시를 생성자로 받으므로, 여기서 ProfileService 를 바로 받으면 서로를 먼저 만들어야 해 생성이 꼬인다
    public CurrentUserCache(@Lazy ProfileService profiles, AppClock clock, @Lazy SessionRevocationService revocations) {
        this.profiles = profiles;
        this.clock = clock;
        this.revocations = revocations;
    }

    /** 서버가 끊은 세션인가 — 캐시하지 않고 SessionRevocationService 에 넘긴다 (그쪽이 메모리 목록을 1분마다 DB 에서 다시 읽는다) */
    @Override
    public boolean isSessionRevoked(String sessionId) {
        return revocations.isRevoked(sessionId);
    }

    /**
     * 사용자 id → CurrentUser(id·역할·가입 완료 여부). 1분 안에 읽은 값이 있으면 DB 를 읽지 않고 그대로 돌려준다.
     * empty 면 JwtToCurrentUserConverter 가 401 로 거절한다. 캐시가 없거나 오래됐으면 ProfileService.currentUser 로 새로 읽는다
     */
    @Override
    public Optional<CurrentUser> resolve(UUID userId) {
        Instant now = clock.now();
        Entry e = entries.get(userId);
        if (e != null && e.at().plus(TTL).isAfter(now)) return e.user(); // 아직 1분이 안 지났다
        long gen = generations.getOrDefault(userId, 0L); // 읽기 전 세대 번호 — 아래에서 읽는 도중 비워졌는지 비교한다
        Optional<CurrentUser> user = profiles.currentUser(userId);
        // 탈퇴 커밋과 겹쳐 읽은 '살아 있음' 을 커밋 뒤에 1분 동안 캐시하면 탈퇴한 계정의 토큰이 계속 통과했다 — 그 사이 비워졌으면 넣지 않는다
        // compute = 이 키의 값을 다른 스레드와 겹치지 않게 새로 정한다. 세대가 그대로면 새 값, 달라졌으면 지금 있는 값(old)을 그대로 둔다
        entries.compute(userId, (k, old) -> generations.getOrDefault(userId, 0L) == gen ? new Entry(user, now) : old);
        return user;
    }

    /**
     * 지금 비우고, 트랜잭션 안이면 **커밋 뒤에 한 번 더** 비운다 — evict 와 커밋 사이에 들어온 요청이 옛 값(가입 미완료·탈퇴 전)을 다시 캐시하는 창을 닫는다.
     * 부르는 곳: ProfileService 의 가입·주소·주거·탈퇴를 바꾸는 메서드 (예: completeSignup, anonymize).
     * 새로 그런 메서드를 만들 때 evict 를 빠뜨리면, 이 서버는 최대 1분 동안 옛 값(예: 탈퇴 전 '살아 있음')으로 인증한다
     */
    public void evict(UUID userId) {
        clear(userId);
        // 트랜잭션이 진행 중이면 '커밋 직후 할 일' 을 등록한다 (afterCommit). 롤백되면 불리지 않는다
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    clear(userId);
                }
            });
        }
    }

    // 세대 번호를 1 올리고(merge: 없으면 1, 있으면 더하기) 캐시 칸을 지운다
    private void clear(UUID userId) {
        generations.merge(userId, 1L, Long::sum); // 먼저 올린다 — 진행 중인 resolve 가 옛 값을 넣지 못하게
        entries.remove(userId);
    }
}
