-- 청소 전·후 사진 (2026-10-23). 한 행 = 한 위치(예: '거실 소파 앞')의 사진 한 쌍.
-- 매니저가 청소 진행 중에 '전' 사진을 찍으면 행이 생기고(before_*), 완료 직전에 같은 위치의 '후' 사진을 찍으면 after_* 가 채워진다.
-- 파일은 Supabase Storage 비공개 버킷 job-photos 에 있고 여기에는 경로만 둔다 (booking/CleaningPhotoService).
-- 예약 행이 지워지면 함께 지운다(on delete cascade). 보존 기간이 지나거나 고객이 탈퇴하면 서버가 파일과 행을 지운다.
create table cleaning_photos (
    id uuid primary key,
    booking_id uuid not null references bookings(id) on delete cascade,
    location text not null check (char_length(location) between 1 and 20), -- 매니저가 적은 위치 이름 (앞뒤 공백 뺀 1~20자)
    sort int not null,               -- 찍은 순서 (화면 표시 순서)
    before_path text not null,       -- Storage 경로 <bookingId>/<photoId>-before.<확장자>
    before_at timestamptz not null,
    after_path text,                 -- 아직 '후' 사진이 없으면 null. 다시 찍으면 새 경로로 바뀐다
    after_at timestamptz,
    created_at timestamptz not null
);
create index cleaning_photos_booking on cleaning_photos(booking_id, sort);
-- 앱은 Supabase 를 직접 부르지 않는다 — 서버(service role)만 읽고 쓴다. V052·V054 와 같은 방식으로 클라이언트 역할 권한을 뺀다
alter table cleaning_photos enable row level security;
do $$ begin
    if exists (select 1 from pg_roles where rolname = 'anon') then
        revoke all on cleaning_photos from anon;
    end if;
    if exists (select 1 from pg_roles where rolname = 'authenticated') then
        revoke all on cleaning_photos from authenticated;
    end if;
end $$;
