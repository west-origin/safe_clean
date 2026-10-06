-- 탈퇴한 사용자의 1:1 문의는 전자상거래법의 '소비자 불만·분쟁처리 기록'으로 탈퇴 뒤 3년 보관하고 지운다
-- (사용자 결정 2026-09-27 — 자체 점검 #31. 예전에는 기간 없이 남았다). 탈퇴할 때 purge_after 를 채우고 매일 지난 것을 지운다
alter table inquiries add column purge_after timestamptz;
create index inquiries_purge_after_idx on inquiries (purge_after) where purge_after is not null;
-- 이미 탈퇴한 사용자의 문의도 같은 규칙으로 (탈퇴 시각 + 3년)
update inquiries i set purge_after = p.deleted_at + interval '3 years'
  from profiles p where p.id = i.user_id and p.deleted_at is not null and i.purge_after is null;
