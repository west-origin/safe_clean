package kr.ansimclean.api.common.events;

import java.util.UUID;

// 아웃박스 이벤트 (CODE_GUIDE.md 3-6). 클래스 이름이 outbox_events 의 type 이 되어 핸들러를 고른다 — 이름을 바꾸지 말 것.
// 발행: friend.FriendService.sendRequest — 새 대기 요청을 만들 때만(자동 친구·초대 수락은 알리지 않는다).
// 처리: push.PushHandler — 받은 사람에게 '친구 요청이 왔어요' (고객 push 토글). 개인정보(닉네임)는 싣지 않는다
/** 고객이 친구 요청을 받았다 */
public record FriendRequested(UUID requestId, UUID addresseeId) {}
