// Settings › 공지사항 본문 (Figma 에 화면 없음)
// 들어오는 길: 공지사항 목록(Notices.jsx)의 행 → /settings/notices/:id. 본문은 서버 GET /support/notices/:id 에서 받는다.
import { useNavigate, useParams } from 'react-router';
import { ScreenLayout, NavBar, ScreenError, ScreenLoading, useAsync, cx } from '@ansimclean/ui';
import { getNotice } from '../../api/customer.js';
import styles from './settingsSub.module.css';

/** 공지 한 건의 제목·날짜·본문. 뒤로가기는 공지사항 목록으로 */
export default function NoticeDetail() {
  const navigate = useNavigate();
  const { id } = useParams(); // 주소의 :id — 바뀌면 useAsync 가 다시 받는다
  const { data: n, error, reload } = useAsync(() => getNotice(id), [id]);
  const navBar = <NavBar title="공지사항" onBack={() => navigate('/settings/notices')} />;
  // 없는 공지(404)면 제목만 '찾을 수 없어요' 로 바꾼다. 그 밖의 오류는 ScreenError 기본 문구
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} title={error?.status === 404 ? '공지를 찾을 수 없어요' : undefined} />;
  if (!n) return <ScreenLoading navBar={navBar} />;
  return (
    <ScreenLayout navBar={navBar} gap="md">
      <header className={styles.stack}>
        <h1 className="text-heading-s">{n.title}</h1>
        <p className={cx('text-caption-l', styles.secondary)}>{n.date}</p>
      </header>
      <p className={cx('text-body-default', styles.primary)}>{n.body}</p>
    </ScreenLayout>
  );
}
