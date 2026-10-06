-- 수리 사진 정리의 '삭제 중' 표시 (재감사 2차 #4, 2026-09-24).
-- 원장 행을 먼저 지우고 Storage 를 지우면, 그 사이 서버가 멈출 때 파일만 남고 다시 지울 기록이 사라졌다.
-- 정리는 이제 행에 deleting_at 을 적어 '예약에 붙을 수 없는 상태' 로 잡고 → Storage 삭제 → 행 삭제. 멈추면 행이 남아 다음 정리가 다시 한다
alter table repair_photo_uploads add column deleting_at timestamptz;
