package kr.ansimclean.api.payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, UUID> {
    Optional<PaymentRefund> findByPaymentId(UUID paymentId);
}
