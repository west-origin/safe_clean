// Manager/Admin/Notices (코드에서 정함, rbac.md §15-23) — Figma 에 없다. 관리자 홈 '공지 관리' → 여기.
// 올린 공지를 대상(고객·매니저·전체)별 묶음으로 최신순 나열하고, 행을 누르면 수정 화면(/admin/notices/:id), '공지 올리기' 는 /admin/notices/new.
// 기존 ui 프리미티브(ScreenLayout·NavBar·MenuGroup·MenuRow·EmptyState·Button)만 조립한다. 문구는 api/mock.js ADMIN_NOTICE_COPY.
import { useNavigate } from 'react-router';
import { ScreenLayout, NavBar, MenuGroup, MenuRow, EmptyState, Button, ScreenError, ScreenLoading, useAsync, cx } from '@ansimclean/ui';
import { getAdminNotices, getAdminNoticeCopy } from '../../api/manager.js';
import styles from './admin.module.css';

/** 공지 관리 목록 (/admin/notices) */
export default function AdminNotices() {
  const navigate = useNavigate();
  const { data, error, reload } = useAsync(() => Promise.all([getAdminNoticeCopy(), getAdminNotices()]), []);
  const navBar = <NavBar title={data?.[0].listTitle ?? ''} onBack={() => navigate('/admin')} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!data) return <ScreenLoading navBar={navBar} />;
  const [copy, notices] = data;

  return (
    <ScreenLayout
      navBar={navBar}
      stickyCta={
        <Button chevron={false} onClick={() => navigate('/admin/notices/new')}>
          {copy.create}
        </Button>
      }
    >
      <p className={cx('text-body-s', styles.secondary)}>{copy.description}</p>
      {notices.length === 0 ? (
        <EmptyState icon="smile" title={copy.empty} />
      ) : (
        // 대상별 묶음 (고객 → 매니저 → 전체). 묶음 안은 서버가 준 최신순 그대로
        copy.audiences
          .filter((a) => notices.some((n) => n.audience === a.key))
          .map((a) => (
            <MenuGroup key={a.key} title={a.label}>
              {notices
                .filter((n) => n.audience === a.key)
                .map((n) => (
                  <MenuRow key={n.id} label={`${n.title} · ${n.date}`} onClick={() => navigate(`/admin/notices/${n.id}`)} />
                ))}
            </MenuGroup>
          ))
      )}
    </ScreenLayout>
  );
}
