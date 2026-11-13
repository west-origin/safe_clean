package kr.ansimclean.api.payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, UUID> {
    /** 예약·선물 결제의 전체 잔액 환불 (결제당 한 행). 시리즈 결제는 아래 회차 행들을 쓴다 */
    @org.springframework.data.jpa.repository.Query("select r from PaymentRefund r where r.paymentId = :paymentId and r.bookingId is null")
    Optional<PaymentRefund> findByPaymentId(UUID paymentId);

    /** 시리즈 결제의 회차 몫 환불 행들 — 만든 순서 (PG 누적 환불액을 이 순서로 나눠 done 을 정한다) */
    java.util.List<PaymentRefund> findByPaymentIdAndBookingIdIsNotNullOrderByCreatedAtAscIdAsc(UUID paymentId);
}
