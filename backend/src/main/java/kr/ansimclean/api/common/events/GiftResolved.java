package kr.ansimclean.api.common.events;

import java.util.UUID;

// 아웃박스 이벤트 — 클래스 이름이 type 이다(바꾸지 말 것).
// 발행: gift.GiftService — 받는 사람이 수락·거절했거나, 받는 기간이 지나 금액권이 돌아왔을 때 (보낸 사람 취소는 알리지 않는다).
// 처리: push.PushHandler — 보낸 사람에게 결과 (거래성, 토글과 무관). result = accepted | rejected | expired
/** 보낸 선물의 결과 */
public record GiftResolved(UUID giftId, UUID senderId, String result) {}
