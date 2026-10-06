package kr.ansimclean.api.payment;

import kr.ansimclean.api.payment.dto.PaymentMethodResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

// 부르는 곳: PaymentMethodController (GET /customer/payment-methods). 쓰기 메서드가 없어 클래스 전체를 읽기 전용으로 둔다.
/** 결제 수단 조회. 등록·청구는 PaymentGateway (구조만) */
@Service
@Transactional(readOnly = true)
public class PaymentService {
    private final PaymentMethodRepository methods;

    public PaymentService(PaymentMethodRepository methods) {
        this.methods = methods;
    }

    /**
     * 고객의 결제 수단을 등록 순으로 화면용 {id, label, isDefault} 목록으로 바꿔 준다. 빌링키는 밖으로 내보내지 않는다.
     * PG 연동 전에는 행이 없어 늘 빈 목록
     */
    public List<PaymentMethodResponse> methods(UUID customerId) {
        return methods.findByCustomerIdOrderByCreatedAt(customerId).stream()
                .map(m -> new PaymentMethodResponse(m.getId(), m.getLabel(), m.isDefault())).toList();
    }
}
