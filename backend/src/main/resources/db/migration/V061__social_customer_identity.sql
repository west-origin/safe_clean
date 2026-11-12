-- Additive migration: preserves every existing auth id and business row.
-- Flyway migration (this repository does not use Supabase CLI migration history).
alter table profiles alter column phone drop not null;
alter table profiles add column social_account boolean not null default false;
comment on column profiles.social_account is 'Social onboarding; never an authorization or identity-linking claim';

-- Phone proof is distinct from login identity. The same phone can be proved by multiple
-- accounts; this grants no access to the other account's profile or bookings.
create table customer_phone_proofs (
  customer_id uuid primary key references profiles(id),
  phone text not null check (phone ~ '^\+8210[0-9]{8}$'),
  verified_at timestamptz not null,
  source text not null check (source in ('sms', 'legacy_sms'))
);
create table customer_phone_challenges (
  customer_id uuid primary key references profiles(id),
  phone text not null,
  requested_at timestamptz not null default now()
);
create table first_cleaning_claims (
  phone text primary key,
  customer_id uuid not null references profiles(id),
  booking_id uuid references bookings(id),
  claimed_at timestamptz not null default now(),
  source text not null check (source in ('booking', 'legacy_booking')),
  unique(customer_id)
);
-- Do not infer phone proof from a profile or SNS identity; every customer proves
-- the number through the authenticated SMS flow. Historical completed services
-- reserve the first-use benefit independently, without granting any data access.
insert into first_cleaning_claims(phone, customer_id, booking_id, claimed_at, source)
select distinct on (p.phone) p.phone, p.id, b.id, b.created_at, 'legacy_booking'
from profiles p join bookings b on b.customer_id = p.id
where p.role = 'customer' and p.phone ~ '^\+8210[0-9]{8}$' and b.category_key = 'home' and b.status = 'done'
order by p.phone, b.created_at;
alter table customer_phone_proofs enable row level security;
alter table customer_phone_challenges enable row level security;
alter table first_cleaning_claims enable row level security;
-- Deny all Data API access. Spring uses its existing private DB connection and user checks.
revoke all on customer_phone_proofs, customer_phone_challenges, first_cleaning_claims from anon, authenticated;
