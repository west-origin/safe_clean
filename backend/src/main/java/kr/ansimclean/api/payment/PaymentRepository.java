package kr.ansimclean.api.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

/**
 * payments 테이블을 읽고 쓰는 곳. 쓰는 곳: NoopPaymentGateway(물려받은 save), PaymentAccountPurge(detachMethodsOf).
 */
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    /** 탈퇴 + 5년 파기: 이 고객의 결제 수단을 가리키는 결제 행의 참조를 끊는다(행은 예약 기록이라 남긴다) */
    // 돌려주는 값은 바뀐 행 수. 결제 수단 행을 지우기 전에 불러야 외래 키 오류가 나지 않는다 (PaymentAccountPurge)
    @Modifying
    @Query(value = "update payments set payment_method_id = null where payment_method_id in (select id from payment_methods where customer_id = :customerId)", nativeQuery = true)
    int detachMethodsOf(UUID customerId);
}
