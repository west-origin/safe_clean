// RBAC R3 검증: V001~V066 을 적용하고 activity_logs 가 쓰기만 되고 고치기·지우기·비우기는 막히는지 확인한다.
// 사용: node scripts/verify-rbac-v066.mjs <별도로 설치한 @electric-sql/pglite 의 dist 경로> backend/src/main/resources/db/migration
// (앱 의존성이 아니다 — PGlite 는 임시 폴더에 따로 설치해 경로만 넘긴다. Docker 가 없어 IT 를 못 돌릴 때의 대체 검증)
import { readdir, readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
import { pathToFileURL } from 'node:url';
import path from 'node:path';

const [dist, migDir] = [process.argv[2], process.argv[3]];
const { PGlite } = await import(pathToFileURL(path.join(dist, 'index.js')).href);
const { btree_gist } = await import(pathToFileURL(path.join(dist, 'contrib/btree_gist.js')).href);
const { pgcrypto } = await import(pathToFileURL(path.join(dist, 'contrib/pgcrypto.js')).href);
const db = new PGlite({ extensions: { btree_gist, pgcrypto } });
const q = async (sql, p) => (await db.query(sql, p)).rows;
// Supabase 재현: 클라이언트 역할 + public 기본 권한 + Flyway 이력 표
await db.exec(`
  create role anon nologin; create role authenticated nologin; create role service_role nologin;
  alter default privileges in schema public grant all on tables to anon, authenticated, service_role;
  alter default privileges in schema public grant all on functions to anon, authenticated, service_role;
  create table flyway_schema_history (installed_rank int primary key, version text, description text, script text, success boolean);
`);
const files = (await readdir(migDir)).filter(f => /^V\d+__.*\.sql$/.test(f)).sort((a, b) => parseInt(a.slice(1)) - parseInt(b.slice(1)));
for (const f of files.filter(f => parseInt(f.slice(1)) <= 66)) {
  try {
    await db.exec('begin;' + await readFile(path.join(migDir, f), 'utf8') + ';commit;');
  } catch (e) {
    await db.exec('rollback;').catch(() => {});
    throw new Error(`${f} 실패: ${e.message}`, { cause: e });
  }
}
console.log('적용: V001 … V066');

// 1) 쓰기는 된다
await db.exec(`insert into activity_logs (actor_id, actor_role, action, target_type, target_id, after_data) values (gen_random_uuid(), 'admin', 'notice.create', 'notice', 'n1', '{"title":"t"}')`);
assert.equal((await q('select count(*)::int c from activity_logs'))[0].c, 1);
// 2) 고치기·지우기·비우기는 표 소유자도 막힌다
await assert.rejects(db.exec(`update activity_logs set reason = 'x'`), /고치거나 지울 수 없어요/);
await assert.rejects(db.exec(`delete from activity_logs`), /고치거나 지울 수 없어요/);
await assert.rejects(db.exec(`truncate activity_logs`), /고치거나 지울 수 없어요/);
assert.equal((await q('select count(*)::int c from activity_logs'))[0].c, 1);
// 3) RLS 켬, 앱 키 권한 없음(V064 기본 권한 회수 덕분), 트리거 함수도 앱 키가 실행할 수 없다
assert.equal((await q(`select relrowsecurity from pg_class where relname='activity_logs'`))[0].relrowsecurity, true);
for (const r of ['anon', 'authenticated']) {
  assert.equal((await q(`select has_table_privilege('${r}', 'activity_logs', 'SELECT,INSERT,UPDATE,DELETE') p`))[0].p, false, r);
  assert.equal((await q(`select has_function_privilege('${r}', 'activity_logs_immutable()', 'EXECUTE') p`))[0].p, false, r);
}
console.log('V066 검증 통과: 쓰기 가능, UPDATE·DELETE·TRUNCATE 차단, RLS·앱 키 권한 없음');
