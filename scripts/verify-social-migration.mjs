// Run with a separately installed PGlite module path; no application dependency.
import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
import { pathToFileURL } from 'node:url';
const { PGlite } = await import(pathToFileURL(process.argv[2]).href);
const db = new PGlite();
const first = '00000000-0000-4000-8000-000000000001';
const second = '00000000-0000-4000-8000-000000000002';
await db.exec(`
  create role anon; create role authenticated;
  create table profiles(id uuid primary key, role text not null, name text not null default 'customer', phone text not null, deleted_at timestamptz, created_at timestamptz not null default now());
  create unique index profiles_phone_live on profiles(phone) where deleted_at is null;
  create table bookings(id uuid primary key, customer_id uuid references profiles(id), category_key text, frequency text, status text, created_at timestamptz not null default now());
  insert into profiles(id,role,phone) values('${first}','customer','+821012345678');
  insert into bookings(id,customer_id,category_key,status) values('${first}','${first}','home','done');
`);
await db.exec(await readFile(new URL('../backend/src/main/resources/db/migration/V061__social_customer_identity.sql', import.meta.url), 'utf8'));
assert.equal((await db.query('select * from profiles')).rows.length, 1);
assert.equal((await db.query('select * from bookings')).rows[0].customer_id, first);
assert.equal((await db.query('select * from customer_phone_proofs')).rows.length, 0, 'a stored profile phone is not a new SMS proof');
assert.equal((await db.query('select * from first_cleaning_claims')).rows[0].booking_id, first);
await db.exec(`insert into profiles(id,role,phone,social_account) values('${second}','customer',null,true);
  insert into customer_phone_proofs values('${first}','+821012345678',now(),'sms'),('${second}','+821012345678',now(),'sms');`);
// Two authenticated accounts can prove the same phone without sharing customer rows.
assert.equal((await db.query('select * from customer_phone_proofs')).rows.length, 2);
// Execute the actual application claim SQL, so uniqueness and transaction rollback are exercised.
const source = await readFile(new URL('../backend/src/main/java/kr/ansimclean/api/profile/FirstCleaningService.java', import.meta.url), 'utf8');
// Check the production completion query against cross-category and non-completed bookings.
const completedSql = source.match(/jdbc.sql\("""\s*(select exists[\s\S]*?)"""/)[1].replaceAll(':id', '$1');
for (const category of ['home', 'store', 'appliance']) {
  for (const frequency of ['oneTime', 'recurring']) {
    for (const status of ['done', 'requested', 'cancelled']) {
      await db.query('update bookings set category_key=$1,frequency=$2,status=$3 where id=$4', [category, frequency, status, first]);
      const eligibleHistory = (await db.query(completedSql, [first])).rows[0].exists;
      assert.equal(eligibleHistory, ['home', 'store'].includes(category) && frequency === 'oneTime' && status === 'done');
      assert.equal((await db.query(completedSql, [second])).rows[0].exists, false, 'other customer history is private');
    }
  }
}
const claim = source.match(/jdbc.sql\("""\s*(insert into first_cleaning_claims[\s\S]*?)"""/)[1].replaceAll(':booking','$1').replaceAll(':id','$2');
let result = await db.query(claim, [first, second]);
assert.equal(result.affectedRows, 0, 'duplicate phone cannot claim from another account');
assert.equal((await db.query('select * from first_cleaning_claims')).rows.length, 1);
await db.exec(`update customer_phone_proofs set phone='+821087654321' where customer_id='${second}'`);
await assert.rejects(db.transaction(async tx => {
  const res = await tx.query(claim, [first, second]);
  assert.equal(res.affectedRows, 1);
  throw new Error('booking failed');
}));
assert.equal((await db.query('select * from first_cleaning_claims')).rows.length, 1, 'failed booking rolls award back');
result = await db.query(claim, [first, second]);
assert.equal(result.affectedRows, 1);
assert.equal((await db.query(claim, [first, second])).affectedRows, 0, 'second award is rejected');
const rls = await db.query("select tablename,rowsecurity from pg_tables where schemaname='public' and tablename in ('customer_phone_proofs','customer_phone_challenges','first_cleaning_claims')");
assert(rls.rows.every(row => row.rowsecurity));
await db.exec('set role authenticated');
await assert.rejects(db.query('select * from customer_phone_proofs'), /permission denied/);
await db.close();
console.log('PASS: migration preserves legacy IDs, phone proof is separate, first benefit is unique, rollback restores eligibility, Data API access is denied.');
