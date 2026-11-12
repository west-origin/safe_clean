-- V064__revoke_client_role_defaults.sql — 앱 키(anon·authenticated)로 public 스키마에 닿는 길을 모두 막는다 (RBAC R1, backend/docs/rbac.md §13)
--
-- 배경 (2026-11-12 safe_clean_test 실측):
--  · Supabase 는 public 의 '기본 권한(default privileges)' 으로, 표 소유자(postgres = Flyway 접속 계정)가 앞으로 만드는
--    표·시퀀스·함수마다 anon·authenticated 에 전체 권한을 자동으로 준다. 지금 있는 표는 회수돼 있지만, 새 표는 만들어지는 순간
--    앱 키에 열리고 RLS 하나만 남는다(만드는 마이그레이션이 revoke 를 빠뜨리면 그대로 노출).
--  · 시퀀스 booking_status_history_id_seq 와 트리거 함수 3개에 anon·authenticated 권한이 남아 있다.
-- 앱은 Supabase 데이터를 직접 부르지 않는다(고객 앱은 Auth 소셜 로그인만). 서버는 표 소유자·service_role 로 접근하므로 영향 없음.
--
-- flyway_schema_history 에는 RLS 를 켜지 않는다 — Flyway 가 자기 잠금과 엉켜 기동이 실패한다(V011 주석). 권한 회수만 다시 확인한다.
-- 표 잠금이 필요한 문장이 없다(REVOKE·ALTER DEFAULT PRIVILEGES·ALTER FUNCTION 은 표를 잠그지 않는다).
-- anon·authenticated 역할은 Supabase 에만 있다(Testcontainers·PGlite 에는 없을 수 있음) — 있을 때만 실행한다.

do $$
declare
  r text;
  f regprocedure;
begin
  foreach r in array array['anon', 'authenticated'] loop
    if exists (select 1 from pg_roles where rolname = r) then
      -- 1) 앞으로 이 마이그레이션 계정(current_user)이 public 에 만드는 객체: 앱 키에 자동으로 권한을 주지 않는다
      execute format('alter default privileges in schema public revoke all on tables from %I', r);
      execute format('alter default privileges in schema public revoke all on sequences from %I', r);
      execute format('alter default privileges in schema public revoke all on functions from %I', r);
      -- 2) 지금 있는 표(flyway_schema_history 포함)·시퀀스에서 회수
      execute format('revoke all on all tables in schema public from %I', r);
      execute format('revoke all on all sequences in schema public from %I', r);
    end if;
  end loop;

  -- 3) 우리 함수(확장이 만든 함수는 빼고 — btree_gist·pgcrypto 는 확장이 관리한다)의 실행 권한.
  --    함수는 PUBLIC(모든 역할)에도 실행 권한이 기본으로 붙으므로 PUBLIC 에서도 거둔다.
  --    트리거 함수는 트리거가 실행될 때 EXECUTE 권한을 검사하지 않으므로 서버 동작에 영향이 없다.
  for f in
    select p.oid::regprocedure
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and not exists (select 1 from pg_depend d where d.objid = p.oid and d.deptype = 'e')
  loop
    execute format('revoke all on function %s from public', f);
    foreach r in array array['anon', 'authenticated'] loop
      if exists (select 1 from pg_roles where rolname = r) then
        execute format('revoke all on function %s from %I', f, r);
      end if;
    end loop;
  end loop;
end $$;

-- 앞으로 이 계정이 만드는 함수도 PUBLIC 실행 권한 없이 만든다.
-- 함수의 PUBLIC 실행 권한은 Postgres 의 '전역' 기본값이라 'in schema public' 으로는 거둘 수 없다(스키마 단위 기본 권한은 더하기만 한다).
-- 그래서 스키마를 적지 않는다 — 이 계정(Flyway 접속 계정)이 어느 스키마에 만드는 함수든 적용된다. 우리 함수는 전부 Flyway 가 public 에 만든다
alter default privileges revoke execute on functions from public;

-- 4) search_path 고정 (Supabase 보안 권고 function_search_path_mutable). 본문은 now() 뿐이라 pg_catalog 만 있으면 된다
alter function set_updated_at() set search_path = '';
