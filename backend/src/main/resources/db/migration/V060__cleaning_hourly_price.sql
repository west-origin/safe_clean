-- 고객앱 시간 선택 화면과 견적·예약 저장 금액의 계산 기준을 통일한다.
-- 기존 예약 행의 금액은 변경하지 않는다.
insert into app_settings (key, value) values
  ('cleaning_hourly_amount', '20800'::jsonb),
  ('cleaning_recurring_discount', '2000'::jsonb)
on conflict (key) do nothing;
