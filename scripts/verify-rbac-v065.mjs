// RBAC R2 검증: V001~V064 를 적용하고 매니저 데이터를 넣은 뒤 V065 를 실행해 데이터 이전·제약을 확인한다.
// 사용: node scripts/verify-rbac-v065.mjs <별도로 설치한 @electric-sql/pglite 의 dist 경로> backend/src/main/resources/db/migration
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
// Supabase 의 클라이언트 역할 (V052 등이 이 역할 이름으로 revoke 한다)
await db.exec(`create role anon nologin; create role authenticated nologin; create role service_role nologin;
  create table flyway_schema_history (installed_rank int primary key, version text, description text, script text, success boolean);`);

const files = (await readdir(migDir)).filter(f => /^V\d+__.*\.sql$/.test(f))
  .sort((a, b) => parseInt(a.slice(1)) - parseInt(b.slice(1)));
const run = async (f) => {
  try {
    await db.exec('begin;' + await readFile(path.join(migDir, f), 'utf8') + ';commit;');
  } catch (e) {
    await db.exec('rollback;').catch(() => {});
    throw new Error(`${f} 실패: ${e.message}`, { cause: e });
  }
};
for (const f of files.filter(f => parseInt(f.slice(1)) <= 64)) await run(f);

// V065 전 데이터: 승인 매니저 · 미승인 매니저 · 탈퇴 매니저(승인 상태로 남은 예전 데이터 포함) · 고객
const ids = { active: '00000000-0000-0000-0000-000000000001', pending: '00000000-0000-0000-0000-000000000002', gone: '00000000-0000-0000-0000-000000000003', customer: '00000000-0000-0000-0000-000000000004' };
await db.exec(`
  insert into profiles (id, role, name, phone, deleted_at) values
    ('${ids.active}', 'manager', '가', '+821000000001', null),
    ('${ids.pending}', 'manager', '나', '+821000000002', null),
    ('${ids.gone}', 'manager', '다', '+821000000003', now()),
    ('${ids.customer}', 'customer', '라', '+821000000004', null);
  insert into manager_profiles (manager_id, approved) values ('${ids.active}', true), ('${ids.pending}', false), ('${ids.gone}', true);
`);
await run(files.find(f => f.startsWith('V065__')));
console.log('적용: V001 … V065');

// 1) 데이터 이전
const statuses = Object.fromEntries((await q('select manager_id, status, status_changed_at from manager_profiles')).map(r => [r.manager_id, r]));
assert.equal(statuses[ids.active].status, 'active');
assert.equal(statuses[ids.pending].status, 'pending');
assert.equal(statuses[ids.gone].status, 'withdrawn');
assert.ok(Object.values(statuses).every(r => r.status_changed_at));
// 2) approved 칸이 없어졌다
assert.equal((await q(`select 1 from information_schema.columns where table_name='manager_profiles' and column_name='approved'`)).length, 0);
// 3) 새 역할 두 개를 받고, 모르는 역할은 거절
await db.exec(`insert into profiles (id, role, name, phone) values ('00000000-0000-0000-0000-000000000005', 'regional_manager', '마', '+821000000005'),
                                                                  ('00000000-0000-0000-0000-000000000006', 'admin', '바', '+821000000006')`);
await assert.rejects(db.exec(`insert into profiles (id, role, name, phone) values ('00000000-0000-0000-0000-000000000007', 'owner', '사', '+821000000007')`));
// 4) 새 매니저 행은 pending 으로 시작하고, 모르는 상태는 거절
await db.exec(`insert into manager_profiles (manager_id) values ('00000000-0000-0000-0000-000000000005')`);
assert.equal((await q(`select status from manager_profiles where manager_id='00000000-0000-0000-0000-000000000005'`))[0].status, 'pending');
await assert.rejects(db.exec(`update manager_profiles set status='fired' where manager_id='${ids.active}'`));
for (const s of ['suspended', 'dormant', 'withdrawn', 'active']) await db.exec(`update manager_profiles set status='${s}' where manager_id='${ids.active}'`);
// 5) V064 이후라 새 칸이 생겨도 RLS·권한은 그대로(표 단위)
assert.equal((await q(`select relrowsecurity from pg_class where relname='manager_profiles'`))[0].relrowsecurity, true);
console.log('V065 검증 통과: 이전(active·pending·withdrawn), approved 삭제, 역할 4개, 상태 5개 제약');
