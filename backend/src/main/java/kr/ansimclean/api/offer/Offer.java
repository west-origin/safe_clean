package kr.ansimclean.api.offer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;

import java.time.Instant;
import java.util.UUID;

// 콜(offer) 한 건 = DB offers 테이블의 한 행. 예약 하나를 여러 매니저에게 같은 회차(round)로 동시에 보낼 수 있고,
// 한 명이 수락하면 나머지는 cancelled 가 된다. 만들고 바꾸는 곳은 OfferService 뿐이다.
// 상태 흐름: pending → accepted | rejected | expired | cancelled, 그리고 accepted → superseded.
// DB 제약: (booking_id, manager_id, round) 중복 금지, 한 예약에 accepted 는 하나만(offers_one_accepted 인덱스).
/** offers — 매니저에게 보낸 콜 (§6.2). 상태는 메서드로만 바뀐다 */
@Entity
@Table(name = "offers")
public class Offer {
    // status 칸에 들어가는 문자열 — DB 의 check 제약과 같은 값이어야 한다
    public static final String PENDING = "pending"; // 보냈고 아직 답이 없다 (매니저 콜 목록에 보인다)
    public static final String ACCEPTED = "accepted"; // 매니저가 수락해 예약에 배정됐다
    public static final String REJECTED = "rejected"; // 매니저가 거절했다
    public static final String EXPIRED = "expired"; // 답 없이 유효 시간이 지났다 (OfferExpirer)
    public static final String CANCELLED = "cancelled"; // 답하기 전에 닫혔다 (예: 예약 취소, 다른 매니저 수락, 일정 변경·방문 취소·재매칭으로 새로 보낼 때)
    public static final String SUPERSEDED = "superseded"; // 수락했던 콜이 일정 변경·매니저 방문 취소로 무효가 됐다
    /** (코드에서 정함) 내가 수락한 콜을 거절하려 할 때의 오류 문구 — 앱 거절 시트가 그대로 보인다 (문제 목록 10번) */
    static final String ALREADY_ACCEPTED = "이미 수락한 콜이에요. 일정에서 확인해주세요";

    // @Id = 기본 키, @Column = DB 칸 이름·제약과 연결한다 (name 이 없는 id·round·status·preferred 는 필드 이름과 같은 칸)
    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "booking_id", columnDefinition = "uuid", nullable = false)
    private UUID bookingId;

    @Column(name = "manager_id", columnDefinition = "uuid", nullable = false)
    private UUID managerId;

    @Column(nullable = false)
    private int round; // 보낼 때의 예약 dispatch_round — 재매칭·일정 변경·방문 취소 때마다 예약 쪽 값이 1씩 오른다

    @Column(columnDefinition = "text", nullable = false)
    private String status; // 위 상수 중 하나

    @Column(name = "pay_amount", nullable = false)
    private long payAmount; // 매니저 수령 예정액(원) = 예약 금액 − 플랫폼 수수료, 보낼 때 고정

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt; // 이 시각이 지나면 수락할 수 없다

    @Column(name = "responded_at")
    private Instant respondedAt; // 수락·거절·만료된 시각. 대기 중이거나 cancelled 로 닫혔으면 null

    @Column(name = "reject_reason", columnDefinition = "text")
    private String rejectReason; // 거절 사유 문구 (거절이 아니면 null)

    @Column(nullable = false)
    private boolean preferred; // 우선 콜 — 직전 매니저 한 명에게 먼저 보내는 짧은 콜 (preferred_offer_ttl_minutes)

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // JPA 가 DB 행을 객체로 만들 때 쓰는 빈 생성자. 코드에서는 아래 pending() 으로만 만든다
    protected Offer() {}

    /** 새 대기 콜을 만든다 (아직 저장 전 — OfferService.send 가 save 한다). id 는 여기서 새로 뽑는다 */
    public static Offer pending(UUID bookingId, UUID managerId, int round, long payAmount, Instant expiresAt, boolean preferred, Instant now) {
        Offer o = new Offer();
        o.id = UUID.randomUUID();
        o.bookingId = bookingId;
        o.managerId = managerId;
        o.round = round;
        o.status = PENDING;
        o.payAmount = payAmount;
        o.expiresAt = expiresAt;
        o.preferred = preferred;
        o.createdAt = now;
        return o;
    }

    // 읽기 전용 getter — setter 가 없어서 상태는 아래 accept·reject·expire·cancel·supersede 로만 바뀐다
    public UUID getId() { return id; }
    public UUID getBookingId() { return bookingId; }
    public UUID getManagerId() { return managerId; }
    public int getRound() { return round; }
    public String getStatus() { return status; }
    public long getPayAmount() { return payAmount; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRespondedAt() { return respondedAt; }
    public String getRejectReason() { return rejectReason; }
    public boolean isPreferred() { return preferred; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean isPending() {
        return PENDING.equals(status);
    }

    /** 유효 시간이 지났는가 — expiresAt 이 now 보다 앞이면 true (상태 칸과 상관없이 시각만 본다) */
    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }

    /** 수락: pending 이고 만료 전일 때만 (§6.2-3). 아니면 OFFER_TAKEN / OFFER_EXPIRED */
    public void accept(Instant now) {
        if (!isPending()) throw new ApiException(ErrorCode.OFFER_TAKEN);
        if (isExpired(now)) throw new ApiException(ErrorCode.OFFER_EXPIRED);
        status = ACCEPTED;
        respondedAt = now;
    }

    /**
     * 거절: 대기 중이고 만료 전일 때만. 닫힌 콜이면 닫힌 이유대로 오류를 던져 앱이 알맞은 안내를 보이게 한다 (문제 목록 10번, 사용자 결정 B):
     * 내가 수락한 콜 = INVALID_STATE(ALREADY_ACCEPTED — 수락 응답이 끊겨 거절한 줄 알지 않게), 만료됐거나 만료 시각이 지난 콜 = OFFER_EXPIRED,
     * 그 밖에 닫힌 콜(다른 매니저 수락·고객 취소로 cancelled, 일정 변경·방문 취소로 superseded) = OFFER_TAKEN. 앱은 뒤 둘에 '이미 마감된 요청이에요' 시트를 연다.
     * 이미 거절한 콜은 OfferService.reject 가 여기 오기 전에 저장된 사유로 돌려준다(멱등) — 여기로 오면 OFFER_TAKEN 이다
     */
    public void reject(String reason, Instant now) {
        if (ACCEPTED.equals(status)) throw new ApiException(ErrorCode.INVALID_STATE, ALREADY_ACCEPTED);
        if (EXPIRED.equals(status) || (isPending() && isExpired(now))) throw new ApiException(ErrorCode.OFFER_EXPIRED); // 만료 시각이 지났는데 OfferExpirer(30초)가 아직 닫지 못한 콜도
        if (!isPending()) throw new ApiException(ErrorCode.OFFER_TAKEN);
        status = REJECTED;
        rejectReason = reason;
        respondedAt = now;
    }

    /** 스케줄러: pending 이면 expired 로 (아니면 무시) */
    public boolean expire(Instant now) {
        if (!isPending()) return false; // 잠그는 사이 수락·거절됐으면 건드리지 않는다. true = 실제로 만료시켰다
        status = EXPIRED;
        respondedAt = now;
        return true;
    }

    /** pending 이면 cancelled 로 (예: 고객 취소, 다른 매니저 수락 — 쓰이는 경우는 위 CANCELLED 설명) */
    public void cancel() {
        if (isPending()) status = CANCELLED; // 이미 닫힌 콜은 그대로 둔다 — 그래서 여러 상태가 섞인 목록에 한꺼번에 불러도 된다
    }

    /** 일정 변경·매니저 방문 취소: accepted → superseded (offers_one_accepted 인덱스를 비운다) */
    public void supersede() {
        if (ACCEPTED.equals(status)) status = SUPERSEDED;
    }
}
