// 배너 4회차 검증: V001~V067 을 적용하고 home_banners 제약·RLS·앱 키 권한을 확인한다.
// 사용: node scripts/verify-banners-v067.mjs <별도로 설치한 @electric-sql/pglite 의 dist 경로> backend/src/main/resources/db/migration
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
for (const f of files.filter(f => parseInt(f.slice(1)) <= 67)) {
  try {
    await db.exec('begin;' + await readFile(path.join(migDir, f), 'utf8') + ';commit;');
  } catch (e) {
    await db.exec('rollback;').catch(() => {});
    throw new Error(`${f} 실패: ${e.message}`, { cause: e });
  }
}

// 1) 정상 행은 들어가고, 시작일 > 종료일은 거절
await db.exec(`insert into home_banners (title, image_path, starts_on, ends_on, sort_order) values ('첫 청소 쿠폰', 'a/1.png', '2026-11-01', '2026-12-31', 1)`);
await db.exec(`insert into home_banners (title, image_path, sort_order) values ('기간 없음', 'b/1.png', 2)`);
await assert.rejects(db.exec(`insert into home_banners (title, image_path, starts_on, ends_on, sort_order) values ('거꾸로', 'c/1.png', '2026-12-31', '2026-11-01', 3)`));
assert.equal((await q(`select description from home_banners where title = '기간 없음'`))[0].description, ''); // 설명 기본값
// 2) RLS 켬, 앱 키 권한 없음 (V064 기본 권한 회수)
assert.equal((await q(`select relrowsecurity from pg_class where relname='home_banners'`))[0].relrowsecurity, true);
for (const r of ['anon', 'authenticated']) {
  assert.equal((await q(`select has_table_privilege('${r}', 'home_banners', 'SELECT,INSERT,UPDATE,DELETE') p`))[0].p, false, r);
}
console.log('V067 검증 통과: 기간 제약, 설명 기본값, RLS·앱 키 권한 없음');
