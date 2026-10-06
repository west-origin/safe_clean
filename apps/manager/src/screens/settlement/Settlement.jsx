// Figma: Manager/Settlement (2141:2698)
// 프로토타입: SettlementCard → Settlement/Detail · 탭 → 각 탭 화면
// 경로 /settlement (하단 탭 정산). 데이터 GET /manager/settlements — { monthly, stats, items, nextBefore }.
// 목록은 한 페이지씩 오고(서버 기본 50건), nextBefore 가 있으면 '더 보기' 로 다음 페이지를 이어 붙인다.
import { useNavigate } from 'react-router';
import { ScreenLayout, ManagerTabBar, Card, SummaryRow, SettlementCard, EmptyState, Button, cx, useAsync, useAction, ScreenError, ScreenLoading, ActionError } from '@ansimclean/ui';
import { getSettlement } from '../../api/manager.js';
import { useManagerTabs } from '../useManagerTabs.js';
import styles from './Settlement.module.css';

/** 정산 탭 화면: 이번 달 합계 카드, 요약 줄, 업무별 정산 카드 목록. 카드를 누르면 /settlement/:id */
export default function Settlement() {
  const navigate = useNavigate();
  const goTab = useManagerTabs();
  const { data: data, error, reload, setData } = useAsync(() => getSettlement(), []);
  // '더 보기' — 다음 페이지를 이어 붙인다
  const more = useAction(async () => {
    const next = await getSettlement({ before: data.nextBefore }); // nextBefore 는 서버가 준 커서 문자열 — 뜻을 풀지 않고 그대로 돌려보낸다
    setData((d) => ({ ...d, items: [...d.items, ...next.items], nextBefore: next.nextBefore })); // 마지막 페이지면 nextBefore 가 null 이라 '더 보기' 가 사라진다
  });


  if (error) return <ScreenError error={error} onRetry={reload} tabBar={<ManagerTabBar active="settlement" onSelect={goTab} />} />;
  if (!data) return <ScreenLoading tabBar={<ManagerTabBar active="settlement" onSelect={goTab} />} />;

  return (
    <ScreenLayout tabBar={<ManagerTabBar active="settlement" onSelect={goTab} />}>
      <h1 className="text-heading-s">정산</h1>

      <Card tone="subtle" gap="sm">
        <p className={cx('text-body-s', styles.secondary)}>{data.monthly.label}</p>
        <p className={cx('text-heading-xl', styles.brand)}>{data.monthly.amount}</p>
      </Card>

      <Card padding="list" gap="none" divided>
        {data.stats.map((row) => (
          <SummaryRow key={row.label} label={row.label} value={row.value} tone={row.tone} emphasis="card" />
        ))}
      </Card>

      <section className={styles.list}>
        <h2 className="text-title-card">업무별 정산</h2>
        {/* 빈 목록에 제목만 남아 화면이 끊겨 보였다 (사용성 검토 2026-09-22) */}
        {data.items.length === 0 && <EmptyState icon="calendar" title="아직 정산 내역이 없어요" description="청소를 완료하면 여기에 쌓여요." />}
        {data.items.map((item) => (
          <SettlementCard
            key={item.id}
            date={item.date}
            status={item.status}
            title={item.title}
            amount={item.amount}
            onClick={() => navigate(`/settlement/${item.id}`)}
          />
        ))}
        {data.nextBefore && (
          <>
            <ActionError error={more.error} fallback="더 불러오지 못했어요. 다시 시도해주세요." />
            <Button variant="outlineGray" loading={more.loading} onClick={more.run}>
              더 보기
            </Button>
          </>
        )}
      </section>
    </ScreenLayout>
  );
}
