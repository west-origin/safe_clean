// 첫 결제 혜택·정기 요금제 금액권 V075 검증: V001~V075 를 적용하고 할인 칸 제약, 혜택 기록(예약당 하나), 서버 SQL
// (FirstPaymentBenefitService 의 원천 시리즈·사용 여부·같은 집 조회, VoucherStore.shareFor, PaymentRepository.findDueIds 의 카드 몫 조건)을 확인한다.
// 사용: node scripts/verify-first-payment-benefit-v075.mjs <별도로 설치한 @electric-sql/pglite 의 dist 경로> backend/src/main/resources/db/migration
// (Docker 가 없어 FirstPaymentBenefitIT 를 못 돌릴 때의 대체 검증. 한 연결뿐이라 잠금·동시성은 IT 에서만 확인된다)
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
for (const f of files.filter(f => parseInt(f.slice(1)) <= 75)) {
  try {
    await db.exec('begin;' + await readFile(path.join(migDir, f), 'utf8') + ';commit;');
  } catch (e) {
    await db.exec('rollback;').catch(() => {});
    throw new Error(`${f} 실패: ${e.message}`, { cause: e });
  }
}
console.log('적용: V001 … V075');

const A = '00000000-0000-0000-0000-0000000000a1', B = '00000000-0000-0000-0000-0000000000b2';
await db.exec(`insert into profiles (id, role, name, phone, signup_completed_at) values
  ('${A}', 'customer', '가', '+821000000075', now()), ('${B}', 'customer', '나', '+821000000076', now())`);
const ROAD = '서울특별시 강남구 테헤란로 123';
const series = async (c, plan = 'house_weekly_1') => (await q(`insert into booking_series(id, customer_id, category_key, weekday, service_time, road_address, free_options, paid_options, notices,
    status, term_start, term_end, visits_planned, created_at, price_plan, weekdays, plan_amount)
  values (gen_random_uuid(), $1, 'home', 0, '10:00', $2, '{}', '{}', '{}', 'active', '2026-11-02', '2026-11-23', 4, now(), $3, '{0}', 329000) returning id`, [c, ROAD, plan]))[0].id;
const booking = async (c, { seriesId = null, category = 'home', total = 82250, discount = 0, status = 'requested', detail = '3층 301호', road = ROAD, building = null } = {}) =>
  (await q(`insert into bookings(id, customer_id, series_id, category_key, home_type, frequency, service_date, service_time, starts_at, ends_at, road_address, detail_address, building_key,
    base_amount, options_amount, total_amount, platform_fee, duration_minutes, status, discount_amount, cancelled_at)
  values (gen_random_uuid(), $1, $2, $3, $4, $5, '2026-11-02', '10:00', '2026-11-02T01:00:00Z', '2026-11-02T04:00:00Z', $6, $7, $8,
    $9::bigint, 0, $9::bigint, $9::bigint / 10, 120, $10, $11::bigint, $12::timestamptz) returning id`,
    [c, seriesId, category, category === 'home' ? 'house' : null, seriesId ? 'recurring' : 'oneTime', road, detail, building, total, status, discount,
     status === 'cancelled' ? new Date('2026-11-01T00:00:00Z') : null]))[0].id;
const pay = async (seriesId, status, createdAt = '2026-10-30T00:00:00Z') => (await q(`insert into payments(id, series_id, provider, amount, status, created_at, updated_at, provider_tx_id)
  values (gen_random_uuid(), $1, 'toss', 329000, $2, $3, now(), gen_random_uuid()::text) returning id`, [seriesId, status, createdAt]))[0].id;

// 1) 할인 칸: 0 ≤ 할인 ≤ 총액
await booking(A, { category: 'appliance', total: 150000, discount: 150000 });
await assert.rejects(booking(A, { category: 'appliance', total: 150000, discount: 150001 }), /bookings_discount_check/);
await assert.rejects(booking(A, { category: 'appliance', total: 150000, discount: -1 }), /bookings_discount_check/);

// 2) FirstPaymentBenefitService 의 SQL (자바 문자열과 같게)
const USED = `select exists(select 1 from first_payment_benefits f join bookings b on b.id = f.booking_id
              where f.customer_id = $1 and b.status <> 'cancelled') as used`;
const SOURCES = `select s.id, v.road_address, v.detail_address, v.building_key from booking_series s
            join lateral (select min(p.created_at) paid_at from payments p
                          where p.series_id = s.id and p.status in ('paid', 'partially_refunded')) p on p.paid_at is not null
            join lateral (select road_address, detail_address, building_key from bookings
                          where series_id = s.id order by service_date, id limit 1) v on true
            where s.customer_id = $1 and s.price_plan like 'house\\_weekly\\_%'
            order by p.paid_at, s.id`;
const HOMES = `select f.source_series_id, f.road_address, f.detail_address, f.building_key from first_payment_benefits f
            join bookings b on b.id = f.booking_id
            where b.status <> 'cancelled' and (f.building_key = $1 or replace(f.road_address, ' ', '') like $2)`;

const SA = await series(A);
await booking(A, { seriesId: SA });
assert.deepEqual(await q(SOURCES, [A]), [], '결제 전 시리즈는 원천이 아니다');
await pay(SA, 'pending');
assert.deepEqual(await q(SOURCES, [A]), [], '대기 주문도 아니다');
await pay(SA, 'failed');
const PAID = await db.query(`update payments set status = 'paid' where series_id = $1 and status = 'pending' returning id`, [SA]);
assert.equal(PAID.rows.length, 1);
const src = await q(SOURCES, [A]);
assert.deepEqual(src.map(r => [r.id, r.road_address, r.detail_address]), [[SA, ROAD, '3층 301호']]);
const studio = await series(A, 'studio_weekly_1');
await booking(A, { seriesId: studio });
await pay(studio, 'paid');
assert.equal((await q(SOURCES, [A])).length, 1, '원룸 정기는 원천이 아니다');
assert.equal((await q(USED, [A]))[0].used, false);

// 혜택 기록: 예약당 하나, 취소된 예약의 기록은 없는 것으로 본다
const H1 = await booking(A, { category: 'appliance', total: 150000, discount: 150000 });
const insertBenefit = (c, b, s) => db.query(`insert into first_payment_benefits(customer_id, booking_id, source_series_id, road_address, detail_address, building_key, amount)
  values ($1, $2, $3, $4, '3층 301호', null, 150000)`, [c, b, s, ROAD]);
await insertBenefit(A, H1, SA);
await assert.rejects(insertBenefit(A, H1, SA), /first_payment_benefits_booking_id_key/);
assert.equal((await q(USED, [A]))[0].used, true);
const name = '%테헤란로%';
assert.equal((await q(HOMES, [null, name])).length, 1, '같은 도로명의 살아 있는 혜택');
await db.query(`update bookings set status = 'cancelled', cancelled_at = now() where id = $1`, [H1]);
assert.equal((await q(USED, [A]))[0].used, false, '취소하면 혜택이 돌아온다');
assert.equal((await q(HOMES, [null, name])).length, 0);

// 다른 계정 B 가 같은 집의 가정 정기를 결제했어도 — 같은 집 판정은 자바(sameRoad·sameUnit)가 HOMES 결과로 한다
const SB = await series(B);
await booking(B, { seriesId: SB });
await pay(SB, 'partially_refunded');
assert.equal((await q(SOURCES, [B])).length, 1, '부분 환불된 결제도 확정이다');
const H2 = await booking(A, { category: 'appliance', total: 130000, discount: 130000 });
await insertBenefit(A, H2, SA);
assert.deepEqual((await q(HOMES, [null, name])).map(r => r.detail_address), ['3층 301호'], 'B 의 판정 때 A 의 살아 있는 혜택이 보인다');

// 3) VoucherStore.shareFor — 쓰는 중 + 예약 취소로 돌려준 몫 (빼기는 제외)
const V = (await q(`insert into vouchers(owner_id, purchaser_id, catalog_key, label, face_amount, balance, status, valid_until)
  values ($1, $1, 'house_weekly_1', '가정 정기청소 주 1회', 100000, 0, 'used', now() + interval '1 year') returning id`, [A]))[0].id;
const visits = [];
for (let i = 0; i < 3; i++) visits.push(await booking(A, { seriesId: SA, total: 100000 }));
const use = (b, amount, status, reason = null) => db.query(`insert into voucher_uses(voucher_id, booking_id, customer_id, amount, status, restore_reason, restored_at)
  values ($1, $2, $3, $4, $5, $6, $7)`, [V, b, A, amount, status, reason, reason ? new Date() : null]);
await use(visits[0], 30000, 'applied');
await use(visits[1], 30000, 'restored', 'booking_cancelled');
await use(visits[2], 30000, 'restored', 'removed');
const SHARE = `select coalesce(sum(amount), 0) as s from voucher_uses where booking_id = $1 and (status = 'applied' or restore_reason = 'booking_cancelled')`;
assert.deepEqual(await Promise.all(visits.map(async b => Number((await q(SHARE, [b]))[0].s))), [30000, 30000, 0]);

// 4) PaymentRepository.findDueIds — 금액권이 다 낸 취소 회차는 카드 환불 대상이 아니다
const due = (await readFile(path.join(migDir, '../../../java/kr/ansimclean/api/payment/PaymentRepository.java'), 'utf8'))
  .match(/select p\.id from payments p left join bookings b[\s\S]*?limit 50/)[0].replace(/--[^\n]*\n/g, '\n').replace(':now', '$1');
const S2 = await series(B);
const w1 = await booking(B, { seriesId: S2, total: 100000 });
const P2 = await pay(S2, 'paid', '2026-10-01T00:00:00Z');
await db.query(`update payments set next_check_at = now() + interval '1 day' where id = $1`, [P2]);
await db.query(`update payments set next_check_at = now() + interval '1 day' where id <> $1`, [P2]);
const dueIds = async () => (await q(due, [new Date()])).map(r => r.id);
assert.ok(!(await dueIds()).includes(P2), '취소 회차 없음');
await db.query(`update bookings set status = 'cancelled', cancelled_at = now() where id = $1`, [w1]);
assert.ok((await dueIds()).includes(P2), '카드 몫이 있는 취소 회차 → 환불 대상');
const V2 = (await q(`insert into vouchers(owner_id, purchaser_id, catalog_key, label, face_amount, balance, status, valid_until)
  values ($1, $1, 'x', 'x', 100000, 0, 'used', now() + interval '1 year') returning id`, [B]))[0].id;
await db.query(`insert into voucher_uses(voucher_id, booking_id, customer_id, amount, status, restore_reason, restored_at) values ($1, $2, $3, 100000, 'restored', 'booking_cancelled', now())`, [V2, w1, B]);
assert.ok(!(await dueIds()).includes(P2), '금액권이 다 낸 회차는 카드 환불 대상이 아니다');

console.log('V075 검증 통과: 할인 칸 제약 · 혜택 기록·원천 시리즈·사용 여부·같은 집 조회 · 금액권 몫 · 재조정 대상 카드 몫 조건');
