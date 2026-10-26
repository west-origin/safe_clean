-- 기존 버전에서 라이브만 시작하고 필수 전 사진을 찍지 않은 진행 중 작업은 전 사진 단계에서 보완한다.
-- 새 버전은 전 사진 확인 후 삭제가 불가능하므로 정상 진행 중 작업에는 해당되지 않는다.
-- 영상 파일/기존 세션/체크 이력은 보존하며, 보완 후 동일 세션에 다시 연결한다.
update bookings b set before_photos_confirmed_at = null
where b.status in ('in_progress', 'paused') and b.video_finished_at is null
  and b.before_photos_confirmed_at is not null
  and exists (
      select 1 from unnest(b.required_photo_rooms) as r(room)
      where not exists (select 1 from cleaning_photos p where p.booking_id = b.id and p.location = r.room)
  );

-- Hibernate의 세션 상태 갱신 때마다 구간 전체를 다시 쓰지 않도록 실제 시간 기준 변경만 처리한다.
drop trigger recording_segments_clock on live_sessions;
create trigger recording_segments_clock after update of recording_started_at, recording_start_confirmed, duration_seconds on live_sessions
for each row when (
    old.recording_started_at is distinct from new.recording_started_at
    or old.recording_start_confirmed is distinct from new.recording_start_confirmed
    or old.duration_seconds is distinct from new.duration_seconds
) execute function refresh_recording_segments_timing();
