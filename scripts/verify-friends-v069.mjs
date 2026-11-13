// 고객 친구 V069 검증: V001~V068 위에 기존 고객을 만들어 두고 V069 를 적용해 태그 채우기·제약·서버 SQL 을 확인한다.
// 사용: node scripts/verify-friends-v069.mjs <별도로 설치한 @electric-sql/pglite 의 dist 경로> backend/src/main/resources/db/migration
// (앱 의존성이 아니다 — PGlite 는 임시 폴더에 따로 설치해 경로만 넘긴다. Docker 가 없어 FriendIT 를 못 돌릴 때의 대체 검증.
//  한 연결뿐이라 동시 요청·advisory lock 의 기다림은 FriendIT 에서만 확인된다)
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
await db.exec(`
  create role anon nologin; create role authenticated nologin; create role service_role nologin;
  alter default privileges in schema public grant all on tables to anon, authenticated, service_role;
  create table flyway_schema_history (installed_rank int primary key, version text, description text, script text, success boolean);
`);
const files = (await readdir(migDir)).filter(f => /^V\d+__.*\.sql$/.test(f)).sort((a, b) => parseInt(a.slice(1)) - parseInt(b.slice(1)));
const apply = async (f) => {
  try {
    await db.exec('begin;' + await readFile(path.join(migDir, f), 'utf8') + ';commit;');
  } catch (e) {
    await db.exec('rollback;').catch(() => {});
    throw new Error(`${f} 실패: ${e.message}`, { cause: e });
  }
};
for (const f of files.filter(f => parseInt(f.slice(1)) <= 68)) await apply(f);

// V069 전의 고객: 같은 닉네임(대소문자만 다름) 3명, 닉네임 없음 1명, 탈퇴 1명, 매니저 1명
const id = (n) => `00000000-0000-0000-0000-00000000000${n}`;
await db.exec(`
  insert into profiles (id, role, name, nickname, phone, signup_completed_at) values
    ('${id(1)}', 'customer', '김일', 'Clean', '+821000000001', now()),
    ('${id(2)}', 'customer', '김이', 'clean', '+821000000002', now()),
    ('${id(3)}', 'customer', '김삼', 'CLEAN', '+821000000003', now()),
    ('${id(4)}', 'customer', '김사', null, '+821000000004', now()),
    ('${id(6)}', 'manager', '김육', null, '+821000000006', now());
  insert into profiles (id, role, name, nickname, phone, deleted_at) values ('${id(5)}', 'customer', '탈퇴한 사용자', 'clean', 'deleted-5', now());
`);
await apply(files.find(f => f.startsWith('V069__')));
console.log('적용: V001 … V069');

// 1) 태그 채우기: 같은 닉네임(대소문자 무시)끼리 서로 다른 4자리, 닉네임 없음·탈퇴는 비어 있다
const tags = await q(`select id, friend_tag from profiles order by id`);
const byId = Object.fromEntries(tags.map(r => [r.id, r.friend_tag]));
for (const n of [1, 2, 3]) assert.match(byId[id(n)], /^[0-9]{4}$/);
assert.equal(new Set([1, 2, 3].map(n => byId[id(n)])).size, 3, '같은 닉네임 태그가 겹친다');
assert.equal(byId[id(4)], null);
assert.equal(byId[id(5)], null);
assert.equal(byId[id(6)], null);

// 2) 같은 (소문자 닉네임, 태그) 는 살아 있는 계정끼리 금지
await assert.rejects(db.exec(`update profiles set friend_tag = '${byId[id(1)]}' where id = '${id(2)}'`), /profiles_friend_handle/);
await assert.rejects(db.exec(`update profiles set friend_tag = '12a4' where id = '${id(4)}'`));

// 3) 요청: 두 사람 사이 대기 요청은 방향 무관 하나, 자기 자신 금지, 수락된 이력은 여러 개 가능
await db.exec(`insert into friend_requests (requester_id, addressee_id) values ('${id(1)}', '${id(2)}')`);
await assert.rejects(db.exec(`insert into friend_requests (requester_id, addressee_id) values ('${id(2)}', '${id(1)}')`), /friend_requests_one_pending/);
await assert.rejects(db.exec(`insert into friend_requests (requester_id, addressee_id) values ('${id(1)}', '${id(1)}')`));
await db.exec(`update friend_requests set status = 'accepted', decided_at = now()`);
await db.exec(`insert into friend_requests (requester_id, addressee_id, status, source, decided_at) values ('${id(2)}', '${id(1)}', 'accepted', 'invite', now())`);
await assert.rejects(db.exec(`insert into friend_requests (requester_id, addressee_id, status) values ('${id(2)}', '${id(3)}', 'maybe')`));

// 4) 친구 관계: 작은 id 가 앞, 한 쌍 한 행 — 서버 SQL(least/greatest, on conflict do nothing) 그대로
const addFriend = (a, b) => db.query(`insert into friendships(user_a, user_b, created_at) values (least($1::uuid, $2::uuid), greatest($1::uuid, $2::uuid), now()) on conflict do nothing`, [a, b]);
await addFriend(id(2), id(1));
await addFriend(id(1), id(2));
assert.equal((await q('select count(*)::int c from friendships'))[0].c, 1);
await assert.rejects(db.exec(`insert into friendships (user_a, user_b) values ('${id(3)}', '${id(1)}')`));

// 5) 차단·초대 제약
await assert.rejects(db.exec(`insert into friend_blocks (blocker_id, blocked_id) values ('${id(1)}', '${id(1)}')`));
await db.exec(`insert into friend_blocks (blocker_id, blocked_id) values ('${id(1)}', '${id(3)}')`);
await db.exec(`insert into friend_invites (code, inviter_id, expires_at) values ('Abcdef2345', '${id(1)}', now() + interval '30 days')`);
await assert.rejects(db.exec(`insert into friend_invites (code, inviter_id, expires_at) values ('bad-code', '${id(1)}', now())`));

// 6) 서버가 쓰는 잠금 SQL 이 실행된다 (FriendStore.lockPair · ProfileRepository.advisoryLock)
await db.exec('begin');
assert.equal(Number((await q(`select count(*) c from (select pg_advisory_xact_lock(hashtextextended($1, 0))) x`, ['friend:a:b']))[0].c), 1);
await db.exec('commit');

// 7) 친구 목록 SQL (FriendStore.friends) — 탈퇴한 친구는 빠진다
const friendsOf = async (me) => q(`
  select p.id from friendships f join profiles p on p.id = case when f.user_a = $1 then f.user_b else f.user_a end
  where (f.user_a = $1 or f.user_b = $1) and p.role = 'customer' and p.deleted_at is null and p.signup_completed_at is not null`, [me]);
assert.deepEqual((await friendsOf(id(1))).map(r => r.id), [id(2)]);
await db.exec(`update profiles set deleted_at = now(), nickname = null where id = '${id(2)}'`);
assert.equal((await friendsOf(id(1))).length, 0);

// 8) RLS 켬, 앱 키 권한 없음 (V064 기본 권한 회수)
for (const t of ['friend_requests', 'friendships', 'friend_blocks', 'friend_invites']) {
  assert.equal((await q(`select relrowsecurity from pg_class where relname = $1`, [t]))[0].relrowsecurity, true, t);
  for (const r of ['anon', 'authenticated']) assert.equal((await q(`select has_table_privilege('${r}', '${t}', 'SELECT,INSERT,UPDATE,DELETE') p`))[0].p, false, `${r} ${t}`);
}
console.log('V069 검증 통과: 태그 채우기·유일성, 대기 요청 쌍당 1건, 친구 한 쌍 한 행, 차단·초대 제약, 잠금 SQL, 탈퇴 제외, RLS·앱 키 권한 없음');
