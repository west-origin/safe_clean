package kr.ansimclean.api.common.events;

import java.util.UUID;

// 아웃박스 이벤트 (CODE_GUIDE.md 3-6). 클래스 이름이 outbox_events 의 type 이 되어 핸들러를 고른다
// — 이름을 바꾸면 이미 쌓인 행이 "핸들러 없음" 으로 실패한다.
// 발행: BookingSeriesService.notifyEnding (SeriesDispatcher 가 매일 09:00 부른다) — 마지막 회차 날짜(term_end)가
// 오늘부터 7일 안으로 들어온 진행 중 시리즈마다 한 번(ending_notified_at 으로 중복을 막는다).
// 처리: push.PushHandler — 고객 푸시(알림 설정의 push 가 꺼져 있으면 보내지 않는다).
/** 정기청소 마지막 회차가 7일 안으로 다가왔다 → 고객 푸시 "정기청소가 곧 끝나요" (§6.8) */
public record SeriesEnding(UUID seriesId, UUID customerId) {}
