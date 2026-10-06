// Manager/Profile › 패널티 내역 (Figma 에 없음 — 매니저 정책 backend/docs/manager-policy.md, 사용자 승인 2026-09-22)
// 누적 점수 · 다음 단계 안내 · 정지 중이면 기간 · 이력(취소·미방문·지각·무사고 회복)
// 경로 /profile/penalties — MY 메뉴 '패널티 내역' 과 패널티 푸시(type penalty)를 눌렀을 때 온다.
// 데이터 { points, nextThreshold, suspension, items } 는 GET /manager/penalties. suspension 은 정지 중일 때만 값이 있고(아니면 null),
// nextThreshold 는 모든 단계를 넘었으면 null 이다 — 둘은 각각 값이 있을 때만 그린다.
import { useNavigate } from 'react-router';
import { ScreenLayout, NavBar, Card, SummaryRow, EmptyState, InfoNote, cx, useAsync, ScreenError, ScreenLoading } from '@ansimclean/ui';
import { getPenalties } from '../../api/manager.js';
import { PENALTY_COPY } from '../../api/mock.js';
import styles from './profileSub.module.css';

/** 패널티 내역 화면 (읽기 전용). 뒤로가기는 고정 경로가 아니라 직전 화면(navigate(-1)) */
export default function Penalties() {
  const navigate = useNavigate();
  const { data, error, reload } = useAsync(() => getPenalties(), []);
  const navBar = <NavBar title={PENALTY_COPY.title} onBack={() => navigate(-1)} />;

  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!data) return <ScreenLoading navBar={navBar} />;

  return (
    <ScreenLayout navBar={navBar}>
      <Card tone="subtle" gap="sm">
        <p className={cx('text-body-s', styles.secondary)}>{PENALTY_COPY.pointsLabel}</p>
        <p className="text-heading-xl">{data.points}점</p>
        {data.nextThreshold && <p className={cx('text-body-s', styles.secondary)}>{data.nextThreshold}</p>}
      </Card>

      {data.suspension && <InfoNote tone="warning" glyph="!">{data.suspension}</InfoNote>}

      {data.items.length === 0 ? (
        <EmptyState icon="smile" title={PENALTY_COPY.empty} description={PENALTY_COPY.emptyDescription} />
      ) : (
        <Card padding="list" gap="none" divided>
          {/* 이력 한 줄: '사유 · 날짜' 와 점수(예: '+1점'). 서버 상태가 '적용 중' 이 아니면(면제됨·기간 만료) 점수를 회색으로 */}
          {data.items.map((item) => (
            <SummaryRow
              key={item.id}
              label={`${item.title} · ${item.date}`}
              value={item.points}
              tone={item.status === '적용 중' ? 'default' : 'secondary'}
              emphasis="card"
            />
          ))}
        </Card>
      )}

      <InfoNote glyph="ⓘ">{PENALTY_COPY.guide}</InfoNote>
    </ScreenLayout>
  );
}
