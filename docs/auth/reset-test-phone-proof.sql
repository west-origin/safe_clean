-- 개발(safe_clean_test, xedkwtdkpxejlqpbdstb) 전용: 고객 휴대폰 인증(첫 1회 청소)을 '미인증' 으로 되돌린다.
-- 운영 프로젝트(safe_clean)에서는 절대 실행하지 않는다.
-- 실행: Supabase 대시보드 > safe_clean_test > SQL Editor 에 붙여 넣고 Run. 맨 위 번호만 바꾸면 된다.
-- 하는 일:
--   1) 우리 테이블: 그 번호의 인증 기록(customer_phone_proofs)·진행 중 요청(customer_phone_challenges) 삭제,
--      아직 예약에 쓰지 않은 첫 1회 혜택 기록(first_cleaning_claims, booking_id 없음) 삭제
--   2) Supabase Auth: 그 계정에 붙은 번호(auth.users.phone)와 phone identity 를 떼어 낸다.
--      이것을 하지 않으면 같은 번호로 다시 요청할 때 서버가 '이미 확인된 번호' 로 보고 문자 없이 바로 인증을 끝낸다.
--      카카오·구글 identity 와 계정(id)·예약 등 다른 데이터는 건드리지 않는다.
-- 확인: 마지막 select 가 0건이면 끝. 앱은 '가정 > 1회' 를 다시 누르면 인증 안내 시트가 뜬다.

begin;

create temp table reset_target on commit drop as
select '+821000000001'::text as e164;   -- ← 되돌릴 번호 (+82 형식)

-- 우리 테이블 (그 번호로 인증한 고객)
create temp table reset_users on commit drop as
select customer_id as id from customer_phone_proofs where phone = (select e164 from reset_target)
union
select id from auth.users where phone = substr((select e164 from reset_target), 2);

delete from customer_phone_challenges where customer_id in (select id from reset_users);
delete from customer_phone_proofs     where customer_id in (select id from reset_users);
delete from first_cleaning_claims     where customer_id in (select id from reset_users) and booking_id is null;

-- Supabase Auth: 번호와 phone identity 떼기 (개발 프로젝트에서만)
delete from auth.identities where provider = 'phone' and user_id in (select id from reset_users);
update auth.users
   set phone = null, phone_confirmed_at = null,
       phone_change = '', phone_change_token = '', phone_change_sent_at = null
 where id in (select id from reset_users);

-- 확인: 모두 0 이어야 한다
select
  (select count(*) from customer_phone_proofs where phone = (select e164 from reset_target)) as proofs,
  (select count(*) from auth.users where phone = substr((select e164 from reset_target), 2)) as auth_phone,
  (select count(*) from auth.identities where provider = 'phone' and user_id in (select id from reset_users)) as phone_identities;

commit;
