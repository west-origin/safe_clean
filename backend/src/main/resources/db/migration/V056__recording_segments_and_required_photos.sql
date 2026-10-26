-- Flyway 버전 규칙을 따른다. 실제 녹화 파일은 live_sessions.recording_path 한 곳에 보관한다.
alter table bookings add column required_photo_rooms text[] not null default '{}';
alter table bookings add column before_photos_confirmed_at timestamptz;
alter table bookings add column video_finished_at timestamptz;
comment on column bookings.required_photo_rooms is '작업 시작 시 체크리스트에서 복사한 필수 촬영 공간명. 현장에서 방 추가 가능. 모든 공간의 전/후 사진을 각각 필수 촬영한다.';
comment on column bookings.before_photos_confirmed_at is '모든 공간의 전 사진 검사 통과 시각. null이면 라이브 송출을 시작할 수 없다.';
comment on column bookings.video_finished_at is '매니저의 라이브 종료 요청 시각. 이후 재송출/체크리스트 변경 금지, 후 사진 촬영 단계. 실제 세션 종료는 live_sessions.ended_at.';

-- 이미 배정된 작업도 필수 공간을 가진다. 과거 녹화가 있는 작업의 시작은 재차 막지 않는다.
update bookings b set required_photo_rooms = q.rooms
from (select booking_id, array_agg(group_name order by first_sort) rooms
      from (select booking_id, group_name, min(sort) first_sort from job_checklist_items group by booking_id, group_name) g
      group by booking_id) q where b.id = q.booking_id;
update bookings set required_photo_rooms = array['청소 공간'] where cardinality(required_photo_rooms) = 0;
update bookings b set before_photos_confirmed_at = coalesce(s.started_at, b.started_at)
from live_sessions s where b.id = s.booking_id and s.started_at is not null;

create table recording_segments (
    id uuid primary key,
    live_session_id uuid not null references live_sessions(booking_id) on delete cascade,
    checklist_event_id uuid not null unique references job_checklist_events(id) on delete cascade,
    item_id uuid not null,
    room_code text not null,
    room_name text not null,
    start_at timestamptz not null,
    end_at timestamptz not null check (end_at >= start_at),
    start_ms bigint,
    end_ms bigint,
    label_source text not null default 'checklist' check (label_source in ('checklist','manual','ai')),
    review_status text not null default 'pending' check (review_status in ('pending','approved','revoked')),
    timing_status text not null default 'pending' check (timing_status in ('pending','ready','empty')),
    created_by uuid not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    check ((start_ms is null and end_ms is null) or (start_ms >= 0 and end_ms > start_ms)),
    check ((timing_status = 'ready') = (start_ms is not null and end_ms is not null))
);
create index recording_segments_timeline on recording_segments(live_session_id, end_at desc);
create index recording_segments_item on recording_segments(live_session_id, item_id);
comment on table recording_segments is '단일 녹화 영상의 공간 구간 라벨. 이전 체크 완료 경계부터 현재 완료까지 해당 항목 공간으로 추정한다. 실제 방 이동 감지가 아니므로 학습 전 검토 필수. 경계 [시작 포함, 종료 제외).';
comment on column recording_segments.id is '영상 구간 라벨 UUID';
comment on column recording_segments.live_session_id is '녹화 세션 ID = 예약 ID. 파일 경로/길이는 live_sessions에서 조회';
comment on column recording_segments.checklist_event_id is '이 구간을 생성한 체크 완료 이력. 재요청으로 같은 구간을 중복 생성하지 않는다.';
comment on column recording_segments.item_id is '체크리스트 항목 ID 스냅샷. 원래 항목 삭제 후에도 보존';
comment on column recording_segments.room_code is '표준 공간 분류 living_room/kitchen/bedroom/bathroom/other';
comment on column recording_segments.room_name is '완료 당시 공간명 스냅샷. 예: 안방, 작은방, 공용 화장실';
comment on column recording_segments.start_at is '원본 시작 경계 시각. 이전 체크 완료 시각, 첫 구간은 라이브 시작 요청 시각. 녹화 기준 변경 시 ms 재계산에 사용';
comment on column recording_segments.end_at is '원본 종료 경계 = 서버가 체크 완료를 수신한 시각. 기기 전송 지연이 포함될 수 있다.';
comment on column recording_segments.start_ms is '실제 영상 시작부터 지난 밀리초(포함). 정확한 녹화 기준 확인 전/영상 밖/길이 0 구간은 null';
comment on column recording_segments.end_ms is '실제 영상 시작부터 지난 밀리초(제외). 녹화 최종 길이로 상한 보정. 확인 전에는 null';
comment on column recording_segments.label_source is '라벨 생성 출처: checklist=체크 완료 기반 추정, manual=수동, ai=모델';
comment on column recording_segments.review_status is 'pending=검토 대기, approved=승인, revoked=체크 해제로 무효화. 무효 라벨은 학습에서 제외';
comment on column recording_segments.timing_status is 'pending=실제 녹화 시작 미확인, ready=시간 계산됨, empty=영상 밖 또는 길이 0. ready라도 라벨 검토 필요';
comment on column recording_segments.created_by is '완료 체크한 매니저 UUID 스냅샷';
comment on column recording_segments.created_at is '구간 생성 시각';
comment on column recording_segments.updated_at is '시간 보정 또는 검토 상태 변경 시각';

-- 시간 기준이 늦게 도착하는 웹훅에도 대응한다. 요청 시각을 실제 영상 시각으로 가장하지 않는다.
create function set_recording_segment_timing() returns trigger language plpgsql set search_path = public as $$
declare s live_sessions%rowtype; a bigint; z bigint;
begin
    select * into s from live_sessions where booking_id = new.live_session_id;
    new.start_ms := null; new.end_ms := null; new.timing_status := 'pending';
    if s.recording_start_confirmed and s.recording_started_at is not null then
        a := greatest(0, floor(extract(epoch from (new.start_at - s.recording_started_at)) * 1000)::bigint);
        z := greatest(0, floor(extract(epoch from (new.end_at - s.recording_started_at)) * 1000)::bigint);
        if s.duration_seconds is not null and s.duration_seconds > 0 then
            a := least(a, s.duration_seconds::bigint * 1000);
            z := least(z, s.duration_seconds::bigint * 1000);
        end if;
        if z > a then new.start_ms := a; new.end_ms := z; new.timing_status := 'ready';
        else new.timing_status := 'empty'; end if;
    end if;
    return new;
end $$;
create trigger recording_segment_timing before insert or update on recording_segments
for each row execute function set_recording_segment_timing();

create function refresh_recording_segments_timing() returns trigger language plpgsql set search_path = public as $$
begin
    update recording_segments set updated_at = now() where live_session_id = new.booking_id;
    return new;
end $$;
create trigger recording_segments_clock after update of recording_started_at, recording_start_confirmed, duration_seconds on live_sessions
for each row execute function refresh_recording_segments_timing();
comment on function set_recording_segment_timing() is '원본 시각을 실제 녹화 시작 기준 ms로 계산. 알려진 파일 길이를 넘지 않도록 보정';
comment on function refresh_recording_segments_timing() is '녹화 웹훅이 시작 시각/최종 길이를 확인하면 기존 구간 시간을 재계산';

alter table recording_segments enable row level security;
do $$ begin
    if exists (select 1 from pg_roles where rolname = 'anon') then revoke all on recording_segments from anon; end if;
    if exists (select 1 from pg_roles where rolname = 'authenticated') then revoke all on recording_segments from authenticated; end if;
end $$;

comment on table cleaning_photos is '예약별 장소의 전/후 사진 쌍. 비공개 Storage job-photos 경로만 저장. 고객에게는 예약 DONE 이후 공개';
comment on column cleaning_photos.id is '사진 쌍 UUID';
comment on column cleaning_photos.booking_id is '촬영 대상 예약 ID';
comment on column cleaning_photos.location is '필수 촬영 공간명. 전 사진 등록 시 해당 작업의 required_photo_rooms 값과 일치해야 한다.';
comment on column cleaning_photos.sort is '사진 표시 순서';
comment on column cleaning_photos.before_path is '비공개 Storage 청소 전 사진 경로';
comment on column cleaning_photos.after_path is '같은 장소 청소 후 사진 경로. 아직 미촬영이면 null';
comment on column cleaning_photos.created_at is '청소 전 사진 등록 시각';
comment on column cleaning_photos.before_at is '청소 전 사진 등록 시각';
comment on column cleaning_photos.after_at is '청소 후 사진 등록/교체 시각';
comment on table job_checklist_events is '체크리스트 체크/해제 원본 이력. recording_segments 라벨의 근거';
