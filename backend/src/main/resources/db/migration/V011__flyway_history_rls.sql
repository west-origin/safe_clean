-- V011__flyway_history_rls.sql — flyway_schema_history 를 anon·authenticated 가 못 읽게 (검수 2차)
-- Supabase 는 public 테이블을 PostgREST 로 노출하고 anon·authenticated 에 기본 권한을 준다. V001 의 RLS 루프가 이 표만 빼서
-- anon 키로 마이그레이션 이력을 읽거나 checksum 을 바꿀 수 있었다(다음 기동 때 validate 실패).
--
-- ※ 이 표에 `alter table … enable row level security` 를 하면 안 된다 (2026-09-21 첫 실제 기동에서 확인): Flyway 는 연결 두 개를 쓰고
--    한쪽이 이력 표를 읽는 트랜잭션(ACCESS SHARE)을 연 채로 다른 쪽에서 마이그레이션을 돌린다 → ALTER TABLE(ACCESS EXCLUSIVE)이
--    자기 자신을 기다리다 statement timeout(2분)으로 실패한다. REVOKE 는 표 잠금이 필요 없어 그대로 된다. 권한을 거두면 PostgREST 로는 보이지 않는다.
-- anon·authenticated 역할은 Supabase 에만 있다 (Testcontainers Postgres 에는 없음) — 있을 때만 권한을 거둔다
do $$ begin
  if exists (select 1 from pg_roles where rolname = 'anon') then execute 'revoke all on flyway_schema_history from anon'; end if;
  if exists (select 1 from pg_roles where rolname = 'authenticated') then execute 'revoke all on flyway_schema_history from authenticated'; end if;
end $$;
