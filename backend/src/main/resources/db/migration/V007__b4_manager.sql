-- V007__b4_manager.sql — B4 매니저: 우선 오퍼 표시, 운영 값·문구 (문구는 앱 mock.js 그대로)
alter table offers add column preferred boolean not null default false;   -- 우선 오퍼(일정 변경·정기 회차의 직전 매니저, 10분). 거절·만료 시 같은 회차에서 일반 매칭으로 이어진다 (§6.2)

insert into app_settings (key, value) values
  (E'max_dispatch_rounds', '3'::jsonb),                                     -- 오퍼가 전부 거절·만료되면 이 회차까지만 재매칭한다 (§6.2-4 "1회")
  (E'category_short_labels', '{"home":"기본청소","office":"사무실청소","appliance":"가전청소","move-in":"입주청소"}'::jsonb),  -- 정산 목록 제목 "강남구 기본청소"
  (E'eta_placeholder', '"이동 시간은 배정 후 안내"'::jsonb),               -- 콜 카드 eta (거리 계산 전, §13-9)
  (E'settlement_note', '"정산은 다음 정산일에 지급됩니다."'::jsonb),
  (E'pause_note', '"촬영이 중단된 동안에는 정산이 집계되지 않습니다."'::jsonb),
  (E'job_complete_title', '"청소가 완료되었습니다"'::jsonb),
  (E'manager_profile_menu', '[{"key":"info","label":"매니저 정보"},{"key":"area","label":"활동 지역"},{"key":"availability","label":"근무 일정 관리","to":"/schedule/availability"},{"key":"identity","label":"신원 인증"},{"key":"education","label":"교육 이수"},{"key":"account","label":"정산 계좌"},{"key":"equipment","label":"촬영 장비","to":"/profile/equipment"},{"key":"notifications","label":"알림 설정"},{"key":"notice","label":"공지사항"},{"key":"support","label":"고객센터"}]'::jsonb),
  (E'manager_notification_rows', '[{"key":"push","label":"푸시 알림"},{"key":"call","label":"새 청소 요청 알림"},{"key":"settlement","label":"정산 완료 알림"}]'::jsonb),
  (E'manager_profile_notes', '{"info":"정보 수정은 고객센터로 문의해주세요.","area":"활동 지역 변경은 고객센터로 문의해주세요.","account":"계좌 변경은 고객센터로 문의해주세요."}'::jsonb);
