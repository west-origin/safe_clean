// 요금표 전환 V074 검증: V001~V074 를 적용하고 요금표 설정·요금제 키 제약·주 N회 시리즈 칸 규칙·시리즈 결제(대상 하나, 시리즈당 살아 있는 주문 1건)·
// 회차 몫 환불(회차당 한 행, 전체 환불은 결제당 한 행)·분해청소 옵션(옛 옵션 retired, 추가 요금 2개)과 서버 SQL(재조정 대상, 미해결 결제)을 확인한다.
// 사용: node scripts/verify-price-plans-v074.mjs <별도로 설치한 @electric-sql/pglite 의 dist 경로> backend/src/main/resources/db/migration
// (Docker 가 없어 PricePlanIT 를 못 돌릴 때의 대체 검증. 한 연결뿐이라 동시성은 IT 에서만 확인된다)
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
for (const f of files.filter(f => parseInt(f.slice(1)) <= 74)) {
  try {
    await db.exec('begin;' + await readFile(path.join(migDir, f), 'utf8') + ';commit;');
  } catch (e) {
    await db.exec('rollback;').catch(() => {});
    throw new Error(`${f} 실패: ${e.message}`, { cause: e });
  }
}
console.log('적용: V001 … V074');

// 1) 요금표 설정 (사용자 요금표 2026-11-13)
const plans = (await q(`select value from app_settings where key = 'price_plans'`))[0].value;
assert.deepEqual(plans.house.weekly, { 1: 329000, 2: 599000, 3: 889000 });
assert.equal(plans.house.trial, 50000);
assert.deepEqual(plans.studio.weekly, { 1: 229000, 2: 399000, 3: 599000 });
assert.equal(plans.studio.once, 69000);
assert.equal(plans.studio.firstMonth[1], 199000);
assert.deepEqual(plans.hood.sizes, { 600: 130000, 900: 150000, 1200: 200000 });
assert.equal(plans.weeks, 4);

// 2) 분해청소 옵션: 옛 유료 옵션은 retired, 추가 요금 2개(+20,000)
const opts = await q(`select key, price, retired from catalog_options where category_key = 'appliance' and kind = 'paid' order by key`);
assert.deepEqual(opts.map(o => [o.key, Number(o.price), o.retired]), [['ac', 40000, true], ['noExtinguisher', 20000, false], ['panel', 20000, false], ['washer', 25000, true]]);

// 3) 예약·시리즈 요금제 키 제약, 시리즈 칸은 셋이 함께
const C = '00000000-0000-0000-0000-00000000000c';
await db.exec(`insert into profiles (id, role, name, phone, signup_completed_at) values ('${C}', 'customer', '가', '+821000000074', now())`);
const series = async (plan, weekdays, amount) => (await q(`insert into booking_series(id, customer_id, category_key, weekday, service_time, road_address, free_options, paid_options, notices,
    status, term_start, term_end, visits_planned, created_at, price_plan, weekdays, plan_amount)
  values (gen_random_uuid(), $1, 'home', 0, '10:00', '검증', '{}', '{}', '{}', 'active', '2026-11-02', '2026-11-23', 4, now(), $2, $3, $4) returning id`, [C, plan, weekdays, amount]))[0].id;
const S = await series('house_weekly_1', [0], 329000);
await series(null, null, null); // 매월 1회 정기(매장 등)
await assert.rejects(series('house_weekly_4', [0], 1), /booking_series_price_plan_check/);
await assert.rejects(series('house_weekly_1', null, 329000), /booking_series_plan_fields/);
const visit = async (plan, seriesId = S, status = 'requested') => (await q(`insert into bookings(id, customer_id, series_id, category_key, home_type, frequency, service_date, service_time,
    starts_at, ends_at, road_address, base_amount, options_amount, total_amount, platform_fee, duration_minutes, status, price_plan)
  values (gen_random_uuid(), $1, $2, 'home', 'house', 'recurring', '2026-11-02', '10:00', '2026-11-02T01:00:00Z', '2026-11-02T04:00:00Z', '검증',
    82250, 0, 82250, 8225, 180, $3, $4) returning id`, [C, seriesId, status, plan]))[0].id;
const V1 = await visit('house_weekly_1');
const V2 = await visit('house_weekly_1');
for (const ok of ['house_trial', 'studio_once', 'studio_weekly_3', 'hood_1200', null]) await visit(ok, null);
await assert.rejects(visit('hood_750', null), /bookings_price_plan_check/);

// 4) 시리즈 결제: 대상은 하나, 시리즈당 살아 있는 주문 1건 (실패한 것은 여러 건)
const pay = async (cols, vals, status = 'pending') => (await q(`insert into payments(id, ${cols}, provider, amount, status, created_at, updated_at)
  values (gen_random_uuid(), ${vals.map((_, i) => '$' + (i + 2)).join(', ')}, 'toss', 329000, $1, now() - interval '1 hour', now()) returning id`, [status, ...vals]))[0].id;
await pay('series_id', [S], 'failed');
const P = await pay('series_id', [S], 'paid');
await assert.rejects(pay('series_id', [S]), /payments_one_series/);
await assert.rejects(pay('series_id, booking_id', [S, V1], 'failed'), /payments_one_target/);
await assert.rejects(db.query(`insert into payments(id, provider, amount, status, created_at, updated_at) values (gen_random_uuid(), 'toss', 1, 'failed', now(), now())`), /payments_one_target/);
await pay('booking_id', [V1], 'failed'); // 예약 결제는 그대로

// 5) 재조정 대상 (PaymentRepository.findDueIds 의 시리즈 조건): 결제 뒤 취소된 회차가 있고 환불 행이 없으면 대상
const due = async () => (await q(`select p.id from payments p left join bookings b on b.id = p.booking_id
   where p.provider = 'toss' and p.status not in ('failed','refunded') and p.provider_tx_id is not null
     and (p.next_check_at is null or p.next_check_at <= now()
          or (b.status = 'cancelled' and p.status in ('paid','partially_refunded') and not exists (select 1 from payment_refunds r where r.payment_id = p.id))
          or (p.series_id is not null and p.status in ('paid','partially_refunded')
              and exists (select 1 from bookings v where v.series_id = p.series_id and v.status = 'cancelled' and v.cancelled_at > p.created_at
                and not exists (select 1 from payment_refunds r where r.payment_id = p.id and r.booking_id = v.id))))`)).map(r => r.id);
await db.query(`update payments set provider_tx_id = 'pk', next_check_at = now() + interval '1 day' where id = $1`, [P]);
assert.deepEqual(await due(), []);
await db.query(`update bookings set status = 'cancelled', cancelled_at = now(), cancel_reason = 'customer' where id = $1`, [V2]);
assert.deepEqual(await due(), [P]);

// 6) 회차 몫 환불: 회차당 한 행, 전체 환불(booking_id 없음)은 결제당 한 행
await db.query(`insert into payment_refunds(id, payment_id, booking_id, amount, status) values (gen_random_uuid(), $1, $2, 82250, 'pending')`, [P, V2]);
assert.deepEqual(await due(), []); // 환불 행이 생기면 더 집지 않는다
await assert.rejects(db.query(`insert into payment_refunds(id, payment_id, booking_id, amount, status) values (gen_random_uuid(), $1, $2, 82250, 'pending')`, [P, V2]), /payment_refunds_one_visit/);
await db.query(`insert into payment_refunds(id, payment_id, booking_id, amount, status) values (gen_random_uuid(), $1, $2, 82250, 'pending')`, [P, V1]);
const B = await pay('booking_id', [await visit(null, null)], 'paid');
await db.query(`insert into payment_refunds(id, payment_id, amount, status) values (gen_random_uuid(), $1, 1000, 'pending')`, [B]);
await assert.rejects(db.query(`insert into payment_refunds(id, payment_id, amount, status) values (gen_random_uuid(), $1, 1000, 'pending')`, [B]), /payment_refunds_one_full/);

// 7) 미해결 결제(탈퇴 가드, PaymentRepository.hasUnresolvedPayment): 시리즈 결제도 고객을 찾는다
const unresolved = (await q(`select exists(select 1 from payments p left join bookings b on b.id = p.booking_id
    left join booking_series s on s.id = p.series_id
    where coalesce(b.customer_id, s.customer_id) = $1 and p.provider = 'toss'
      and (p.status = 'authorized' or exists (select 1 from payment_refunds r where r.payment_id = p.id and r.status = 'pending'))) x`, [C]))[0].x;
assert.equal(unresolved, true);
console.log('V074 검증 통과: 요금표 설정, 분해청소 옵션 retired·추가 요금, 요금제 키·시리즈 칸 제약, 시리즈 결제 대상 하나·살아 있는 주문 1건, 재조정 대상(결제 뒤 취소 회차), 회차당 환불 1행·전체 환불 1행, 시리즈 결제의 미해결 판정');
