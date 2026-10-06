package kr.ansimclean.api.payment;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 탈퇴 + 5년: 결제 수단(빌링키)은 지우고 결제 행은 참조만 끊는다 */
// PaymentAccountPurge 단위 테스트. 저장소 두 개는 Mockito 가짜라 SQL 은 돌지 않고 '무엇을 어떤 순서로 불렀는지' 만 본다.
// role 은 실제로는 DeletedAccountPurger 가 파기 대상 계정마다 넘기는 "customer"·"manager" 값이다
class PaymentAccountPurgeTest {
    PaymentMethodRepository methods = mock(PaymentMethodRepository.class);
    PaymentRepository payments = mock(PaymentRepository.class);
    PaymentAccountPurge purge = new PaymentAccountPurge(methods, payments);

    // 고객: 결제 행의 수단 참조 끊기 → 결제 수단 삭제 순서(InOrder = 여러 가짜에 대한 호출 순서까지 확인).
    // 매니저: 아무것도 지우지 않는다 — 수단 삭제는 앞의 고객 몫 1번뿐이고, 결제 행 자체는 누구 것도 지우지 않는다
    @Test
    void 고객이면_참조를_끊은_뒤_결제_수단을_지우고_매니저는_건드리지_않는다() {
        UUID customer = UUID.randomUUID();
        purge.purge(customer, "customer");
        InOrder o = inOrder(payments, methods);
        o.verify(payments).detachMethodsOf(customer); // FK 때문에 먼저
        o.verify(methods).deleteByCustomerId(customer);

        purge.purge(UUID.randomUUID(), "manager");
        verify(methods, org.mockito.Mockito.times(1)).deleteByCustomerId(any()); // times(1) = 정확히 한 번 (위 고객 호출)
        verify(payments, never()).delete(any());
    }
}
