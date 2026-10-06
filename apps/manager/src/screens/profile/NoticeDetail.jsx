// Manager/Profile › 공지사항 본문 — Figma 에 화면 없음 (코드에서 정함)
// 경로 /profile/notices/:id — 공지사항 목록(Notices.jsx)에서 한 줄을 누르면 온다.
// 데이터 { title, date, body } 는 GET /support/notices/{id}?audience=manager (getManagerNotice).
import { useNavigate, useParams } from 'react-router';
import { ScreenLayout, NavBar, ScreenError, ScreenLoading, useAsync, cx } from '@ansimclean/ui';
import { getManagerNotice } from '../../api/manager.js';
import styles from './profileSub.module.css';

/**
 * 공지 한 건: 제목·날짜·본문. 없는 공지(404)는 '공지를 찾을 수 없어요' 로 바꿔 보인다 (404 는 다시 시도 버튼이 숨는다).
 * 본문은 styles.primary(white-space: pre-line) 라 서버 본문의 줄바꿈이 그대로 보인다.
 */
export default function NoticeDetail() {
  const navigate = useNavigate();
  const { id } = useParams(); // useParams: 주소의 :id 자리 값을 읽는다
  const { data: n, error, reload } = useAsync(() => getManagerNotice(id), [id]);
  const navBar = <NavBar title="공지사항" onBack={() => navigate('/profile/notices')} />;
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
