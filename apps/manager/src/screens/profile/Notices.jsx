// Manager/Profile › 공지사항 목록 — Figma 에 화면 없음 (코드에서 정함)
// 경로 /profile/notices — MY 메뉴 '공지사항'(메뉴 key notice)에서 온다.
// 데이터 [{ id, title, date }] 는 GET /support/notices?audience=manager (getManagerNotices).
import { useNavigate } from 'react-router';
import { ScreenLayout, NavBar, MenuGroup, MenuRow, EmptyState, ScreenError, ScreenLoading, useAsync } from '@ansimclean/ui';
import { getManagerNotices } from '../../api/manager.js';

/** 공지 목록. 비어 있으면 EmptyState, 한 줄('제목 · 날짜')을 누르면 /profile/notices/:id 본문으로 */
export default function Notices() {
  const navigate = useNavigate();
  const { data, error, reload } = useAsync(() => getManagerNotices(), []);
  const navBar = <NavBar title="공지사항" onBack={() => navigate('/profile')} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!data) return <ScreenLoading navBar={navBar} />;
  return (
    <ScreenLayout navBar={navBar}>
      {data.length === 0 ? (
        <EmptyState icon="smile" title="공지사항이 없어요" />
      ) : (
        <MenuGroup title="전체">
          {data.map((n) => (
            <MenuRow key={n.id} label={`${n.title} · ${n.date}`} onClick={() => navigate(`/profile/notices/${n.id}`)} />
          ))}
        </MenuGroup>
      )}
    </ScreenLayout>
  );
}
