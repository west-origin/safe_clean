package kr.ansimclean.api.common.outbox;

/** OutboxService.publish 가 올리는 Spring 이벤트 — 커밋 뒤 OutboxDispatcher 를 바로 깨운다(5초 주기를 기다리지 않게) */
// 필드가 없는 record — 값은 없고 '새 행이 생겼다' 는 신호만 전한다. 받는 곳은 OutboxDispatcher.onQueued
public record OutboxQueued() {
}
