-- 테이블 설명
select n.nspname as schema_name, c.relname as table_name,
       obj_description(c.oid, 'pg_class') as description
from pg_class c join pg_namespace n on n.oid = c.relnamespace
where c.relkind in ('r', 'p')
  and n.nspname not in ('pg_catalog', 'information_schema')
  and n.nspname not like 'pg_toast%'
order by 1, 2;

-- 컬럼 설명: 필요하면 아래 조건에 and c.relname = 'bookings' 추가
select n.nspname as schema_name, c.relname as table_name,
       a.attname as column_name,
       format_type(a.atttypid, a.atttypmod) as data_type,
       col_description(c.oid, a.attnum) as description
from pg_class c join pg_namespace n on n.oid = c.relnamespace
join pg_attribute a on a.attrelid = c.oid
where c.relkind in ('r', 'p') and n.nspname = 'public'
  and a.attnum > 0 and not a.attisdropped
order by n.nspname, c.relname, a.attnum;
