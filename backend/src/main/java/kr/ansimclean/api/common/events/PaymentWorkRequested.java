package kr.ansimclean.api.common.events;
import java.util.UUID;
/** PG 키/개인정보는 아웃박스에 복제하지 않는다. */
public record PaymentWorkRequested(UUID paymentId) {}
