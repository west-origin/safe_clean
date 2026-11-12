-- V066__activity_logs.sql — 활동 이력 (RBAC R3, backend/docs/rbac.md §11)
--
-- 누가(행위자·역할) 무엇을(행위) 어디에(대상 종류·id) 어떻게 바꿨는지(변경 전·후 jsonb) 왜(사유) 언제 남긴다.
-- 민감 작업은 업무 처리와 같은 트랜잭션에서 여기 한 줄을 쓴다(ActivityLogService.record — 트랜잭션 밖에서 부르면 실패한다).
-- 기록 대상 예: 역할·상태 변경, 권역 변경, 콜 반납, 예약 취소·재배정, 정산·페널티 처리, 공지 수정·삭제, 사진 교체, 설정값 변경.
--
-- 불변: 누구도 고치거나 지울 수 없다. UPDATE·DELETE 는 행 트리거, TRUNCATE 는 문장 트리거가 예외를 던진다(표 소유자 포함).
-- 남은 일(rbac.md §15-17): 앱(Spring) 전용 DB 계정(표 소유자 아님, 이 표에는 INSERT·SELECT 만)으로 바꾸는 것은
-- 새 DB 비밀번호·Cloud Run 시크릿이 필요한 인프라 변경이라 배포 때 따로 한다. 그 전까지는 트리거가 유일한 보호다
-- (표 소유자는 트리거를 끌 수 있으므로 계정 분리가 끝나야 완전하다).
-- 조회는 어드민만(API 는 나중). RLS 켬 + 정책 0개 + 앱 키 권한 없음(rbac.md §13, V064 기본 권한).

create table activity_logs (
  id           uuid primary key default gen_random_uuid(),
  actor_id     uuid,                       -- 행위자 profiles.id. null 이면 시스템(스케줄러 등). FK 없음: 탈퇴·파기 뒤에도 이력은 남는다
  actor_role   text,                       -- 그때의 역할(customer·manager·regional_manager·admin). 시스템이면 null
  action       text not null,              -- 행위 키 (ActivityLogService 상수, 예: notice.update)
  target_type  text not null,              -- 대상 종류 (예: notice·booking·manager)
  target_id    text,                       -- 대상 id (uuid 가 아닌 키도 있어 text)
  before_data  jsonb,                      -- 변경 전 값 (새로 만들면 null)
  after_data   jsonb,                      -- 변경 후 값 (지우면 null)
  reason       text,                       -- 사유 (출입 정보 열람 등 사유가 필요한 작업)
  created_at   timestamptz not null default now()
);
create index activity_logs_target on activity_logs (target_type, target_id, created_at desc);
create index activity_logs_actor on activity_logs (actor_id, created_at desc);

alter table activity_logs enable row level security; -- 정책 0개 = 앱 키로는 아무것도 못 한다 (서버만)

create function activity_logs_immutable() returns trigger
language plpgsql
set search_path = ''
as $$
begin
  raise exception 'activity_logs 는 고치거나 지울 수 없어요 (%)', tg_op using errcode = 'insufficient_privilege';
end;
$$;

create trigger activity_logs_no_update_delete before update or delete on activity_logs
  for each row execute function activity_logs_immutable();
create trigger activity_logs_no_truncate before truncate on activity_logs
  for each statement execute function activity_logs_immutable();

comment on table activity_logs is '활동 이력 (rbac.md §11). 고치거나 지울 수 없다(트리거). 앱 전용 DB 계정 분리는 배포 때';
