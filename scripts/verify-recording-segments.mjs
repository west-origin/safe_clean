// 원본 Flyway 마이그레이션과 녹화 시각 보정 트리거를 실제 PostgreSQL 엔진(PGlite)에서 검증한다.
import fs from 'node:fs';
import assert from 'node:assert/strict';
import { PGlite } from '../artifacts/payment-validation/node_modules/@electric-sql/pglite/dist/index.js';
import { pgcrypto } from '../artifacts/payment-validation/node_modules/@electric-sql/pglite/dist/contrib/pgcrypto.js';
import { btree_gist } from '../artifacts/payment-validation/node_modules/@electric-sql/pglite/dist/contrib/btree_gist.js';
const db = new PGlite({ extensions: { pgcrypto, btree_gist } });
const dir = new URL('../backend/src/main/resources/db/migration/', import.meta.url);
const checks = [];
try {
  await db.exec('create role anon; create role authenticated; create table flyway_schema_history(installed_rank int);');
  const migrations = fs.readdirSync(dir).filter((n) => /^V\d+__.*\.sql$/.test(n)).sort();
  for (const n of migrations) {
    if (n.startsWith('V056')) await db.exec('alter default privileges grant all on tables to anon, authenticated;');
    await db.exec(fs.readFileSync(new URL(n, dir), 'utf8'));
  }
  checks.push(`${migrations.length} original migrations applied`);
  const customer = 'aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa';
  const booking = 'bbbbbbbb-bbbb-4bbb-bbbb-bbbbbbbbbbbb';
  const item = 'cccccccc-cccc-4ccc-cccc-cccccccccccc';
  const event = 'dddddddd-dddd-4ddd-dddd-dddddddddddd';
  await db.query("insert into profiles(id,role,name,phone) values ($1,'customer','테스트','+821000000000')", [customer]);
  await db.query(`insert into bookings(id,customer_id,category_key,frequency,service_date,service_time,road_address,
    base_amount,total_amount,platform_fee,duration_minutes,starts_at,ends_at,status)
    values ($1,$2,'home','oneTime','2026-10-24','12:00','검증용',85000,85000,8500,180,
    '2026-10-24T03:00:00Z','2026-10-24T06:00:00Z','requested')`, [booking, customer]);
  await db.query("insert into live_sessions(booking_id,room_name,status) values ($1,'test-room','live')", [booking]);
  await db.query(`insert into job_checklist_events(id,booking_id,item_id,group_name,label,done,changed_at,manager_id)
    values ($1,$2,$3,'안방','바닥',true,'2026-10-24T03:01:30Z',$4)`, [event, booking, item, customer]);
  const insert = `insert into recording_segments(id,live_session_id,checklist_event_id,item_id,room_code,room_name,start_at,end_at,
    label_source,review_status,created_by,created_at,updated_at)
    values (gen_random_uuid(),$1,$2,$3,'bedroom','안방','2026-10-24T03:00:00Z','2026-10-24T03:01:30Z',
    'checklist','pending',$4,now(),now())`;
  await db.query(insert, [booking, event, item, customer]);
  const read = async () => (await db.query('select * from recording_segments')).rows[0];
  assert.equal((await read()).start_ms, null);
  assert.equal((await read()).timing_status, 'pending');
  await db.query("update live_sessions set recording_started_at='2026-10-24T03:00:10Z',recording_start_confirmed=true where booking_id=$1", [booking]);
  let row = await read();
  assert.equal(Number(row.start_ms), 0); assert.equal(Number(row.end_ms), 80000);
  assert.equal(row.review_status, 'pending'); assert.equal(row.timing_status, 'ready');
  checks.push('late exact recording start fills millisecond offsets; negative start clamped to zero; labels still need review');
  await db.query('update live_sessions set duration_seconds=60 where booking_id=$1', [booking]);
  assert.equal(Number((await read()).end_ms), 60000);
  checks.push('final video length clamps segment end');
  await db.query("update recording_segments set review_status='revoked'");
  await db.query('update live_sessions set duration_seconds=70 where booking_id=$1', [booking]);
  assert.equal((await read()).review_status, 'revoked');
  assert.equal(Number((await read()).end_ms), 70000);
  checks.push('uncheck revocation survives later recording webhook correction');
  await assert.rejects(db.query(insert, [booking, event, item, customer]), /unique|duplicate/i);
  checks.push('same checklist event cannot generate duplicate segments');
  await db.query("update live_sessions set recording_started_at='2026-10-24T03:02:00Z' where booking_id=$1", [booking]);
  row = await read(); assert.equal(row.start_ms, null); assert.equal(row.end_ms, null); assert.equal(row.timing_status, 'empty');
  checks.push('zero duration or out-of-video segment stays empty rather than inventing a valid interval');
  const comments = (await db.query(`select attname, col_description(attrelid,attnum) as description from pg_attribute
    where attrelid='recording_segments'::regclass and attnum>0 and not attisdropped`)).rows;
  assert(comments.every((c) => c.description));
  assert((await db.query("select obj_description('recording_segments'::regclass)")).rows[0].obj_description);
  assert.equal((await db.query("select relrowsecurity from pg_class where relname='recording_segments'")).rows[0].relrowsecurity, true);
  for (const role of ['anon', 'authenticated']) {
    assert.equal((await db.query("select has_table_privilege($1,'recording_segments','select') as access", [role])).rows[0].access, false);
  }
  checks.push('table and every column have DB comments; RLS enabled and client grants revoked');
  await db.query('delete from bookings where id=$1', [booking]);
  assert.equal(Number((await db.query('select count(*) as n from recording_segments')).rows[0].n), 0);
  checks.push('booking deletion cascades to segment labels');
  console.log(JSON.stringify({ passed: true, checks }, null, 2));
} finally { await db.close(); }
