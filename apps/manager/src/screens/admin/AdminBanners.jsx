// Manager/Admin/Banners (코드에서 정함, rbac.md §15-22) — Figma 에 없다. 관리자 홈 '배너 관리' → 여기.
// 고객 앱 홈 위쪽 혜택 배너를 고객에게 보이는 순서대로 나열한다(지난·예정 배너 포함, 상태·기간 표시).
// '위로'·'아래로' 로 순서를 바꾸고(서버 PUT /admin/banners/order), '수정' 은 /admin/banners/:id, '배너 올리기' 는 /admin/banners/new.
// 기존 ui 프리미티브(ScreenLayout·NavBar·Card·Button·EmptyState·ActionError)만 조립한다. 문구는 api/mock.js ADMIN_BANNER_COPY.
import { useNavigate } from 'react-router';
import { ScreenLayout, NavBar, Card, Button, EmptyState, ActionError, ScreenError, ScreenLoading, useAsync, useAction, showToast, cx } from '@ansimclean/ui';
import { getAdminBanners, getAdminBannerCopy, reorderAdminBanners } from '../../api/manager.js';
import styles from './admin.module.css';

/** 배너 관리 목록 (/admin/banners) */
export default function AdminBanners() {
  const navigate = useNavigate();
  const { data, error, reload, setData } = useAsync(() => Promise.all([getAdminBannerCopy(), getAdminBanners()]), []);
  // 한 칸 옮기기: 옆 배너와 자리를 바꾼 전체 id 목록을 보내고, 서버가 준 새 목록으로 바꾼다
  const move = useAction(async (index, delta) => {
    const ids = data[1].map((b) => b.id);
    [ids[index], ids[index + delta]] = [ids[index + delta], ids[index]];
    const next = await reorderAdminBanners(ids);
    setData([data[0], next]);
    showToast(data[0].moved);
  });
  const navBar = <NavBar title={data?.[0].listTitle ?? ''} onBack={() => navigate('/admin')} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!data) return <ScreenLoading navBar={navBar} />;
  const [copy, banners] = data;

  return (
    <ScreenLayout
      navBar={navBar}
      stickyCta={
        <Button chevron={false} onClick={() => navigate('/admin/banners/new')}>
          {copy.create}
        </Button>
      }
    >
      <p className={cx('text-body-s', styles.secondary)}>{copy.description}</p>
      <ActionError error={move.error} />
      {banners.length === 0 ? (
        <EmptyState icon="smile" title={copy.empty} />
      ) : (
        <ol className={styles.bannerList}>
          {banners.map((b, i) => (
            <li key={b.id}>
              <Card gap="sm">
                {b.imageUrl && <img className={styles.bannerImage} src={b.imageUrl} alt={b.description ? `${b.title}. ${b.description}` : b.title} />}
                <p className="text-title-small">{`${i + 1}. ${b.title}`}</p>
                <p className={cx('text-body-s', styles.secondary)}>{`${b.stateLabel} · ${b.periodLabel}`}</p>
                <div className={styles.bannerActions}>
                  <Button variant="outlineGray" chevron={false} aria-label={`${b.title} ${copy.edit}`} onClick={() => navigate(`/admin/banners/${b.id}`)}>
                    {copy.edit}
                  </Button>
                  <Button variant="outlineGray" chevron={false} aria-label={`${b.title} ${copy.moveUp}`} disabled={i === 0 || move.loading} onClick={() => move.run(i, -1)}>
                    {copy.moveUp}
                  </Button>
                  <Button variant="outlineGray" chevron={false} aria-label={`${b.title} ${copy.moveDown}`} disabled={i === banners.length - 1 || move.loading} onClick={() => move.run(i, 1)}>
                    {copy.moveDown}
                  </Button>
                </div>
              </Card>
            </li>
          ))}
        </ol>
      )}
    </ScreenLayout>
  );
}
