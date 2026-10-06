package kr.ansimclean.api.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * payment_methods 테이블을 읽고 쓰는 곳. 쓰는 곳: PaymentService(조회), PaymentAccountPurge(파기).
 */
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, UUID> {
    /** 한 고객의 결제 수단, 등록한 순서(오래된 것 먼저) */
    List<PaymentMethod> findByCustomerIdOrderByCreatedAt(UUID customerId);

    /** 탈퇴 + 5년 파기 */
    long deleteByCustomerId(UUID customerId); // 지운 행 수를 돌려준다. 트랜잭션 안에서 불러야 한다 (DeletedAccountPurger 가 계정마다 연다)
}
