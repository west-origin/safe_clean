package kr.ansimclean.api.common.web;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

// idempotency_keys 테이블의 한 행 = (멱등 키, 사용자) 하나. 행 추가는 IdempotencyKeyRepository.insertIfAbsent(SQL),
// 완료 표시는 done() 이고 둘 다 Idempotent 가 부른다. 24시간 지난 행은 AuthCleaner 가 매일 지운다 (Idempotent.purgeOlderThan).
// @IdClass: 기본 키가 두 칸(key + user_id)이라 키 클래스(Id2)를 따로 둔다.
/** 쓰기 요청 한 건의 처리 기록 (BACKEND_PLAN.md §6.9). 업무 저장과 **같은 트랜잭션**에 들어간다. */
@Entity
@Table(name = "idempotency_keys")
@IdClass(IdempotencyKey.Id2.class)
public class IdempotencyKey {
    @Id
    @Column(name = "key", columnDefinition = "text")
    private String key; // 앱이 보낸 Idempotency-Key 헤더 값 (앱은 UUID 를 만든다)

    @Id
    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId; // 키는 사용자별로 따로 센다 — 남의 키와 겹쳐도 남의 응답을 받지 않는다

    @Column(name = "request_hash", columnDefinition = "text", nullable = false)
    private String requestHash; // 요청 내용의 SHA-256. 같은 키로 다른 내용을 보냈는지 가려낸다

    @Column(columnDefinition = "text", nullable = false)
    private String status; // "processing"(처리 중) 또는 "done"(끝남, 응답 저장됨)

    @Column(name = "response_status")
    private Integer responseStatus; // 저장한 응답의 HTTP 상태 (지금은 Idempotent 가 항상 200 으로 적는다)

    // @JdbcTypeCode(SqlTypes.JSON): 자바 문자열을 Postgres jsonb 칸에 JSON 으로 넣고 읽는다
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", columnDefinition = "jsonb")
    private String responseBody; // 첫 처리 결과를 JSON 으로 적어 둔 것. 재시도에는 이것을 그대로 돌려준다

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** JPA(Hibernate)가 DB 행을 읽어 객체를 만들 때 쓰는 빈 생성자. 우리 코드에서는 아래 processing() 안에서만 쓴다 */
    protected IdempotencyKey() {}

    /** '처리 중' 상태의 새 기록. 지금은 부르는 곳이 없다 — 실제 행 추가는 insertIfAbsent(SQL)가 한다 */
    public static IdempotencyKey processing(String key, UUID userId, String requestHash, Instant now) {
        IdempotencyKey k = new IdempotencyKey();
        k.key = key;
        k.userId = userId;
        k.requestHash = requestHash;
        k.status = "processing";
        k.createdAt = now;
        return k;
    }

    public String getRequestHash() { return requestHash; }
    public String getStatus() { return status; }
    public Integer getResponseStatus() { return responseStatus; }
    public String getResponseBody() { return responseBody; }

    /** 첫 요청의 처리가 끝나 응답이 저장됐는가 */
    public boolean isDone() {
        return "done".equals(status);
    }

    /** 처리 끝: 응답을 적어 둔다. Idempotent 가 findForUpdate 로 읽은 행에 부르므로 save 없이 커밋 때 반영된다 (CODE_GUIDE.md 3-3) */
    public void done(int responseStatus, String responseBody) {
        this.status = "done";
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
    }

    /**
     * 두 칸짜리 기본 키(key + userId). JPA 는 이런 키 클래스에 빈 생성자·equals·hashCode 와 Serializable 을 요구한다.
     * 필드 이름이 위 @Id 필드 이름과 같아야 짝이 맞는다. Repository 의 ID 타입으로 쓰인다
     */
    public static class Id2 implements Serializable {
        private String key;
        private UUID userId;

        public Id2() {}

        public Id2(String key, UUID userId) {
            this.key = key;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Id2 k && Objects.equals(key, k.key) && Objects.equals(userId, k.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, userId);
        }
    }
}
