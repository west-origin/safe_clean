-- V076__confirm_copy_text.sql — 예약 확인 화면 문구를 앱과 같게 (2026-11-16, CI 계약 검사 CatalogIT 에서 발견)
-- 앱은 e84cb34(11-12)에서 확인 화면 문구를 바꿨고(apps/customer/src/api/mock.js CONFIRM_COPY, 화면은 이 값을 직접 쓴다),
-- 서버 app_settings.confirm_copy 는 V005 의 옛 문구로 남아 GET /catalog/categories/{c}/confirm-copy 의 copy 가 앱과 달랐다.
-- 운영자가 문구를 손으로 바꿨으면(옛 문구가 아니면) 건드리지 않는다.
update app_settings
set value = jsonb_build_object('title', E'예약 내용을\n확인해 주세요.', 'description', '예약을 확정하기 전 마지막으로 확인해 주세요.')
where key = 'confirm_copy'
  and value = jsonb_build_object('title', E'예약내용을\n확인해주세요', 'description', E'아래 내용으로 예약을 진행할게요.\n다시 한번 확인해주세요.');
