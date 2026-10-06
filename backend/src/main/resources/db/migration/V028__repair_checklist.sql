-- 수리 예약의 작업 체크리스트 (직접 사용 점검 U13, 2026-09-24 — 항목은 코드에서 정함, Figma 에 없음).
-- 템플릿이 없어 가정 청소 목록(거실 바닥 청소·변기 …)이 에어컨 수리 작업에도 나왔다(CatalogService.checklistTemplate 의 home 대체).
-- 순서는 고객 확인 화면 문구("출장 및 진단 후 … 고객님 동의 후 수리를 진행해요")를 따른다. 이미 배정된 예약은 배정 때 복사한 목록 그대로
insert into checklist_templates (category_key, group_name, label, sort) values
  ('repair', E'진단', E'고객과 증상 확인', 1),
  ('repair', E'진단', E'원인 진단', 2),
  ('repair', E'수리', E'부품·비용 안내 후 고객 동의', 11),
  ('repair', E'수리', E'수리 진행', 12),
  ('repair', E'마무리', E'작동 확인', 21),
  ('repair', E'마무리', E'주변 정리', 22)
on conflict do nothing;
