package kr.ansimclean.api.profile;

import kr.ansimclean.api.common.time.AppClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 서버가 끊은 Supabase 세션 목록 (감사 M-03). 요청마다 DB 를 읽지 않도록 메모리에 두고 1분마다 DB 에서 다시 읽는다
 * (여러 대로 늘려도 다른 인스턴스의 로그아웃이 1분 안에 반영된다). 쓰기는 DB 와 메모리에 같이.
 * 인증 필터(CurrentUserCache.isSessionRevoked)와 /auth/refresh 가 본다 — 끊은 세션은 access 토큰도, refresh 로 새로 받은 토큰도(같은 session_id) 거부된다.
 */
// 부르는 곳 예: AuthService(로그아웃·비밀번호 재설정 때 revoke), AuthCleaner(매일 새벽 purgeExpired)
@Service
public class SessionRevocationService {
    /** refresh 토큰이 살아 있을 수 있는 기간만큼 둔다. Supabase 세션 비활성 만료를 이보다 짧게 설정한다 (runbook) */
    static final Duration KEEP = Duration.ofDays(90);
    static final Duration RELOAD = Duration.ofMinutes(1); // 메모리 목록을 DB 에서 다시 읽는 간격

    private final RevokedSessionRepository repository;
    private final AppClock clock;
    // session_id → 끊은 효력이 끝나는 시각. 여러 요청 스레드가 동시에 읽고 쓰므로 ConcurrentHashMap(동시 접근에 안전한 Map)
    private final Map<String, Instant> revoked = new ConcurrentHashMap<>();
    // 마지막으로 DB 에서 읽은 시각. EPOCH(1970년)로 시작해 첫 isRevoked 에서 바로 읽게 한다.
    // volatile: 한 스레드가 바꾼 값을 다른 스레드가 바로 보게 한다
    private volatile Instant loadedAt = Instant.EPOCH;

    public SessionRevocationService(RevokedSessionRepository repository, AppClock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** 끊는다. 이미 있으면 그대로 */
    // 메모리 목록(revoked)은 커밋 전에 바뀐다 — DB 저장이 실패해 되돌려져도 이 서버 메모리에는 끊긴 세션으로 남는다
    @Transactional
    public void revoke(UUID userId, Collection<String> sessionIds) {
        Instant now = clock.now();
        for (String sid : sessionIds) {
            // 빈 값은 건너뛰고, 이미 끊은 세션은 처음 끊은 기록(시각·만료)을 그대로 둔다
            if (sid == null || sid.isBlank() || repository.existsById(sid)) continue;
            repository.save(RevokedSession.of(sid, userId, now, now.plus(KEEP)));
            revoked.put(sid, now.plus(KEEP)); // 이 서버에는 바로 반영. 다른 서버는 다음 reload(1분 안)에 DB 에서 읽는다
        }
    }

    /** 끊은 세션인가 (요청마다 — 메모리 조회. 1분에 한 번 DB 에서 다시 읽는다) */
    @Transactional(readOnly = true)
    public boolean isRevoked(String sessionId) {
        if (sessionId == null) return false; // session_id 가 없으면 대조할 것이 없다 — 끊긴 것으로 보지 않는다
        Instant now = clock.now();
        if (loadedAt.plus(RELOAD).isBefore(now)) reload(now); // 마지막으로 읽은 지 1분이 넘었으면 DB 에서 다시 읽는다
        Instant until = revoked.get(sessionId);
        return until != null && until.isAfter(now); // 목록에 있어도 효력 기간이 지났으면 끊긴 것으로 보지 않는다
    }

    /**
     * DB 의 만료 전 목록을 메모리에 더한다. synchronized: 한 번에 한 스레드만 들어온다 —
     * 1분이 지난 순간 여러 요청이 동시에 와도 DB 는 한 번만 읽는다
     */
    private synchronized void reload(Instant now) {
        if (!loadedAt.plus(RELOAD).isBefore(now)) return; // 다른 스레드가 방금 읽었다
        Map<String, Instant> fresh = new ConcurrentHashMap<>();
        repository.findByExpiresAtAfter(now).forEach(r -> fresh.put(r.getSessionId(), r.getExpiresAt()));
        revoked.putAll(fresh); // 지우지는 않는다 — 방금 커밋 중인 revoke 를 놓치지 않게. 만료분은 isRevoked 가 시각으로 거른다

        loadedAt = now;
    }

    /** AuthCleaner 가 매일 */
    // 효력이 끝난 행을 DB 에서 지우고(지운 건수를 돌려준다), 메모리 목록에서도 같은 기준으로 뺀다
    @Transactional
    public int purgeExpired() {
        int n = repository.deleteExpired(clock.now());
        revoked.values().removeIf(until -> !until.isAfter(clock.now()));
        return n;
    }
}
