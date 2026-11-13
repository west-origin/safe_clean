-- V073__settings_gift_menu.sql — 고객 설정 탭 '친구·선물' 그룹에 '선물하기'·'선물함' 메뉴 (2026-11-13 친구·선물 5회차, 코드에서 정함)
-- V072 가 만든 '친구·선물' 그룹의 rows 끝에 붙인다. 이미 gifts 행이 있으면(운영자가 손으로 넣음) 건드리지 않는다.
-- 앱은 key 'gifts' → /gifts/new(선물 보내기), 'giftBox' → /gifts(받은·보낸 선물·내 금액권)로 잇는다 (apps/customer Settings.jsx MENU_PATHS)
update app_settings s
set value = (
  select jsonb_agg(
           case when g->>'title' = '친구·선물'
                then jsonb_set(g, '{rows}', (g->'rows') || '[{"key":"gifts","label":"선물하기"},{"key":"giftBox","label":"선물함"}]'::jsonb)
                else g end
           order by i)
  from jsonb_array_elements(s.value) with ordinality as t(g, i)
)
where s.key = 'settings_groups' and position('"gifts"' in s.value::text) = 0;
