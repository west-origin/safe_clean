-- 매칭 실패 카드 문구에 자동 재매칭을 반영한다 (사용자 결정 2026-09-27 — 새로 가능한 매니저가 생기면 매시 다시 찾는다, offer/MatchingRetry).
-- V036 '일정을 바꾸면 다시 찾아요' 는 일정 변경만 다시 찾는 길이던 때의 문구. 문구는 코드에서 정함
update app_settings set value = '"매니저를 찾지 못했어요 · 가능한 매니저가 생기면 다시 찾아요"'::jsonb
 where key = 'cleaner_matching_failed_label';
