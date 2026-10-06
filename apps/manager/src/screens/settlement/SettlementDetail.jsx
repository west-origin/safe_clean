// Figma: Manager/Settlement/Detail (2141:2785)
// 프로토타입: Back → Settlement
// 경로 /settlement/:id — 정산 목록의 카드를 누르면 온다. 데이터 GET /manager/settlements/{id}
// — { title, subtitle, breakdown: 금액 줄들, final: { label, value } 최종 금액 }.
import { useNavigate, useParams } from 'react-router';
import { ScreenLayout, NavBar, Card, SummaryRow, cx, useAsync, ScreenError, ScreenLoading } from '@ansimclean/ui';
import { getSettlementDetail } from '../../api/manager.js';
import styles from './SettlementDetail.module.css';

/** 정산 한 건의 내역 (읽기 전용). 마지막 줄(final)만 크게, 브랜드 색으로. 뒤로가기는 정산 목록(/settlement) */
export default function SettlementDetail() {
  const { id } = useParams(); // useParams: 주소의 :id 자리 값을 읽는다
  const navigate = useNavigate();
  const { data: data, error, reload } = useAsync(() => getSettlementDetail(id), [id]);


  if (error) return <ScreenError error={error} onRetry={reload} navBar={<NavBar title="정산 상세" onBack={() => navigate('/settlement')} />} />;
  if (!data) return <ScreenLoading navBar={<NavBar title="정산 상세" onBack={() => navigate('/settlement')} />} />;

  return (
    <ScreenLayout navBar={<NavBar title="정산 상세" onBack={() => navigate('/settlement')} />}>
      <header className={styles.head}>
        <h1 className="text-title-card">{data.title}</h1>
        <p className={cx('text-body-s', styles.secondary)}>{data.subtitle}</p>
      </header>

      <Card padding="list" gap="none" divided>
        {data.breakdown.map((row) => (
          <SummaryRow key={row.label} label={row.label} value={row.value} tone={row.tone} emphasis="body" />
        ))}
        <SummaryRow label={data.final.label} value={data.final.value} tone="brand" emphasis="heading" labelBold size="lg" />
      </Card>
    </ScreenLayout>
  );
}
