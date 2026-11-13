// 설정 메뉴 V073 검증: V001~V073 을 적용하고 '친구·선물' 그룹 rows 가 친구 → 선물하기 → 선물함 순서인지,
// 다른 그룹 순서·내용이 그대로인지, 다시 실행해도 한 번만 들어가는지 확인한다.
// 사용: node scripts/verify-gift-menu-v073.mjs <별도로 설치한 @electric-sql/pglite 의 dist 경로> backend/src/main/resources/db/migration
import { readdir, readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
import { pathToFileURL } from 'node:url';
import path from 'node:path';

const [dist, migDir] = [process.argv[2], process.argv[3]];
const { PGlite } = await import(pathToFileURL(path.join(dist, 'index.js')).href);
const { btree_gist } = await import(pathToFileURL(path.join(dist, 'contrib/btree_gist.js')).href);
const { pgcrypto } = await import(pathToFileURL(path.join(dist, 'contrib/pgcrypto.js')).href);
const db = new PGlite({ extensions: { btree_gist, pgcrypto } });
await db.exec(`
  create role anon nologin; create role authenticated nologin; create role service_role nologin;
  alter default privileges in schema public grant all on tables to anon, authenticated, service_role;
  create table flyway_schema_history (installed_rank int primary key, version text, description text, script text, success boolean);
`);
const files = (await readdir(migDir)).filter(f => /^V\d+__.*\.sql$/.test(f)).sort((a, b) => parseInt(a.slice(1)) - parseInt(b.slice(1)));
const groups = async () => (await db.query(`select value from app_settings where key = 'settings_groups'`)).rows[0].value;
let before;
for (const f of files.filter(f => parseInt(f.slice(1)) <= 73)) {
  if (parseInt(f.slice(1)) === 73) before = await groups();
  try {
    await db.exec('begin;' + await readFile(path.join(migDir, f), 'utf8') + ';commit;');
  } catch (e) {
    await db.exec('rollback;').catch(() => {});
    throw new Error(`${f} 실패: ${e.message}`, { cause: e });
  }
}
const after = await groups();
const friends = after.find(g => g.title === '친구·선물');
assert.deepEqual(friends.rows.map(r => r.key), ['friends', 'gifts', 'giftBox']);
assert.deepEqual(friends.rows.map(r => r.label), ['친구', '선물하기', '선물함']);
assert.deepEqual(after.map(g => g.title), before.map(g => g.title)); // 그룹 순서 그대로
for (const g of after.filter(g => g.title !== '친구·선물')) assert.deepEqual(g, before.find(b => b.title === g.title)); // 다른 그룹 그대로
// 다시 실행해도 한 번만
await db.exec(await readFile(path.join(migDir, files.find(f => f.startsWith('V073__'))), 'utf8'));
assert.equal((await groups()).find(g => g.title === '친구·선물').rows.length, 3);
console.log('V073 검증 통과: 친구·선물 그룹 rows = 친구 · 선물하기 · 선물함, 다른 그룹·순서 그대로, 다시 실행해도 한 번만');
