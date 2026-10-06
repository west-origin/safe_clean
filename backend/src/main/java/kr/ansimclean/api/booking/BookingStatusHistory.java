package kr.ansimclean.api.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

// 예약 상태가 바뀔 때마다 한 줄씩 쌓는 이력 테이블(booking_status_history)의 한 행이다.
// 만드는 곳 예: BookingService.record(다른 패키지는 BookingService.recordTransition 을 거친다), StaleBookingCleaner(시스템 취소).
// setter 가 없어 자바 코드로는 고치지 않는다. 예외: 탈퇴 때 BookingRepository.scrubCustomerHistory 가 고객이 적은 사유만 SQL 로 비운다.
/** booking_status_history — 전이 이력 (누가·언제·왜). actor_id null = 시스템 */
@Entity
@Table(name = "booking_status_history")
public class BookingStatusHistory {
    // 기본 키. IDENTITY = 값은 DB 가 저장할 때 채운다 (bigserial 번호가 1씩 늘어난다)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", columnDefinition = "uuid", nullable = false)
    private UUID bookingId;

    // 상태는 enum 이 아니라 DB 문자열(BookingStatus.db(), 예: "scheduled")로 담는다. 예약을 처음 만들 때는 이전 상태가 없어 null
    @Column(name = "from_status", columnDefinition = "text")
    private String fromStatus;

    @Column(name = "to_status", columnDefinition = "text", nullable = false)
    private String toStatus;

    @Column(name = "actor_id", columnDefinition = "uuid")
    private UUID actorId;

    @Column(columnDefinition = "text")
    private String reason; // 사유 코드(예: "unassigned"·"rescheduled", 매니저 취소 구간·작업 중단 사유) 또는 고객이 적은 취소 사유. 없으면 null

    @Column(nullable = false)
    private Instant at; // 바뀐 시각 (UTC 기준 Instant)

    // JPA 가 DB 행을 객체로 만들 때 쓰는 빈 생성자. 코드에서는 아래 of(...) 로 만든다
    protected BookingStatusHistory() {}

    /** 이력 한 줄을 만든다(저장은 부르는 쪽이 history.save 로). from 이 null 이면 새 예약, actorId 가 null 이면 시스템(스케줄러) */
    public static BookingStatusHistory of(UUID bookingId, BookingStatus from, BookingStatus to, UUID actorId, String reason, Instant at) {
        BookingStatusHistory h = new BookingStatusHistory();
        h.bookingId = bookingId;
        h.fromStatus = from == null ? null : from.db();
        h.toStatus = to.db();
        h.actorId = actorId;
        h.reason = reason;
        h.at = at;
        return h;
    }

    public Long getId() { return id; }
    public UUID getBookingId() { return bookingId; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public UUID getActorId() { return actorId; }
    public String getReason() { return reason; }
    public Instant getAt() { return at; }
}
