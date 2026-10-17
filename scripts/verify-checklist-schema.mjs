// Docker가 없는 환경에서 원본 Flyway SQL·이력 정렬·RLS를 로컬 PostgreSQL WASM으로 확인한다.
// 기존 결제 검증용 PGlite를 재사용한다. 서비스 DB와 네트워크에는 접근하지 않는다.
import fs from 'node:fs';
import assert from 'node:assert/strict';
import { PGlite } from '../artifacts/payment-validation/node_modules/@electric-sql/pglite/dist/index.js';
import { pgcrypto } from '../artifacts/payment-validation/node_modules/@electric-sql/pglite/dist/contrib/pgcrypto.js';
import { btree_gist } from '../artifacts/payment-validation/node_modules/@electric-sql/pglite/dist/contrib/btree_gist.js';

const db = new PGlite({ extensions: { pgcrypto, btree_gist } });
const folder = new URL('../backend/src/main/resources/db/migration/', import.meta.url);
const checks = [];
try {
  await db.exec('create role anon; create role authenticated; create table flyway_schema_history(installed_rank int);');
  const migrations = fs.readdirSync(folder).filter((name) => /^V\d+__.*\.sql$/.test(name)).sort();
  for (const migration of migrations) {
    if (migration.startsWith('V054')) {
      await db.exec('alter default privileges grant all on tables to anon, authenticated; alter default privileges grant all on sequences to anon, authenticated;');
    }
    await db.exec(fs.readFileSync(new URL(migration, folder), 'utf8'));
  }
  checks.push(`${migrations.length} original Flyway SQL files applied, including payments and scheduler locks`);
  const customer = 'aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa';
  const booking = 'bbbbbbbb-bbbb-4bbb-bbbb-bbbbbbbbbbbb';
  const item = 'cccccccc-cccc-4ccc-cccc-cccccccccccc';
  await db.query("insert into profiles(id,role,name,phone) values ($1,'customer','테스트','+821000000000')", [customer]);
  await db.query(`insert into bookings(id,customer_id,category_key,frequency,service_date,service_time,road_address,
    base_amount,total_amount,platform_fee,duration_minutes,starts_at,ends_at,status)
    values ($1,$2,'home','oneTime','2026-10-20','12:00','검증용',85000,85000,8500,180,
    '2026-10-20T03:00:00Z','2026-10-20T06:00:00Z','requested')`, [booking, customer]);
  await db.query("insert into live_sessions(booking_id,room_name,status,recording_status) values ($1,'test-room','created','none')", [booking]);
  const session = (await db.query('select recording_started_at,recording_start_confirmed from live_sessions where booking_id=$1', [booking])).rows[0];
  assert.equal(session.recording_started_at, null);
  assert.equal(session.recording_start_confirmed, false);
  checks.push('legacy sessions remain unknown; no fabricated recording start');
  await db.query("insert into job_checklist_items(id,booking_id,group_name,label,sort,done,done_at) values ($1,$2,'거실','바닥',1,true,'2026-10-20T03:01:00Z')", [item, booking]);
  for (const done of [true, false, true]) {
    await db.query(`insert into job_checklist_events(id,booking_id,item_id,group_name,label,done,changed_at,manager_id)
      values (gen_random_uuid(),$1,$2,'거실','바닥',$3,'2026-10-20T03:01:00Z',$4)`, [booking, item, done, customer]);
  }
  const latest = (await db.query('select distinct on (item_id) * from job_checklist_events where booking_id=$1 order by item_id,event_order desc', [booking])).rows;
  assert.equal(latest.length, 1);
  assert.equal(latest[0].done, true);
  assert.equal(Number(latest[0].event_order), 3);
  checks.push('same-timestamp check/uncheck/recheck resolved by DB event_order');
  await db.query('delete from job_checklist_items where booking_id=$1', [booking]);
  assert.equal(Number((await db.query('select count(*) as n from job_checklist_events where booking_id=$1', [booking])).rows[0].n), 3);
  checks.push('deleting checklist items preserves all historical events');
  assert.equal((await db.query("select relrowsecurity from pg_class where relname='job_checklist_events'")).rows[0].relrowsecurity, true);
  for (const role of ['anon', 'authenticated']) {
    const permissions = (await db.query(`select has_table_privilege($1,'job_checklist_events','SELECT') as read,
      has_table_privilege($1,'job_checklist_events','INSERT') as write,
      has_sequence_privilege($1,'job_checklist_events_event_order_seq','USAGE') as sequence`, [role])).rows[0];
    assert.deepEqual(permissions, { read: false, write: false, sequence: false });
  }
  checks.push('RLS enabled; default client table and sequence grants revoked');
  await db.query('delete from bookings where id=$1', [booking]);
  assert.equal(Number((await db.query('select count(*) as n from job_checklist_events where booking_id=$1', [booking])).rows[0].n), 0);
  checks.push('booking deletion cascades to checklist events');
  process.stdout.write(JSON.stringify({ passed: true, checks }, null, 2) + '\n');
} finally {
  await db.close();
}
