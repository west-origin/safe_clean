-- 매칭 실패 카드 문구가 '매니저를 찾고 있어요 · 조금 더 걸릴 수 있어요' 였지만 실패 뒤에는 아무도 다시 찾지 않는다 (자체 점검 #5·#13, 2026-09-27 — 문구는 코드에서 정함)
-- 실제로 다시 찾는 길: 고객이 일정을 바꾸거나(재매칭 + 표시 지움) 운영자가 POST /admin/bookings/{id}/redispatch
update app_settings set value = '"매니저를 찾지 못했어요 · 일정을 바꾸면 다시 찾아요"'::jsonb
 where key = 'cleaner_matching_failed_label';
