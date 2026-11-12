-- 고객 휴대폰 인증을 Supabase Auth phone_change 로 바꾸면서(2026-11-10) 인증번호 오답 횟수를 요청 단위로 센다.
-- 5번 틀리면 그 요청을 지우고 새 인증번호를 받게 한다 (CustomerPhoneService.MAX_FAILURES). 인증번호 자체는 Supabase 가 보관한다.
alter table customer_phone_challenges add column failed_attempts integer not null default 0 check (failed_attempts >= 0);
