package kr.ansimclean.api.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

// 로그아웃·비밀번호 재설정으로 끊은 세션 한 건. SessionRevocationService 만 쓴다(저장·읽기·만료 삭제).
// 끊은 뒤에도 서명·만료가 맞는 토큰이 통과하지 않도록 이 목록과 토큰의 session_id 를 대조한다.
/** revoked_sessions — 서버가 끊은 Supabase 세션 (감사 M-03, V023) */
@Entity
@Table(name = "revoked_sessions")
public class RevokedSession {
    @Id // 기본 키 = Supabase 토큰 안의 session_id
    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
    private UUID userId;

    @Column(name = "revoked_at", nullable = false)
    private Instant revokedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt; // 이 시각이 지나면 목록에서 지워도 된다 (끊은 시각 + SessionRevocationService.KEEP)

    protected RevokedSession() {} // JPA 가 DB 행을 객체로 만들 때 쓰는 빈 생성자. 코드에서는 of 로 만든다

    /** 끊은 세션 한 건을 만든다 (저장은 부르는 쪽이 한다) */
    public static RevokedSession of(String sessionId, UUID userId, Instant now, Instant expiresAt) {
        RevokedSession r = new RevokedSession();
        r.sessionId = sessionId;
        r.userId = userId;
        r.revokedAt = now;
        r.expiresAt = expiresAt;
        return r;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
