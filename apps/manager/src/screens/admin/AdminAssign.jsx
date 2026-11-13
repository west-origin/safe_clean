// Manager/Admin/Assign (코드에서 정함 — Figma 에 없다. backend/docs/domain.md '매니저 방문 취소 승인·재배정' ⑦, 2026-11-13).
// 배정 대기 목록의 '매니저 배정' → 여기 (/admin/reassignments/:id, :id = 예약 id).
// 위에 예약 요약(방문 일시·주소·고객), 아래에 배정할 수 있는 매니저(서버가 활동 지역·겹치는 작업·이 방문을 취소한 매니저를 걸러 준다)를
// 라디오로 고르고 '배정하기'. 근무시간 밖인 매니저는 설명 줄에 '근무시간 밖' — 어드민이 통화하고 배정할 수 있다.
// 배정되면 토스트 후 배정 대기 목록으로. 이미 배정된 예약이면 서버 문구(409)를 오류 화면으로.
// 기존 ui 프리미티브(ScreenLayout·NavBar·Card·RadioOption·Button·EmptyState·ActionError)만 조립한다. 문구는 api/mock.js ADMIN_REASSIGN_COPY.
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { ScreenLayout, NavBar, Card, RadioOption, Button, EmptyState, ActionError, ScreenError, ScreenLoading, useAsync, useAction, showToast, cx } from '@ansimclean/ui';
import { getAdminReassignCopy, getReassignments, getAssignableManagers, assignBooking } from '../../api/manager.js';
import styles from './admin.module.css';

/** 매니저 배정 (/admin/reassignments/:id) */
export default function AdminAssign() {
  const { id } = useParams();
  const navigate = useNavigate();
  // 문구 · 대기 목록(이 예약의 요약을 찾는다 — 한 건 조회 API 가 없다) · 배정할 수 있는 매니저
  const { data, error, reload } = useAsync(() => Promise.all([getAdminReassignCopy(), getReassignments(), getAssignableManagers(id)]), [id]);
  const [managerId, setManagerId] = useState(null);
  const assign = useAction(async () => {
    await assignBooking(id, managerId);
    showToast(data[0].assigned);
    navigate('/admin/reassignments', { replace: true });
  });
  const back = () => navigate('/admin/reassignments');
  const navBar = <NavBar title={data?.[0].assignTitle ?? ''} onBack={back} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!data) return <ScreenLoading navBar={navBar} />;
  const [copy, items, managers] = data;
  const booking = items.find((r) => r.bookingId === id);
  // 그 사이 다른 어드민이 배정해 대기 목록에서 빠졌다
  if (!booking) return <ScreenError navBar={navBar} title={copy.notWaiting} retryLabel={copy.waitingTitle} onRetry={back} />;

  return (
    <ScreenLayout
      navBar={navBar}
      stickyCta={
        managers.length === 0 ? undefined : (
          <div className={styles.actions}>
            <ActionError error={assign.error} />
            <Button chevron={false} disabled={!managerId} loading={assign.loading} onClick={assign.run}>
              {copy.assignConfirm}
            </Button>
          </div>
        )
      }
    >
      <Card tone="muted" gap="sm">
        <p className="text-title-small">{booking.visitLabel}</p>
        <p className="text-body-s">{booking.address.join(' ')}</p>
        <p className={cx('text-body-s', styles.secondary)}>{[booking.customer?.name, booking.customer?.phone].filter(Boolean).join(' · ')}</p>
      </Card>
      {managers.length === 0 ? (
        <EmptyState icon="call" title={copy.assignEmpty} description={copy.assignEmptyDescription} />
      ) : (
        <div role="radiogroup" aria-label={copy.assignListLabel} className={styles.choices}>
          {managers.map((m) => (
            <RadioOption
              key={m.id}
              label={[m.name, m.phone].filter(Boolean).join(' · ')}
              description={m.available ? undefined : copy.assignOutsideHours}
              selected={managerId === m.id}
              onSelect={() => setManagerId(m.id)}
            />
          ))}
        </div>
      )}
    </ScreenLayout>
  );
}
