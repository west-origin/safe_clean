// 로컬 PostgreSQL WASM 검증. 서버 연결·자격 증명·서비스 데이터는 사용하지 않는다.
// 준비: npm install --prefix artifacts/payment-validation --ignore-scripts --save-exact @electric-sql/pglite@0.5.10
// 실행: node scripts/verify-payment-schema.mjs
import fs from 'node:fs';
import assert from 'node:assert/strict';
import { PGlite } from '../artifacts/payment-validation/node_modules/@electric-sql/pglite/dist/index.js';
import { pgcrypto } from '../artifacts/payment-validation/node_modules/@electric-sql/pglite/dist/contrib/pgcrypto.js';
import { btree_gist } from '../artifacts/payment-validation/node_modules/@electric-sql/pglite/dist/contrib/btree_gist.js';

const db = new PGlite({ extensions: { pgcrypto, btree_gist } });
const migrationPath = new URL('../backend/src/main/resources/db/migration/', import.meta.url);
const checks = [];
try {
  await db.exec(
    'create role anon; create role authenticated; create table flyway_schema_history(installed_rank int);'
  );
  const migrations = fs
    .readdirSync(migrationPath)
    .filter((name) => /^V\d+__.*\.sql$/.test(name))
    .sort();
  for (const name of migrations) {
    if (name.startsWith('V052')) {
      // Supabase 기본 권한이 있는 환경에서도 V052가 회수하는지 확인한다.
      await db.exec('grant all on payments, payment_methods to anon, authenticated');
    }
    await db.exec(fs.readFileSync(new URL(name, migrationPath), 'utf8'));
  }
  checks.push(`${migrations.length} original Flyway SQL files executed without modification`);
  const user = 'aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa';
  const booking = 'bbbbbbbb-bbbb-4bbb-bbbb-bbbbbbbbbbbb';
  const payment = 'cccccccc-cccc-4ccc-cccc-cccccccccccc';
  await db.query(
    "insert into profiles(id,role,name,phone) values ($1,'customer','테스트','+821000000000')",
    [user]
  );
  await db.query(
    `insert into bookings(id,customer_id,category_key,frequency,service_date,service_time,road_address,
    base_amount,total_amount,platform_fee,duration_minutes,starts_at,ends_at,status)
    values ($1,$2,'home','oneTime','2026-10-20','12:00','검증용',85000,85000,8500,180,
      '2026-10-20T03:00:00Z','2026-10-20T06:00:00Z','requested')`,
    [booking, user]
  );
  await db.query(
    "insert into payments(id,booking_id,provider,amount,status) values ($1,$2,'toss',85000,'pending')",
    [payment, booking]
  );
  await assert.rejects(
    db.query(
      "insert into payments(booking_id,provider,amount,status) values ($1,'toss',85000,'pending')",
      [booking]
    ),
    { code: '23505' }
  );
  checks.push('duplicate checkout rejected by unique index');
  await assert.rejects(
    db.query('update payments set refunded_amount=85001 where id=$1', [payment]),
    { code: '23514' }
  );
  await assert.rejects(db.query('update payments set refunded_amount=-1 where id=$1', [payment]), {
    code: '23514',
  });
  checks.push('negative/excess refunds rejected');
  await db.query(
    "insert into payment_refunds(payment_id,amount,status) values ($1,85000,'pending')",
    [payment]
  );
  await assert.rejects(
    db.query("insert into payment_refunds(payment_id,amount,status) values ($1,85000,'pending')", [
      payment,
    ]),
    { code: '23505' }
  );
  checks.push('duplicate refund rejected');
  for (const role of ['anon', 'authenticated'])
    for (const table of ['payments', 'payment_methods', 'payment_refunds']) {
      const result = await db.query(
        "select has_table_privilege($1,$2,'select,insert,update,delete') as allowed",
        [role, table]
      );
      assert.equal(result.rows[0].allowed, false);
    }
  const rls = await db.query(
    "select relrowsecurity from pg_class where oid='payment_refunds'::regclass"
  );
  assert.equal(rls.rows[0].relrowsecurity, true);
  checks.push('RLS enabled; default client privileges revoked');
  // 애플리케이션에서 실제 실행하는 재조정 SQL 자체를 가져와 실행한다.
  const repository = fs.readFileSync(
    new URL(
      '../backend/src/main/java/kr/ansimclean/api/payment/PaymentRepository.java',
      import.meta.url
    ),
    'utf8'
  );
  const query = repository.match(/@Query\(value = """([\s\S]*?)"""/)[1].replaceAll(':now', '$1');
  const guardQuery = [...repository.matchAll(/@Query\(value = """([\s\S]*?)"""/g)][1][1].replaceAll(
    ':userId',
    '$1'
  );
  assert.equal((await db.query(guardQuery, [user])).rows[0].exists, true); // 환불 pending
  assert.equal((await db.query(query, ['2026-10-17T00:00:00Z'])).rows.length, 0);
  await db.query(
    "update payments set provider_tx_id='validation-key', status='authorized' where id=$1",
    [payment]
  );
  assert.equal((await db.query(query, ['2026-10-17T00:00:00Z'])).rows.length, 1);
  await db.query(
    "update payments set status='paid', next_check_at='2026-10-18T00:00:00Z' where id=$1",
    [payment]
  );
  assert.equal((await db.query(query, ['2026-10-17T00:00:00Z'])).rows.length, 0);
  await db.query('delete from payment_refunds where payment_id=$1', [payment]);
  assert.equal((await db.query(guardQuery, [user])).rows[0].exists, false);
  await db.query("update bookings set status='cancelled' where id=$1", [booking]);
  assert.equal((await db.query(query, ['2026-10-17T00:00:00Z'])).rows.length, 1);
  checks.push('actual reconciliation SQL selects uncertain payments and cancelled paid bookings');
  checks.push('actual account-deletion guard SQL blocks unresolved refunds');
  await db.query(
    "update payments set status='partially_refunded',refunded_amount=40000 where id=$1",
    [payment]
  );
  await db.query("update payments set status='refunded',refunded_amount=85000 where id=$1", [
    payment,
  ]);
  assert.equal((await db.query(query, ['2026-10-17T00:00:00Z'])).rows.length, 0);
  checks.push('partial/full refund states accepted; terminal payments excluded from retry');
  const result = {
    success: true,
    engine: 'PGlite 0.5.10 (PostgreSQL WASM)',
    checks,
    limits:
      'Spring/Testcontainers wiring and multi-connection concurrency require Docker; no remote database was contacted.',
  };
  fs.writeFileSync(
    new URL('../artifacts/payment-db-validation.json', import.meta.url),
    JSON.stringify(result, null, 2)
  );
  console.log(JSON.stringify(result, null, 2));
} finally {
  await db.close();
}
