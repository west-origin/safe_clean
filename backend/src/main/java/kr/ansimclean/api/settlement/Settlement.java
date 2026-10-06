package kr.ansimclean.api.settlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

// 작업 완료(JobService) 트랜잭션 안에서 SettlementService.createFor 가 pending 으로 만든다.
// 매니저 앱 정산 화면(목록·합계)과 정산 상세, 작업 완료 화면의 예상 정산액이 이 행을 읽는다.
// 금액은 모두 long 원 단위이고, 예약 행에 고정된 금액(total_amount·platform_fee)에서만 계산한다.
/** settlements — 작업 완료 1건 = 정산 1건 (booking_id unique, §6.5). status=complete 는 운영자가 지급 후 바꾼다 */
@Entity
@Table(name = "settlements")
public class Settlement {
    // status 칸의 값: pending = 정산 예정(아직 지급 전), complete = 지급 완료
    public static final String PENDING = "pending";
    public static final String COMPLETE = "complete";

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "booking_id", columnDefinition = "uuid", nullable = false)
    private UUID bookingId;

    @Column(name = "manager_id", columnDefinition = "uuid", nullable = false)
    private UUID managerId;

    @Column(name = "service_amount", nullable = false)
    private long serviceAmount; // 서비스 금액 = 예약 total_amount

    @Column(name = "platform_fee", nullable = false)
    private long platformFee; // 플랫폼 수수료 = 예약 platform_fee

    @Column(name = "other_fee", nullable = false)
    private long otherFee; // 기타 공제. 지금 코드에서는 만들 때 0 으로만 넣는다

    @Column(name = "final_amount", nullable = false)
    private long finalAmount; // 매니저가 받을 최종 금액 (목록·합계에 쓰는 값)

    @Column(columnDefinition = "text", nullable = false)
    private String status;

    @Column(name = "settled_at")
    private Instant settledAt; // 지급 완료 시각. pending 이면 null

    // 정산 행을 만든 시각 = 작업 완료 시각. '이번 달' 합계와 목록 순서·페이지 커서의 기준
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // JPA 가 DB 행을 객체로 만들 때 쓰는 빈 생성자. 코드에서는 pending(...) 으로 만든다
    protected Settlement() {}

    /** 예약 스냅샷에서만 계산한다: service = total_amount, fee = platform_fee, final = total − fee (§6.4·§6.5) */
    public static Settlement pending(UUID bookingId, UUID managerId, long serviceAmount, long platformFee, Instant now) {
        Settlement s = new Settlement();
        s.id = UUID.randomUUID();
        s.bookingId = bookingId;
        s.managerId = managerId;
        s.serviceAmount = serviceAmount;
        s.platformFee = platformFee;
        s.otherFee = 0;
        s.finalAmount = serviceAmount - platformFee;
        s.status = PENDING;
        s.createdAt = now;
        return s;
    }

    public UUID getId() { return id; }
    public UUID getBookingId() { return bookingId; }
    public UUID getManagerId() { return managerId; }
    public long getServiceAmount() { return serviceAmount; }
    public long getPlatformFee() { return platformFee; }
    public long getOtherFee() { return otherFee; }
    public long getFinalAmount() { return finalAmount; }
    public String getStatus() { return status; }
    public Instant getSettledAt() { return settledAt; }
    public Instant getCreatedAt() { return createdAt; }

    /** 운영자가 지급한 뒤 (관리 API 전까지 SQL·테스트) */
    public void markComplete(Instant now) {
        status = COMPLETE;
        settledAt = now;
    }

    /** 지급 완료인가 — 목록 배지(complete / settlementPending)를 고를 때 쓴다 */
    public boolean isComplete() {
        return COMPLETE.equals(status);
    }
}
