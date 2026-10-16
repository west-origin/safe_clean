package kr.ansimclean.api.payment;

import kr.ansimclean.api.common.events.PaymentWorkRequested;
import kr.ansimclean.api.common.outbox.OutboxHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.util.Set;

/** 아웃박스의 트랜잭션을 중단하고 PG에 요청한다. DB 작업은 짧은 별도 트랜잭션이다. */
@Component
public class PaymentWorkHandler implements OutboxHandler {
    private final CheckoutService service;
    private final TossPaymentGateway gateway;
    private final JsonMapper json = JsonMapper.builder().build();
    public PaymentWorkHandler(CheckoutService service, TossPaymentGateway gateway) { this.service = service; this.gateway = gateway; }
    @Override public Set<String> types() { return Set.of("PaymentWorkRequested"); }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void handle(String type, String payloadJson) {
        var event = json.readValue(payloadJson, PaymentWorkRequested.class);
        var work = service.work(event.paymentId());
        var result = gateway.lookup(work.id().toString());
        if (work.confirm() && work.paymentKey() != null && (result == null || "IN_PROGRESS".equals(result.status()))) {
            result = gateway.confirm(work.id().toString(), work.paymentKey(), work.amount());
        }
        if (result == null) return; // 결과 불명: 새 주문을 만들지 않고 재조정 대상에 남긴다.
        service.apply(work.id(), result);
        // 취소가 승인 호출과 경합했어도 최신 예약 상태로 전체 잔액 환불을 예약한다.
        work = service.work(work.id());
        if (work.refundId() != null && !"CANCELED".equals(result.status())) {
            service.apply(work.id(), gateway.cancel(work.id().toString(), result.paymentKey(), work.refundId()));
        }
    }
}
