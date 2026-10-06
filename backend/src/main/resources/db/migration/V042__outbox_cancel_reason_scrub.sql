-- 고객 취소 이벤트(BookingCancelled)에 자유 입력 취소 사유가 원문으로 남아 있었다 — 아웃박스 원장은 지우지 않아 탈퇴 5년 파기도 비켜 갔다 (자체 점검 후속 검토).
-- 앞으로는 사유 코드만 싣는다(BookingService.cancelOne). 이미 쌓인 행도 코드로 바꾼다 — 푸시는 not_started 인지만 본다
update outbox_events
   set payload = jsonb_set(payload, '{reason}', to_jsonb('customer'::text))
 where type = 'BookingCancelled'
   and payload ? 'reason'
   and payload->>'reason' not in ('customer', 'series_cancelled', 'unassigned', 'not_started');
