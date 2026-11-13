// 금액권·선물 V070 검증: V001~V069 위에 예약 결제 한 건을 만들어 두고 V070 을 적용해, 기존 결제가 그대로 유효한지와
// 새 제약(결제 대상 하나, 선물당 살아 있는 결제 하나, 금액권당 열린 선물 하나, 잔액 범위)·서버 SQL 을 확인한다.
// 사용: node scripts/verify-gifts-v070.mjs <별도로 설치한 @electric-sql/pglite 의 dist 경로> backend/src/main/resources/db/migration
// (Docker 가 없어 GiftIT 를 못 돌릴 때의 대체 검증. 한 연결뿐이라 동시성은 GiftIT 에서만 확인된다)
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
for (const f of files.filter(f => parseInt(f.slice(1)) <= 69)) await apply(f);

const A = '00000000-0000-0000-0000-00000000000a';
const B = '00000000-0000-0000-0000-00000000000b';
const BOOKING = '00000000-0000-0000-0000-0000000000b1';
await db.exec(`insert into profiles (id, role, name, phone, signup_completed_at) values ('${A}', 'customer', '가', '+821000000010', now()), ('${B}', 'customer', '나', '+821000000011', now())`);
await db.query(`insert into bookings(id,customer_id,category_key,frequency,service_date,service_time,road_address,
    base_amount,total_amount,platform_fee,duration_minutes,starts_at,ends_at,status)
    values ($1,$2,'home','oneTime','2026-10-20','12:00','검증용',85000,85000,8500,180,'2026-10-20T03:00:00Z','2026-10-20T06:00:00Z','requested')`, [BOOKING, A]);
await db.exec(`insert into payments (booking_id, provider, amount, status) values ('${BOOKING}', 'toss', 85000, 'paid')`);
await apply(files.find(f => f.startsWith('V070__')));
console.log('적용: V001 … V070');

// 1) 기존 예약 결제는 그대로 유효(새 check 를 통과), 예약·선물 둘 다이거나 둘 다 없으면 거부
assert.equal((await q(`select count(*)::int c from payments where booking_id = '${BOOKING}' and gift_id is null`))[0].c, 1);
await assert.rejects(db.exec(`insert into payments (provider, amount, status) values ('toss', 1000, 'pending')`), /payments_one_target/);

// 2) 금액권: 잔액은 0~얼굴 금액, 상태값 제한
const voucher = (await q(`insert into vouchers(owner_id, purchaser_id, catalog_key, label, face_amount, balance, status)
  values ($1, $1, 'studio_once', '원룸 1회 청소', 69000, 69000, 'unpaid') returning id`, [A]))[0].id;
await assert.rejects(db.query(`update vouchers set balance = 70000 where id = $1`, [voucher]));
await assert.rejects(db.query(`update vouchers set balance = -1 where id = $1`, [voucher]));
await assert.rejects(db.query(`update vouchers set status = 'used_up' where id = $1`, [voucher]));

// 3) 선물: 금액권당 열린 선물(결제 대기·보냄) 하나, 자기에게 선물 금지, 메시지 200자
const gift = (await q(`insert into gifts(sender_id, recipient_id, voucher_id, method, amount, label, status, created_at)
  values ($1, $2, $3, 'purchase', 69000, '원룸 1회 청소', 'awaiting_payment', now()) returning id`, [A, B, voucher]))[0].id;
await assert.rejects(db.query(`insert into gifts(sender_id, recipient_id, voucher_id, method, amount, label, status)
  values ($1, $2, $3, 'voucher', 69000, 'x', 'sent')`, [A, B, voucher]), /gifts_one_open_per_voucher/);
await assert.rejects(db.query(`insert into gifts(sender_id, recipient_id, voucher_id, method, amount, label, status) values ($1, $1, $2, 'voucher', 1, 'x', 'cancelled')`, [A, voucher]));
await assert.rejects(db.query(`insert into gifts(sender_id, recipient_id, voucher_id, method, amount, label, status, message) values ($1, $2, $3, 'voucher', 1, 'x', 'cancelled', $4)`, [A, B, voucher, 'a'.repeat(201)]));

// 4) 선물 결제: 선물당 살아 있는 결제 하나 (실패한 결제는 남고 새로 만들 수 있다)
await db.query(`insert into payments (gift_id, provider, amount, status) values ($1, 'toss', 69000, 'pending')`, [gift]);
await assert.rejects(db.query(`insert into payments (gift_id, provider, amount, status) values ($1, 'toss', 69000, 'pending')`, [gift]), /payments_one_gift/);
await db.query(`update payments set status = 'failed' where gift_id = $1`, [gift]);
await db.query(`insert into payments (gift_id, provider, amount, status) values ($1, 'toss', 69000, 'pending')`, [gift]);

// 5) 서버 SQL (GiftStore) — 잠금 조회, 결제 확정, 소유자 이전(coalesce 로 받은 시각 유지), 만료 대상, 탈퇴 가드
await db.exec('begin');
assert.equal((await q(`select id from gifts g where g.id = $1 for update`, [gift])).length, 1);
await db.query(`update vouchers set status = 'gifting', purchased_at = $2 where id = $1 and status = 'unpaid'`, [voucher, '2026-11-13T03:00:00Z']);
await db.query(`update gifts set status = 'sent', sent_at = $2, expires_at = $3 where id = $1`, [gift, '2026-11-13T03:00:00Z', '2026-11-20T03:00:00Z']);
await db.exec('commit');
assert.deepEqual((await q(`select id from gifts where status = 'sent' and expires_at <= $1 order by expires_at limit 100`, ['2026-11-21T00:00:00Z'])).map(r => r.id), [gift]);
await db.query(`update vouchers set owner_id = $2, status = 'held', valid_until = $3, received_at = coalesce($4, received_at) where id = $1`, [voucher, B, '2027-11-13T03:00:00Z', '2026-11-14T00:00:00Z']);
await db.query(`update vouchers set owner_id = $2, status = 'held', valid_until = $3, received_at = coalesce($4, received_at) where id = $1`, [voucher, A, '2027-11-13T03:00:00Z', null]);
const v = (await q(`select owner_id, received_at from vouchers where id = $1`, [voucher]))[0];
assert.equal(v.owner_id, A);
assert.equal(new Date(v.received_at).toISOString(), '2026-11-14T00:00:00.000Z');
const guard = async (u) => (await q(`select exists(select 1 from gifts where (sender_id = $1 or recipient_id = $1) and status in ('awaiting_payment', 'sent'))
  or exists(select 1 from vouchers where owner_id = $1 and status in ('held', 'gifting') and balance > 0) as b`, [u]))[0].b;
assert.equal(await guard(A), true);
await db.query(`update vouchers set status = 'void' where id = $1`, [voucher]);
await db.query(`update gifts set status = 'accepted' where id = $1`, [gift]);
assert.equal(await guard(A), false);

// 6) 선물 목록 설정: 14개, 첫 1회 체험(50,000원) 없음, 받는 기간 7일·유효기간 365일
const catalog = (await q(`select value from app_settings where key = 'gift_catalog'`))[0].value;
assert.equal(catalog.length, 14);
assert.ok(!catalog.some(i => i.amount === 50000));
assert.ok(catalog.some(i => i.key === 'studio_once' && i.amount === 69000));
assert.equal((await q(`select value from app_settings where key = 'gift_accept_days'`))[0].value, 7);
assert.equal((await q(`select value from app_settings where key = 'voucher_valid_days'`))[0].value, 365);

// 7) RLS 켬, 앱 키 권한 없음, updated_at 트리거
for (const t of ['vouchers', 'gifts']) {
  assert.equal((await q(`select relrowsecurity from pg_class where relname = $1`, [t]))[0].relrowsecurity, true, t);
  for (const r of ['anon', 'authenticated']) assert.equal((await q(`select has_table_privilege('${r}', '${t}', 'SELECT,INSERT,UPDATE,DELETE') p`))[0].p, false, `${r} ${t}`);
}
assert.equal((await q(`select count(*)::int c from pg_trigger where tgname = 'vouchers_updated_at'`))[0].c, 1);
console.log('V070 검증 통과: 기존 예약 결제 유지, 결제 대상 하나, 선물당 결제 하나, 금액권당 열린 선물 하나, 잔액·메시지 제약, 서버 SQL, 설정 14개(체험 제외), RLS·앱 키 권한 없음');
