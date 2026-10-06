package kr.ansimclean.api.payment;

import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.time.AppClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

// PaymentGateway 의 PG 연동 전 구현 — 결제(PG) 회사와 연결하지 않고 흉내만 낸다.
// main 코드에서 PaymentGateway 를 주입받아 부르는 곳은 아직 없다 — 부를 자리는 BookingService 의 `// PAYMENT:` 주석에 적혀 있다.
// PG 구현체를 새로 만들고 @Primary 를 붙이면 스프링이 이 클래스 대신 그쪽을 넣어 준다.
/** PG 연동 전 기본 구현: 청구는 pending 행만 남기고 로그, 빌링키 등록·환불은 INVALID_STATE (앱 화면이 "연동 후 제공" 을 안내한다) */
@Component
public class NoopPaymentGateway implements PaymentGateway {
    private static final Logger log = LoggerFactory.getLogger(NoopPaymentGateway.class);

    private final PaymentRepository payments;
    private final AppClock clock;

    public NoopPaymentGateway(PaymentRepository payments, AppClock clock) {
        this.payments = payments;
        this.clock = clock;
    }

    /** 결제 수단 등록 — PG 가 없어 빌링키를 받을 수 없으므로 늘 INVALID_STATE(HTTP 409)를 던진다 */
    @Override
    public PaymentMethod registerBillingKey(UUID customerId, String providerPayload) {
        throw new ApiException(ErrorCode.INVALID_STATE, "결제 수단 등록은 결제 서비스 연동 후 제공돼요");
    }

    /**
     * 청구 흉내 — 돈은 오가지 않는다. provider="none" 인 대기(pending) 결제 행 하나를 DB 에 저장하고 로그만 남긴다.
     * amount 는 원 단위. method 가 없으면(null) 결제 수단 칸도 비운다
     */
    @Override
    public Payment charge(UUID bookingId, long amount, PaymentMethod method) {
        log.info("PAYMENT(noop) 청구 예약={} 금액={}", bookingId, amount);
        return payments.save(Payment.pending(bookingId, method == null ? null : method.getId(), "none", amount, clock.now()));
    }

    /** 환불 — PG 가 없어 할 수 없으므로 늘 INVALID_STATE(HTTP 409) */
    @Override
    public void refund(Payment payment, long amount) {
        throw new ApiException(ErrorCode.INVALID_STATE, "환불은 결제 서비스 연동 후 제공돼요");
    }
}
