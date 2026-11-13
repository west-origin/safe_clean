// Manager/Admin/CancelRequests (코드에서 정함 — Figma 에 없다. backend/docs/domain.md '매니저 방문 취소 승인·재배정', 2026-11-13).
// 관리자 홈 '방문 취소 요청' 또는 어드민 푸시('방문 취소 요청이 들어왔어요', data type admin-cancel-request) → 여기.
// 대기 중인 요청을 오래된 순으로 카드로 보이고, 카드마다 '거절'·'승인'. 둘 다 확인 시트를 거친다(거절은 메모 선택).
// 승인하면 서버가 배정을 풀고 24시간 재배정 콜을 보낸다(방문이 가깝거나 후보가 없으면 곧바로 배정 대기 → 토스트가 그쪽을 안내).
// 기존 ui 프리미티브(ScreenLayout·NavBar·Card·Button·Sheet·TextArea·EmptyState·ActionError)만 조립한다. 문구는 api/mock.js ADMIN_REASSIGN_COPY.
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { ScreenLayout, NavBar, Card, Button, Sheet, TextArea, EmptyState, ActionError, ScreenError, ScreenLoading, useAsync, useAction, showToast, cx } from '@ansimclean/ui';
import { getAdminReassignCopy, getAdminCancelRequests, approveCancelRequest, rejectCancelRequest } from '../../api/manager.js';
import { INPUT_LIMITS } from '../../api/mock.js';
import styles from './admin.module.css';

/** 방문 취소 요청 목록 (/admin/cancel-requests) */
export default function AdminCancelRequests() {
  const navigate = useNavigate();
  const { data, error, reload } = useAsync(() => Promise.all([getAdminReassignCopy(), getAdminCancelRequests()]), []);
  // 열린 확인 시트: null | { kind: 'approve' | 'reject', request }. 거절 메모는 시트를 열 때마다 비운다
  const [sheet, setSheet] = useState(null);
  const [note, setNote] = useState('');
  const open = (kind, request) => {
    setNote('');
    setSheet({ kind, request });
  };
  // 승인·거절 — 성공한 뒤에만 시트를 닫고 목록을 다시 받는다. 실패(이미 처리된 요청 등)는 시트 안에 서버 문구
  const decide = useAction(async () => {
    const copy = data[0];
    if (sheet.kind === 'approve') {
      const res = await approveCancelRequest(sheet.request.id);
      showToast(res.next === 'awaitingAdmin' ? copy.approvedWaiting : copy.approvedCalling);
    } else {
      await rejectCancelRequest(sheet.request.id, note);
      showToast(copy.rejected);
    }
    setSheet(null);
    reload();
  });
  const navBar = <NavBar title={data?.[0].requestsTitle ?? ''} onBack={() => navigate('/admin')} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!data) return <ScreenLoading navBar={navBar} />;
  const [copy, requests] = data;

  return (
    <ScreenLayout navBar={navBar}>
      <p className={cx('text-body-s', styles.secondary)}>{copy.requestsDescription}</p>
      {requests.length === 0 ? (
        <EmptyState icon="smile" title={copy.requestsEmpty} />
      ) : (
        <ul className={styles.cardList}>
          {requests.map((r) => {
            // 카드 안 라벨 · 값 줄. 상세(매니저가 적은 내용)가 있으면 사유 다음 줄에
            const facts = [
              [copy.managerLabel, r.managerName],
              [copy.customerLabel, r.customerName],
              [copy.addressLabel, r.address],
              [copy.reasonLabel, r.detail ? `${r.reason} — ${r.detail}` : r.reason],
              [copy.requestedLabel, r.requestedLabel],
            ];
            return (
              <li key={r.id}>
                <Card gap="sm">
                  <p className="text-title-small">{r.visitLabel}</p>
                  <dl className={styles.facts}>
                    {facts.map(([label, value]) => (
                      <div key={label} className={styles.fact}>
                        <dt className={cx('text-body-s', styles.secondary)}>{label}</dt>
                        <dd className="text-body-s">{value}</dd>
                      </div>
                    ))}
                  </dl>
                  <div className={styles.rowActions}>
                    <Button variant="secondary" chevron={false} aria-label={`${r.managerName} ${r.visitLabel} ${copy.reject}`} onClick={() => open('reject', r)}>
                      {copy.reject}
                    </Button>
                    <Button chevron={false} aria-label={`${r.managerName} ${r.visitLabel} ${copy.approve}`} onClick={() => open('approve', r)}>
                      {copy.approve}
                    </Button>
                  </div>
                </Card>
              </li>
            );
          })}
        </ul>
      )}

      {/* 확인 시트 — 승인: 설명 + 승인하기 / 거절: 설명 + 메모(선택, 500자) + 거절하기 */}
      <Sheet open={sheet !== null} onClose={() => setSheet(null)} title={sheet?.kind === 'reject' ? copy.rejectTitle : copy.approveTitle}>
        <p className={cx('text-body-default', styles.secondary)}>{sheet?.kind === 'reject' ? copy.rejectDescription : copy.approveDescription}</p>
        {sheet?.kind === 'reject' && (
          <TextArea ariaLabel={copy.rejectNoteLabel} placeholder={copy.rejectNotePlaceholder} value={note} onChange={(e) => setNote(e.target.value)} maxLength={INPUT_LIMITS.jobCancelDetail} />
        )}
        <ActionError error={decide.error} />
        <Button variant={sheet?.kind === 'reject' ? 'destructive' : 'primary'} chevron={false} loading={decide.loading} onClick={decide.run}>
          {sheet?.kind === 'reject' ? copy.rejectConfirm : copy.approveConfirm}
        </Button>
        <Button variant="secondary" chevron={false} onClick={() => setSheet(null)}>
          {copy.back}
        </Button>
      </Sheet>
    </ScreenLayout>
  );
}
