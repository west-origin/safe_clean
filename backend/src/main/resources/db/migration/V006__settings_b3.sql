-- V006__settings_b3.sql — B3 에서 추가된 운영 문구
insert into app_settings (key, value) values
  (E'payment_note', '"결제는 청소 완료 후 진행돼요"'::jsonb);
