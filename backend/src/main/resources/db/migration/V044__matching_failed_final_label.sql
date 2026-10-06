-- 자동 재매칭이 약속 전에 더 볼 수 없는(밤·약속 3시간 안) 매칭 실패 예약의 카드 문구 — '다시 찾아요' 라고 약속하지 않는다 (자체 점검 후속 검토, 문구는 코드에서 정함)
insert into app_settings (key, value) values
  (E'cleaner_matching_failed_final_label', '"매니저를 찾지 못했어요 · 1:1 문의로 알려주세요"'::jsonb);
