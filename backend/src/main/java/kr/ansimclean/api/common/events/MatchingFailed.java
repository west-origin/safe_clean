package kr.ansimclean.api.common.events;

import java.util.UUID;

// 아웃박스 이벤트 (CODE_GUIDE.md 3-6). 클래스 이름이 outbox_events 의 type 이 되어 핸들러를 고른다
// — 이름을 바꾸면 이미 쌓인 행이 "핸들러 없음" 으로 실패한다.
// 발행: OfferService.matchingFailed — 보낼 후보가 없거나 최대 회차까지 모두 거절·만료됐을 때. 예약에 실패 표시가
// 새로 붙을 때만(Booking.markMatchingFailed) 발행한다 — 표시가 남아 있는 동안은 다시 알리지 않고, 자동·운영자 재매칭이나
// 일정 변경으로 표시가 지워진 뒤 다시 실패하면 또 알린다.
// 처리: push.PushHandler — retryable·changeable 조합으로 문구 네 가지 중 하나를 고른다 (알림 설정과 무관).
/** 매칭 실패(후보 0명·최대 회차 소진) — 고객 안내 푸시. 예약은 assigning 으로 남고 운영자가 확인한다(§13-8) */
/**
 * retryable = 약속 전에 자동 재매칭(MatchingRetry)이 볼 수 있다, changeable = 고객이 아직 일정을 바꿀 수 있다(24시간 전) — 안내 문구가 가능한 길만 말하게 (자체 점검 후속 검토).
 * 배포 전에 쌓인 이벤트는 null — 둘 다 가능하다고 본다
 */
public record MatchingFailed(UUID bookingId, UUID customerId, Boolean retryable, Boolean changeable) {}
