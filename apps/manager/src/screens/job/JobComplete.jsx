// Figma: Manager/Job/Complete (2145:2842)
// 프로토타입: 정산 내역 보기 → Settlement · 홈으로 → Home
// 매니저 앱 /job/complete 화면. JobInProgress 의 '청소 완료' 확인 시트에서 완료 요청이 성공하면 이리로 온다.
// 서버의 마지막 완료 작업(getCompletedJob → GET /manager/jobs/last-completed)을 받아 제목·요약 행·예상 정산을 그린다.
// 요약 행(청소 시간·총 소요·체크리스트·촬영 기록)과 문구는 서버가 만든 그대로 보여 준다.
import { useNavigate } from 'react-router';
import { ScreenLayout, Card, Button, StatusBadge, SummaryRow, CompleteCheckIcon, cx, usePolling, ScreenError, ScreenLoading } from '@ansimclean/ui';
import { getCompletedJob } from '../../api/manager.js';
import styles from './JobComplete.module.css';

// 완료 요약 화면. 버튼: '정산 내역 보기' → /settlement, '홈으로' → /
export default function JobComplete() {
  const navigate = useNavigate();
  // 화면이 보이는 동안 10초마다 조용히 다시 받아, 촬영 기록 '업로드 중' 이 '정상 업로드'·'업로드 실패' 로 바뀌는 것을 보여준다 (검증 검토 — 완료 직후 스냅샷 한 번뿐이었다)
  // 업로드가 끝나도 폴링은 멈추지 않는다 — 촬영 기록 상태와 상관없이 이 화면을 떠날 때까지 돈다
  const { data: data, error, reload } = usePolling(() => getCompletedJob(), [], { interval: 10_000 });


  if (error) return <ScreenError error={error} onRetry={reload} onBack={() => navigate(-1)} />;
  if (!data) return <ScreenLoading />;

  return (
    <ScreenLayout
      topPadding="lg"
      stickyCta={
        <>
          <Button variant="secondary" onClick={() => navigate('/settlement')}>
            정산 내역 보기
          </Button>
          <Button onClick={() => navigate('/')}>홈으로</Button>
        </>
      }
    >
      <header className={styles.head}>
        <CompleteCheckIcon className={styles.icon} />
        <h1 className="text-heading-m">{data.title}</h1>
        <p className={cx('text-body-s', styles.secondary)}>{data.subtitle}</p>
      </header>

      {/* 요약 행. tone 은 서버가 정한다 (예: 체크리스트를 다 채웠으면 success, 촬영 기록 업로드 실패면 issue) */}
      <Card padding="list" gap="none" divided>
        {data.summary.map((row) => (
          <SummaryRow key={row.label} label={row.label} value={row.value} tone={row.tone} />
        ))}
      </Card>

      {/* 예상 정산 카드. 금액은 서버가 정산 행이 있으면 그 최종 금액, 없으면 예약 금액 − 수수료(매니저 몫)로 준다 */}
      <Card tone="subtle" padding="alert" gap="xs">
        <div className={styles.row}>
          <span className={cx('text-body-s', styles.secondary)}>{data.payout.label}</span>
          <StatusBadge status="complete" />
        </div>
        <p className={cx('text-heading-xl', styles.brand)}>{data.payout.amount}</p>
        <p className={cx('text-body-s', styles.secondary)}>{data.payout.note}</p>
      </Card>
    </ScreenLayout>
  );
}
