// RBAC R1 검증: Supabase 와 같은 기본 권한 상태에서 V001~V064 를 Flyway 처럼 차례로 실행하고 앱 키 권한을 확인한다.
// 사용: node scripts/verify-rbac-v064.mjs <별도로 설치한 @electric-sql/pglite 의 dist 경로> backend/src/main/resources/db/migration [마지막 버전]
// (앱 의존성이 아니다 — PGlite 는 임시 폴더에 따로 설치해 경로만 넘긴다. Docker 가 없어 IT 를 못 돌릴 때의 대체 검증)
import { readdir, readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
import { pathToFileURL } from 'node:url';
import path from 'node:path';

const [dist, migDir, stopAt] = [process.argv[2], process.argv[3], process.argv[4] ?? '999'];
const { PGlite } = await import(pathToFileURL(path.join(dist, 'index.js')).href);
const { btree_gist } = await import(pathToFileURL(path.join(dist, 'contrib/btree_gist.js')).href);
const { pgcrypto } = await import(pathToFileURL(path.join(dist, 'contrib/pgcrypto.js')).href);
const db = new PGlite({ extensions: { btree_gist, pgcrypto } });

// Supabase 재현: 클라이언트 역할 + postgres 의 public 기본 권한(실측값과 같은 모양)
await db.exec(`
  create role anon nologin; create role authenticated nologin; create role service_role nologin;
  alter default privileges in schema public grant all on tables to anon, authenticated, service_role;
  alter default privileges in schema public grant all on sequences to anon, authenticated, service_role;
  alter default privileges in schema public grant all on functions to anon, authenticated, service_role;
  -- Flyway 가 마이그레이션 전에 만드는 이력 표 (기본 권한을 받는다)
  create table flyway_schema_history (installed_rank int primary key, version text, description text, script text, success boolean);
`);

const files = (await readdir(migDir)).filter(f => /^V\d+__.*\.sql$/.test(f))
  .sort((a, b) => parseInt(a.slice(1)) - parseInt(b.slice(1)))
  .filter(f => parseInt(f.slice(1)) <= parseInt(stopAt));
for (const f of files) {
  try {
    await db.exec('begin;' + await readFile(path.join(migDir, f), 'utf8') + ';commit;');
  } catch (e) {
    await db.exec('rollback;').catch(() => {});
    throw new Error(`${f} 실패: ${e.message}`, { cause: e });
  }
}
console.log(`적용: ${files[0]} … ${files.at(-1)} (${files.length}개)`);

const q = async (sql, p) => (await db.query(sql, p)).rows;
const leaks = async () => ({
  tables: await q(`select c.relname, r.rolname from pg_class c join pg_namespace n on n.oid=c.relnamespace
      cross join (values ('anon'),('authenticated')) r(rolname)
      where n.nspname='public' and c.relkind in ('r','p','v','m')
        and has_table_privilege(r.rolname, c.oid, 'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')`),
  sequences: await q(`select c.relname, r.rolname from pg_class c join pg_namespace n on n.oid=c.relnamespace
      cross join (values ('anon'),('authenticated')) r(rolname)
      where n.nspname='public' and c.relkind='S' and has_sequence_privilege(r.rolname, c.oid::regclass::text, 'USAGE,SELECT,UPDATE')`),
  functions: await q(`select p.proname, r.rolname from pg_proc p join pg_namespace n on n.oid=p.pronamespace
      cross join (values ('anon'),('authenticated')) r(rolname)
      where n.nspname='public' and not exists (select 1 from pg_depend d where d.objid=p.oid and d.deptype='e')
        and has_function_privilege(r.rolname, p.oid, 'EXECUTE')`),
});

const before = await leaks();
console.log('누출(표/시퀀스/함수):', before.tables.length, before.sequences.length, before.functions.length);
if (parseInt(stopAt) < 64) {
  console.log(JSON.stringify(before, null, 1).slice(0, 1500));
  process.exit(0);
}

// 1) V064 뒤 기존 객체에 앱 키 권한이 하나도 없다
assert.deepEqual(before, { tables: [], sequences: [], functions: [] });
const rls = await q(`select c.relname from pg_class c join pg_namespace n on n.oid=c.relnamespace
    where n.nspname='public' and c.relkind='r' and not c.relrowsecurity and c.relname <> 'flyway_schema_history'`);
assert.deepEqual(rls, [], 'flyway 이력 말고 RLS 꺼진 표가 없다');

// 2) 앞으로 만드는 표·시퀀스·함수도 앱 키에 열리지 않는다 (다음 마이그레이션이 revoke 를 빠뜨린 경우)
await db.exec(`create table rbac_future (id bigserial primary key, v text);
  create function rbac_future_fn() returns int language sql as 'select 1';`);
const after = await leaks();
assert.deepEqual(after, { tables: [], sequences: [], functions: [] }, '새 객체 누출');
// 서버 쪽(service_role)은 그대로 받는다
assert.equal((await q(`select has_table_privilege('service_role','rbac_future','SELECT,INSERT') ok`))[0].ok, true);

// 3) set_updated_at search_path 고정
const cfg = (await q(`select proconfig from pg_proc where proname='set_updated_at'`))[0].proconfig;
assert.deepEqual(cfg, ['search_path=""']);

// 4) EXECUTE 를 거둔 트리거 함수가 표 권한만 있는 역할의 UPDATE 에서도 그대로 동작한다
// app_like = 서버 계정처럼 RLS 를 우회하지만 함수 소유자는 아닌 역할 (service_role·앞으로의 앱 전용 계정과 같은 처지)
await db.exec(`create role app_like nologin bypassrls; grant select, insert, update on profiles to app_like;
  insert into profiles(id, role, name, phone, updated_at) values ('00000000-0000-4000-8000-000000000001','customer','a','+821000000000', '2000-01-01');
  set role app_like; update profiles set name='b' where id='00000000-0000-4000-8000-000000000001'; reset role;`);
const upd = (await q(`select updated_at > '2001-01-01' ok from profiles`))[0].ok;
assert.equal(upd, true, '트리거가 updated_at 을 갱신한다');
assert.equal((await q(`select has_function_privilege('app_like','set_updated_at()','EXECUTE') ok`))[0].ok, false);

// 5) 다시 실행해도 깨지지 않는다(멱등) — Flyway 는 한 번만 돌지만 수동 복구 대비
await db.exec(await readFile(path.join(migDir, files.at(-1)), 'utf8'));
console.log('V064 검증 통과: 기존·신규 객체 앱 키 권한 0, RLS 안전망 유지, search_path 고정, 트리거 동작, 재실행 가능');
