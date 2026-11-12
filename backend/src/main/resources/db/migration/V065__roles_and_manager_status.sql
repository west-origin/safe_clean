-- V065__roles_and_manager_status.sql — 역할 4개 + 매니저 계정 상태 (RBAC R2, backend/docs/rbac.md §2·§3)
--
-- 1) profiles.role: customer · manager 에 regional_manager(지역 매니저) · admin(어드민)을 더한다.
--    어드민은 앱에서 임명하지 않는다 — 개발자가 DB 에서 직접 지정한다(rbac.md §2, runbook.md).
-- 2) manager_profiles.status: 매니저·지역 매니저의 계정 상태. 예전의 approved(참/거짓)를 대신한다.
--      pending(승인 대기) · active(활동 중) · suspended(활동 정지) · dormant(휴면) · withdrawn(퇴사)
--    데이터 이전: 탈퇴한 계정 → withdrawn, approved=true → active, 나머지 → pending.
--    패널티 '콜 배정 정지'(suspended_until)는 이 상태와 별개로 그대로 둔다(rbac.md §3.1).
--    status_changed_at = 마지막 상태 전환 시각(퇴사자 정산 조회 기한 계산에 쓴다, §3). 이전 때는 지금 시각.
-- 3) approved 칸을 지운다 — 같은 뜻의 값이 두 곳에 있으면 어긋난다. 코드는 status 만 본다.
--
-- 잠금: profiles 의 제약 교체(검사 1회)와 manager_profiles 의 칸 추가·갱신·삭제. 두 표 모두 작다(매니저 수십 명).

alter table profiles drop constraint profiles_role_check;
alter table profiles add constraint profiles_role_check
  check (role in ('customer', 'manager', 'regional_manager', 'admin'));

alter table manager_profiles add column status text not null default 'pending'
  constraint manager_profiles_status_check check (status in ('pending', 'active', 'suspended', 'dormant', 'withdrawn'));
alter table manager_profiles add column status_changed_at timestamptz;

update manager_profiles m
   set status = case when p.deleted_at is not null then 'withdrawn'
                     when m.approved then 'active'
                     else 'pending' end,
       status_changed_at = now()
  from profiles p
 where p.id = m.manager_id;

alter table manager_profiles drop column approved;

comment on column profiles.role is 'customer | manager | regional_manager | admin (rbac.md §2). admin 은 DB 에서만 지정';
comment on column manager_profiles.status is 'pending | active | suspended | dormant | withdrawn (rbac.md §3). 전환은 어드민만(탈퇴 시 withdrawn 은 시스템)';
comment on column manager_profiles.status_changed_at is '마지막 상태 전환 시각';
