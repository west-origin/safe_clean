// Manager/Admin/Reassignments (코드에서 정함 — Figma 에 없다. backend/docs/domain.md '매니저 방문 취소 승인·재배정', 2026-11-13).
// 관리자 홈 '배정 대기' 또는 어드민 푸시('재배정이 필요한 예약이 있어요', data type admin-reassign) → 여기.
// 재배정 콜로 매니저를 찾지 못한(아무도 수락 안 함·방문 임박·후보 없음) 예약을 방문 가까운 순으로 보인다.
// 카드에 예약 ID·고객 이름·연락처·방문 일시·주소·취소한 매니저·취소 사유·요청/승인 시각. '매니저 배정' → /admin/reassignments/:bookingId.
// 기존 ui 프리미티브(ScreenLayout·NavBar·Card·Button·EmptyState)만 조립한다. 문구는 api/mock.js ADMIN_REASSIGN_COPY.
import { useNavigate } from 'react-router';
import { ScreenLayout, NavBar, Card, Button, EmptyState, ScreenError, ScreenLoading, useAsync, cx } from '@ansimclean/ui';
import { getAdminReassignCopy, getReassignments } from '../../api/manager.js';
import styles from './admin.module.css';

/** 배정 대기 목록 (/admin/reassignments) */
export default function AdminReassignments() {
  const navigate = useNavigate();
  const { data, error, reload } = useAsync(() => Promise.all([getAdminReassignCopy(), getReassignments()]), []);
  const navBar = <NavBar title={data?.[0].waitingTitle ?? ''} onBack={() => navigate('/admin')} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!data) return <ScreenLoading navBar={navBar} />;
  const [copy, items] = data;

  return (
    <ScreenLayout navBar={navBar}>
      <p className={cx('text-body-s', styles.secondary)}>{copy.waitingDescription}</p>
      {items.length === 0 ? (
        <EmptyState icon="smile" title={copy.waitingEmpty} />
      ) : (
        <ul className={styles.cardList}>
          {items.map((r) => {
            // 라벨 · 값 줄. 값이 없는 줄(취소 요청 없이 대기가 된 예약 등)은 그리지 않는다
            const facts = [
              [copy.bookingIdLabel, r.bookingId],
              [copy.customerLabel, r.customer?.name],
              [copy.phoneLabel, r.customer?.phone],
              [copy.addressLabel, r.address.join(' ')],
              [copy.cancelledManagerLabel, r.cancelledManager?.name],
              [copy.reasonLabel, r.cancelReason],
              [copy.requestedLabel, r.requestedLabel],
              [copy.approvedLabel, r.approvedLabel],
            ].filter(([, value]) => value);
            return (
              <li key={r.bookingId}>
                <Card gap="sm">
                  <p className="text-title-small">{r.visitLabel}</p>
                  <p className={cx('text-body-s', styles.secondary)}>{copy.waitingReasons[r.reason] ?? r.reason}</p>
                  <dl className={styles.facts}>
                    {facts.map(([label, value]) => (
                      <div key={label} className={styles.fact}>
                        <dt className={cx('text-body-s', styles.secondary)}>{label}</dt>
                        <dd className="text-body-s">{value}</dd>
                      </div>
                    ))}
                  </dl>
                  <Button chevron={false} aria-label={`${r.visitLabel} ${copy.assignButton}`} onClick={() => navigate(`/admin/reassignments/${r.bookingId}`)}>
                    {copy.assignButton}
                  </Button>
                </Card>
              </li>
            );
          })}
        </ul>
      )}
    </ScreenLayout>
  );
}
