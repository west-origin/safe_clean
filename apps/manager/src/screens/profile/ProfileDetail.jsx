// Manager/Profile › 매니저 정보·활동 지역·신원 인증·교육 이수·정산 계좌 — Figma 에 화면 없음 → 읽기 전용 정보 화면 하나로 (코드에서 정함). 수정은 고객센터로
// 경로 /profile/:key (key = info·area·identity·education·account, MY 메뉴 항목의 key). 데이터는 GET /manager/profile/{key}.
// 응답 { title, rows?, chips?, note? } 에서 있는 것만 그린다: rows = 라벨·값 카드, chips = 칩 묶음(예: 활동 지역), note = 아래 안내 문구.
import { useNavigate, useParams } from 'react-router';
import { ScreenLayout, NavBar, Card, SummaryRow, Chip, ScreenError, ScreenLoading, useAsync, cx } from '@ansimclean/ui';
import { getProfileDetail } from '../../api/manager.js';
import styles from './profileSub.module.css';

/** MY 하위 정보 화면 하나 (key 에 따라 내용이 바뀐다). 제목은 응답의 title 이라, 받기 전·오류 화면의 상단 바는 'MY' */
export default function ProfileDetail() {
  const navigate = useNavigate();
  const { key } = useParams(); // useParams: 주소의 :key 자리 값을 읽는다
  const { data, error, reload } = useAsync(() => getProfileDetail(key), [key]);
  const back = () => navigate('/profile');
  if (error) return <ScreenError error={error} onRetry={reload} navBar={<NavBar title="MY" onBack={back} />} title={error?.status === 404 ? '정보를 찾을 수 없어요' : undefined} />;
  if (!data) return <ScreenLoading navBar={<NavBar title="MY" onBack={back} />} />;
  return (
    <ScreenLayout navBar={<NavBar title={data.title} onBack={back} />} gap="md">
      {data.rows && (
        <Card padding="list" gap="none" divided>
          {data.rows.map((r) => (
            <SummaryRow key={r.label} label={r.label} value={r.value} emphasis="body" size="sm" />
          ))}
        </Card>
      )}
      {data.chips && (
        <div className={styles.chips}>
          {data.chips.map((c) => (
            <Chip key={c}>{c}</Chip>
          ))}
        </div>
      )}
      {data.note && <p className={cx('text-body-s', styles.secondary)}>{data.note}</p>}
    </ScreenLayout>
  );
}
