-- V072__settings_friends_menu.sql — 고객 설정 탭에 '친구·선물' 그룹과 '친구' 메뉴 (2026-11-13 고객 친구 4회차, 코드에서 정함)
-- settings_groups(V005) 의 '계정' 그룹 바로 뒤에 넣는다. 이미 friends 행이 있으면(운영자가 손으로 넣음) 건드리지 않는다.
-- 앱은 key 'friends' 를 /friends 화면으로 잇는다(apps/customer Settings.jsx MENU_PATHS). '선물하기' 행은 5회차에서 더한다
update app_settings
set value = jsonb_insert(value, '{1}', '{"title":"친구·선물","rows":[{"key":"friends","label":"친구"}]}'::jsonb)
where key = 'settings_groups' and position('"friends"' in value::text) = 0;
