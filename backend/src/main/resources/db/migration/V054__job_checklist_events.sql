-- 항목을 지워도 체크·해제 이력을 보존한다. 예약 삭제 때만 함께 정리한다.
create table job_checklist_events (
    id uuid primary key,
    event_order bigint generated always as identity unique,
    booking_id uuid not null references bookings(id) on delete cascade,
    item_id uuid not null,
    group_name text not null,
    label text not null,
    done boolean not null,
    changed_at timestamptz not null,
    manager_id uuid not null
);
-- 같은 서버 시각에 체크·해제가 겹쳐도 마지막 상태를 정할 수 있는 저장 순서.
create index job_checklist_events_latest on job_checklist_events(booking_id, item_id, event_order desc);
alter table job_checklist_events enable row level security;
do $$ begin
    if exists (select 1 from pg_roles where rolname = 'anon') then
        revoke all on job_checklist_events from anon;
        revoke all on sequence job_checklist_events_event_order_seq from anon;
    end if;
    if exists (select 1 from pg_roles where rolname = 'authenticated') then
        revoke all on job_checklist_events from authenticated;
        revoke all on sequence job_checklist_events_event_order_seq from authenticated;
    end if;
end $$;
