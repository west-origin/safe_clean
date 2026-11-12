-- Read-only. Run against each environment before applying V061 or deploying customer password removal.
select i.provider, count(*) from auth.identities i group by i.provider;
select p.id, p.signup_completed_at is not null as complete,
       array(select i.provider from auth.identities i where i.user_id=p.id) as providers,
       (select count(*) from bookings b where b.customer_id=p.id) as bookings
from profiles p
where p.role='customer' and p.deleted_at is null
  and not exists(select 1 from auth.identities i where i.user_id=p.id and i.provider in ('kakao','google'));
select table_name, column_name, data_type, is_nullable
from information_schema.columns
where table_schema='public' and table_name in ('profiles','bookings','payments','payment_methods','phone_verifications')
order by table_name, ordinal_position;
-- Existing duplicate auth emails are a review queue, not a request to merge them.
select lower(email) as email, count(*) from auth.users
where email is not null and email <> '' group by lower(email) having count(*) > 1;
-- Prospective conservative legacy benefit backfill; review cancelled/unpaid records.
select b.status, count(*) from bookings b join profiles p on p.id=b.customer_id
where b.category_key='home' and p.role='customer' group by b.status;
select tablename, rowsecurity from pg_tables where schemaname='public';
