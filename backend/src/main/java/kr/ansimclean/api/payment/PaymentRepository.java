package kr.ansimclean.api.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

/**
 * payments 테이블을 읽고 쓰는 곳. 쓰는 곳: NoopPaymentGateway(물려받은 save), PaymentAccountPurge(detachMethodsOf).
 */
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    java.util.Optional<Payment> lockById(UUID id);

    java.util.Optional<Payment> findFirstByBookingIdAndProviderAndStatusNotOrderByCreatedAtDesc(UUID bookingId, String provider, String status);

    @Query("select p.bookingId from Payment p where p.id = :id and p.provider = 'toss'")
    java.util.Optional<UUID> findCheckoutBookingId(UUID id);

    @Query(value = """
            select p.id from payments p join bookings b on b.id = p.booking_id
             where p.provider = 'toss' and p.status not in ('failed','refunded')
               and p.provider_tx_id is not null
               and (p.next_check_at is null or p.next_check_at <= :now
                    or (b.status = 'cancelled' and p.status in ('paid','partially_refunded')
                        and not exists (select 1 from payment_refunds r where r.payment_id = p.id)))
             order by p.next_check_at nulls first, p.id limit 50
            """, nativeQuery = true)
    java.util.List<UUID> findDueIds(java.time.Instant now);

    @Query(value = """
            select exists(select 1 from payments p join bookings b on b.id = p.booking_id
              where b.customer_id = :userId and p.provider = 'toss'
                and (p.status = 'authorized' or exists
                  (select 1 from payment_refunds r where r.payment_id = p.id and r.status = 'pending')))
            """, nativeQuery = true)
    boolean hasUnresolvedPayment(UUID userId);
    /** 탈퇴 + 5년 파기: 이 고객의 결제 수단을 가리키는 결제 행의 참조를 끊는다(행은 예약 기록이라 남긴다) */
    // 돌려주는 값은 바뀐 행 수. 결제 수단 행을 지우기 전에 불러야 외래 키 오류가 나지 않는다 (PaymentAccountPurge)
    @Modifying
    @Query(value = "update payments set payment_method_id = null where payment_method_id in (select id from payment_methods where customer_id = :customerId)", nativeQuery = true)
    int detachMethodsOf(UUID customerId);
}
