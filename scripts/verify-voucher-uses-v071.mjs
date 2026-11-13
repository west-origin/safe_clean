// 금액권 사용·환불 V071 검증: V001~V071 을 적용하고 사용 원장(예약당 1장)·환불 요청(금액권당 진행 중 1건, 카드/계좌 칸 규칙)·
// 새 금액권 상태·서버 SQL(사용 금액 합, 취소된 예약의 사용 찾기, 처리 때 계좌 암호문 삭제)을 확인한다.
// 사용: node scripts/verify-voucher-uses-v071.mjs <별도로 설치한 @electric-sql/pglite 의 dist 경로> backend/src/main/resources/db/migration
// (Docker 가 없어 VoucherIT 를 못 돌릴 때의 대체 검증. 한 연결뿐이라 동시성은 VoucherIT 에서만 확인된다)
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
for (const f of files.filter(f => parseInt(f.slice(1)) <= 71)) {
  try {
    await db.exec('begin;' + await readFile(path.join(migDir, f), 'utf8') + ';commit;');
  } catch (e) {
    await db.exec('rollback;').catch(() => {});
    throw new Error(`${f} 실패: ${e.message}`, { cause: e });
  }
}
console.log('적용: V001 … V071');

const A = '00000000-0000-0000-0000-00000000000a';
await db.exec(`insert into profiles (id, role, name, phone, signup_completed_at) values ('${A}', 'customer', '가', '+821000000010', now())`);
const booking = async (status) => (await q(`insert into bookings(id,customer_id,category_key,frequency,service_date,service_time,road_address,
    base_amount,total_amount,platform_fee,duration_minutes,starts_at,ends_at,status)
    values (gen_random_uuid(),$1,'home','oneTime','2026-11-20','12:00','검증용',85000,85000,8500,180,'2026-11-20T03:00:00Z','2026-11-20T06:00:00Z',$2) returning id`, [A, status]))[0].id;
const voucher = async () => (await q(`insert into vouchers(owner_id, purchaser_id, catalog_key, label, face_amount, balance, status)
  values ($1, $1, 'studio_once', '원룸', 140000, 140000, 'held') returning id`, [A]))[0].id;

// 1) 새 상태값
const v1 = await voucher();
for (const s of ['used', 'refund_requested', 'refunded', 'held']) await db.query(`update vouchers set status = $2 where id = $1`, [v1, s]);
await assert.rejects(db.query(`update vouchers set status = 'expired' where id = $1`, [v1]));

// 2) 사용: 예약당 쓰는 중 하나, 되돌린 뒤엔 다시 쓸 수 있다, 금액 > 0
const b1 = await booking('requested');
await db.query(`insert into voucher_uses(voucher_id, booking_id, customer_id, amount, status) values ($1, $2, $3, 85000, 'applied')`, [v1, b1, A]);
const v2 = await voucher();
await assert.rejects(db.query(`insert into voucher_uses(voucher_id, booking_id, customer_id, amount, status) values ($1, $2, $3, 1000, 'applied')`, [v2, b1, A]), /voucher_uses_one_per_booking/);
await assert.rejects(db.query(`insert into voucher_uses(voucher_id, booking_id, customer_id, amount, status) values ($1, $2, $3, 0, 'restored')`, [v2, b1, A]));
assert.equal(Number((await q(`select coalesce(sum(amount), 0) s from voucher_uses where booking_id = $1 and status = 'applied'`, [b1]))[0].s), 85000);

// 3) 취소된 예약의 사용 찾기 (VoucherStore.appliedOnCancelledBookings) → 되돌리기 (applied 일 때만 1행)
await db.query(`update bookings set status = 'cancelled' where id = $1`, [b1]);
const due = await q(`select u.id from voucher_uses u join bookings b on b.id = u.booking_id where u.status = 'applied' and b.status = 'cancelled' order by u.created_at limit 100`);
assert.equal(due.length, 1);
const restore = (id) => db.query(`update voucher_uses set status = 'restored', restored_at = now(), restore_reason = 'booking_cancelled' where id = $1 and status = 'applied'`, [id]);
assert.equal((await restore(due[0].id)).affectedRows, 1);
assert.equal((await restore(due[0].id)).affectedRows, 0); // 두 번째는 아무것도 안 한다
await db.query(`insert into voucher_uses(voucher_id, booking_id, customer_id, amount, status) values ($1, $2, $3, 1000, 'applied')`, [v2, b1, A]); // 되돌린 뒤 다시

// 4) 환불: 금액권당 진행 중 하나, 카드는 계좌 칸 없음 / 계좌는 은행 필수, 비율 95|100
await db.query(`insert into voucher_refunds(voucher_id, owner_id, method, rate, base_amount, amount, status) values ($1, $2, 'card', 100, 140000, 140000, 'processing')`, [v1, A]);
await assert.rejects(db.query(`insert into voucher_refunds(voucher_id, owner_id, method, rate, base_amount, amount, status, bank_name) values ($1, $2, 'bank', 100, 1, 1, 'requested', '국민')`, [v1, A]), /voucher_refunds_one_open/);
await assert.rejects(db.query(`insert into voucher_refunds(voucher_id, owner_id, method, rate, base_amount, amount, status) values ($1, $2, 'bank', 100, 1, 1, 'requested')`, [v2, A]));
await assert.rejects(db.query(`insert into voucher_refunds(voucher_id, owner_id, method, rate, base_amount, amount, status, bank_name) values ($1, $2, 'card', 100, 1, 1, 'processing', '국민')`, [v2, A]));
await assert.rejects(db.query(`insert into voucher_refunds(voucher_id, owner_id, method, rate, base_amount, amount, status, bank_name) values ($1, $2, 'bank', 90, 1, 1, 'requested', '국민')`, [v2, A]));
const r2 = (await q(`insert into voucher_refunds(voucher_id, owner_id, method, rate, base_amount, amount, status, bank_name, account_holder, account_cipher, account_last4)
  values ($1, $2, 'bank', 95, 60000, 57000, 'requested', '국민은행', '가', 'CIPHER', '9012') returning id`, [v2, A]))[0].id;
// 카드 환불 PG 확인 (VoucherStore.cardRefundDone)
await db.query(`update voucher_refunds set status = 'done', decided_at = now() where voucher_id = $1 and method = 'card' and status = 'processing'`, [v1]);
// 어드민 완료: 암호문 삭제, 끝 4자리 남음 (VoucherStore.decideRefund)
await db.query(`update voucher_refunds set status = 'paid', decided_at = now(), decided_by = $2, note = null, account_cipher = null where id = $1`, [r2, A]);
const r = (await q(`select account_cipher, account_last4, status from voucher_refunds where id = $1`, [r2]))[0];
assert.equal(r.account_cipher, null);
assert.equal(r.account_last4, '9012');

// 5) RLS 켬, 앱 키 권한 없음
for (const t of ['voucher_uses', 'voucher_refunds']) {
  assert.equal((await q(`select relrowsecurity from pg_class where relname = $1`, [t]))[0].relrowsecurity, true, t);
  for (const role of ['anon', 'authenticated']) assert.equal((await q(`select has_table_privilege('${role}', '${t}', 'SELECT,INSERT,UPDATE,DELETE') p`))[0].p, false, `${role} ${t}`);
}
console.log('V071 검증 통과: 새 금액권 상태, 예약당 사용 1장·되돌린 뒤 재사용, 취소된 예약 사용 찾기·한 번만 복원, 환불 진행 중 1건·카드/계좌 칸 규칙·비율, 처리 뒤 계좌 암호문 삭제, RLS·앱 키 권한 없음');
