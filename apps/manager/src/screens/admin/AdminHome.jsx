// Manager/Admin/Home (코드에서 정함, rbac.md §15-20·24) — Figma 에 없다. 어드민이 MFA 를 통과하면 매니저 탭 대신 이 화면만 본다.
// 들어오는 길: MfaVerify 성공, 또는 RequireAuth(어드민이 다른 주소로 왔을 때).
// 메뉴: 방문 취소 요청 → /admin/cancel-requests · 배정 대기 → /admin/reassignments (2026-11-13) · 배너 관리 → /admin/banners · 공지 관리 → /admin/notices.
// 기존 ui 프리미티브(ScreenLayout·Card·Button)만 조립한다. 문구는 api/mock.js ADMIN_COPY.
import { useNavigate } from 'react-router';
import { ScreenLayout, Card, Button, MenuGroup, MenuRow, ScreenError, ScreenLoading, useAsync, useAction, cx } from '@ansimclean/ui';
import { getAdminCopy, getAdminReassignCopy, getAdminNoticeCopy, getAdminBannerCopy, currentUserName, logout } from '../../api/manager.js';
import styles from './admin.module.css';

/** 관리자 홈 (/admin). 이름·안내 + 관리 메뉴 + 로그아웃 */
export default function AdminHome() {
  const navigate = useNavigate();
  const { data, error, reload } = useAsync(() => Promise.all([getAdminCopy(), getAdminNoticeCopy(), getAdminBannerCopy(), getAdminReassignCopy()]), []);
  const out = useAction(async () => {
    await logout(); // 서버 요청이 실패해도 던지지 않고 세션을 지운다
    navigate('/auth/login', { replace: true });
  });
  if (error) return <ScreenError error={error} onRetry={reload} />;
  if (!data) return <ScreenLoading />;
  const [copy, noticeCopy, bannerCopy, reassignCopy] = data;

  return (
    <ScreenLayout topPadding="lg">
      <header className={styles.header}>
        <h1 className="text-heading-l">{copy.title}</h1>
        <p className={cx('text-body-s', styles.secondary)}>{copy.description}</p>
      </header>

      <Card tone="muted" gap="md">
        <p className="text-heading-s">{currentUserName()}</p>
      </Card>

      {/* 관리 메뉴: 방문 취소 요청 · 배정 대기(매니저 방문 취소 승인·재배정) · 배너 관리(고객 앱 홈 혜택 배너) · 공지 관리 */}
      <MenuGroup title={copy.menuTitle}>
        <MenuRow label={reassignCopy.requestsMenu} onClick={() => navigate('/admin/cancel-requests')} />
        <MenuRow label={reassignCopy.waitingMenu} onClick={() => navigate('/admin/reassignments')} />
        <MenuRow label={bannerCopy.menu} onClick={() => navigate('/admin/banners')} />
        <MenuRow label={noticeCopy.menu} onClick={() => navigate('/admin/notices')} />
      </MenuGroup>

      <Button variant="secondary" chevron={false} loading={out.loading} onClick={out.run}>
        {copy.logout}
      </Button>
    </ScreenLayout>
  );
}
