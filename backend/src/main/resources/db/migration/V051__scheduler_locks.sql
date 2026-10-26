-- Cloud Run은 최대 인스턴스 1이어도 리비전 교체 때 실행이 겹칠 수 있다.
create table shedlock (
    name varchar(64) not null primary key,
    lock_until timestamp(3) not null,
    locked_at timestamp(3) not null,
    locked_by varchar(255) not null
);
alter table shedlock enable row level security;
-- 앱은 Spring 서버만 부른다. 클라이언트가 잠금을 바꾸거나 해제할 수 없게 한다.
do $$ begin
    if exists (select 1 from pg_roles where rolname = 'anon') then
        revoke all on shedlock from anon;
    end if;
    if exists (select 1 from pg_roles where rolname = 'authenticated') then
        revoke all on shedlock from authenticated;
    end if;
end $$;
