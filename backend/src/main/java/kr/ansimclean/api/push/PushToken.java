package kr.ansimclean.api.push;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

// push_tokens 테이블 한 행 = 한 사용자의 한 기기 토큰. PushService 가 만들고·바꾸고·지운다.
// @IdClass: 기본키가 두 칼럼(user_id, token)이라 키 모양을 아래 Key 클래스로 따로 적는다.
/** 기기 푸시 토큰. 사용자 1명 = 기기 여러 대. (user_id, token) 이 키 — 같은 토큰을 다시 등록하면 updated_at(과 platform)만 갱신 */
@Entity
@Table(name = "push_tokens")
@IdClass(PushToken.Key.class)
public class PushToken {
    @Id
    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Id
    @Column(columnDefinition = "text")
    private String token;

    @Column(columnDefinition = "text", nullable = false)
    private String platform; // ios | android | web

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PushToken() {} // JPA 가 DB 행을 객체로 만들 때 쓰는 빈 생성자. 코드에서는 of 를 쓴다

    // 새 토큰 행 객체를 만든다 (DB 저장은 PushService.register 가 tokens.save 로)
    public static PushToken of(UUID userId, String platform, String token, Instant now) {
        PushToken t = new PushToken();
        t.userId = userId;
        t.platform = platform;
        t.token = token;
        t.updatedAt = now;
        return t;
    }

    // 같은 토큰을 다시 등록할 때 — platform 과 updated_at 만 바꾼다 (save 없이 커밋 때 반영, CODE_GUIDE.md 3-3)
    public void touch(String platform, Instant now) {
        this.platform = platform;
        this.updatedAt = now;
    }

    // 읽기용 값. setter 는 없다 — 값을 바꾸는 길은 touch 뿐이다
    public UUID getUserId() { return userId; }
    public String getToken() { return token; }
    public String getPlatform() { return platform; }
    public Instant getUpdatedAt() { return updatedAt; }

    // (user_id, token) 두 칼럼 키. PushService 가 findById·deleteById 에 넘긴다.
    // JPA 규칙: 필드 이름이 위 @Id 필드와 같아야 하고, Serializable·빈 생성자·equals/hashCode 가 있어야 한다
    public static class Key implements Serializable {
        private UUID userId;
        private String token;

        public Key() {}

        public Key(UUID userId, String token) {
            this.userId = userId;
            this.token = token;
        }

        // 두 값이 모두 같으면 같은 키 (instanceof Key k: Key 이면 k 라는 이름으로 바로 쓴다)
        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userId, k.userId) && Objects.equals(token, k.token);
        }

        // equals 와 같은 두 값으로 만든다 (같은 키면 같은 hashCode)
        @Override
        public int hashCode() {
            return Objects.hash(userId, token);
        }
    }
}
