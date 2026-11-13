package kr.ansimclean.api.common.events;

import java.util.UUID;

// 아웃박스 이벤트 — 클래스 이름이 type 이다(바꾸지 말 것).
// 발행: gift.GiftLifecycle.send — 보유 금액권 선물을 보낼 때, 새로 구매한 선물은 결제가 확정되는 트랜잭션에서.
// 처리: push.PushHandler — 받는 사람에게 '선물이 도착했어요' (돈이 오가는 거래성 알림이라 토글과 무관). 개인정보·메시지는 싣지 않는다
/** 고객이 친구에게 금액권 선물을 받았다 */
public record GiftReceived(UUID giftId, UUID recipientId) {}
