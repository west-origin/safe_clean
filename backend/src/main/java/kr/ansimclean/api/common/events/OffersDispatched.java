package kr.ansimclean.api.common.events;

import java.util.List;
import java.util.UUID;

// 아웃박스 이벤트 (CODE_GUIDE.md 3-6). 클래스 이름이 outbox_events 의 type 이 되어 핸들러를 고른다
// — 이름을 바꾸면 이미 쌓인 행이 "핸들러 없음" 으로 실패한다.
// 발행: OfferService.send — 매니저마다 대기 콜 행을 저장한 뒤 한 건. 우선 콜(preferred)은 정기청소 직전 매니저 한 명에게
// 먼저 보내는 콜이다. 일정 변경 전 매니저에게 먼저 보내는 우선 콜은 이 이벤트를 적지 않는다 — BookingRescheduled 푸시가 안내한다 (문제 목록 15번).
// 운영자 재매칭이 밤에 미뤄 둔 일정 변경 전 매니저에게 일반 콜을 보낼 때도 그 매니저는 managerIds 에서 빠진다 (합친 38건 검토 R2).
// 처리: push.PushHandler — managerIds 중 push·call 설정이 켜진 매니저에게 푸시. 한 명이 실패해도 나머지에게 먼저 보내고,
// 실패가 있으면 재시도한다.
/** 매니저들에게 콜(오퍼)을 보냈다 → 매니저 푸시 "새 콜이 도착했어요" (우선 콜은 단골 고객 문구) (§6.2-2). preferred = 우선 오퍼 1명 */
public record OffersDispatched(UUID bookingId, List<UUID> managerIds, boolean preferred) {}
