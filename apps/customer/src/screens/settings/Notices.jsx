// Settings › 공지사항 목록 (Figma 에 화면 없음 — 설정 메뉴와 같은 MenuGroup 골격)
// 들어오는 길: 설정 화면의 '공지사항' 메뉴(/settings/notices). 목록은 서버 GET /support/notices?audience=customer.
// 행을 누르면 본문 화면(NoticeDetail, /settings/notices/:id)으로 간다.
import { useNavigate } from 'react-router';
import { ScreenLayout, NavBar, MenuGroup, MenuRow, EmptyState, ScreenError, ScreenLoading, useAsync } from '@ansimclean/ui';
import { getNotices } from '../../api/customer.js';

/** 공지사항 목록 화면. 공지가 없으면 빈 상태, 있으면 '제목 · 날짜' 한 줄씩 */
export default function Notices() {
  const navigate = useNavigate();
  const { data, error, reload } = useAsync(() => getNotices(), []);
  const navBar = <NavBar title="공지사항" onBack={() => navigate('/settings')} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!data) return <ScreenLoading navBar={navBar} />;
  return (
    <ScreenLayout navBar={navBar}>
      {data.length === 0 ? (
        <EmptyState icon="smile" title="공지사항이 없어요" />
      ) : (
        <MenuGroup title="전체">
          {data.map((n) => (
            <MenuRow key={n.id} label={`${n.title} · ${n.date}`} onClick={() => navigate(`/settings/notices/${n.id}`)} />
          ))}
        </MenuGroup>
      )}
    </ScreenLayout>
  );
}
