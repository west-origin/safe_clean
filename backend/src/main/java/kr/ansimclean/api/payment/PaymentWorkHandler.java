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
    static final int MAX_REFUNDS = 20; // 한 번에 이어서 하는 부분 취소 수 (주 3회 한 달 = 12회차)
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
        // 취소가 승인 호출과 경합했어도 최신 예약 상태로 환불을 예약한다. 시리즈 결제는 취소된 회차마다 부분 취소가 한 번씩이라
        // 남은 환불 행이 없을 때까지(최대 MAX_REFUNDS 번) 이어서 한다 — 남으면 재조정이 다음에 이어 간다
        java.util.UUID last = null;
        for (int i = 0; i < MAX_REFUNDS; i++) {
            work = service.work(work.id());
            // 같은 환불 행이 또 오면(PG 결과가 아직 반영되지 않음) 멈춘다 — 같은 멱등 키로 되풀이하지 않고 재조정에 맡긴다
            if (work.refundId() == null || "CANCELED".equals(result.status()) || work.refundId().equals(last)) return;
            last = work.refundId();
            var paymentKey = result.paymentKey();
            result = gateway.cancel(work.id().toString(), paymentKey, work.refundId(), work.cancelAmount());
            if (result == null) return; // 결과 불명 — 재조정이 다시 조회한다
            service.apply(work.id(), result);
        }
    }
}
