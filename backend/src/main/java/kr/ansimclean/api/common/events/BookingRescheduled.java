package kr.ansimclean.api.common.events;

import java.util.UUID;

// 아웃박스 이벤트 (CODE_GUIDE.md 3-6). 클래스 이름이 outbox_events 의 type 이 되어 핸들러를 고른다
// — 이름을 바꾸면 이미 쌓인 행이 "핸들러 없음" 으로 실패한다.
// 발행: BookingService 의 일정 변경 — 매칭 전(requested)이 아니고 이전 매니저가 있었을 때만.
// 밤이라 새 콜을 아침으로 미뤘으면 OfferService 가 미룬 매칭을 보낼 때(아침 08:05·새 일정이 급해졌을 때·운영자 재매칭) 발행한다 (문제 목록 2번).
// 그때 그 매니저에게 콜이 가도 콜 푸시(OffersDispatched)는 따로 적지 않는다 — 이 푸시 하나가 콜 목록으로 안내한다 (문제 목록 15번, 합친 38건 검토 R2).
// 처리: push.PushHandler — 이전 매니저에게. preferredOffered 가 false 면 '다른 매니저에게 다시 배정돼요',
// true 이거나 null 이면 '새 콜을 확인해주세요' 문구.
// 이 필드가 생기기 전에 저장된 JSON 에는 키가 없어 null 로 읽힌다. 기본형 boolean 은 null 을 담지 못하고 Boolean 은 담는다.
/** 고객이 일정을 바꿨다. previousManagerId 가 있으면 그 매니저에게 푸시 + 우선 오퍼 (§6.1 일정 변경) */
/** preferredOffered = 이전 매니저에게 우선 오퍼를 다시 보냈는가 (아니면 '다른 매니저에게 다시 배정' 문구, 자체 점검 #18·#56). 배포 전에 쌓인 이벤트는 null = 예전처럼 콜 안내 */
public record BookingRescheduled(UUID bookingId, UUID customerId, UUID previousManagerId, Boolean preferredOffered) {}
