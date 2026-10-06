// Figma: Manager/Profile/Equipment (2141:2903)
// 프로토타입: Back → Profile. (B5, §9.2-4) 촬영 장비 = 별도 바디캠(결정 §13-18) → 휴대폰 카메라 테스트 대신 바디캠 설정 정보(RTMP 주소·마스킹된 키·마지막 연결)를 보여준다 (코드에서 정함)
// 경로 /profile/equipment — MY 메뉴 '촬영 장비' 에서 온다. 데이터는 GET /manager/device (api/manager.js getEquipmentDevice).
// 흐름: 받는 중 → 404(바디캠 미등록)면 안내 화면, 그 밖의 오류면 다시 시도 화면 → 기기 이름·연결 배지·항목별 상태·설정 정보.
// 읽기만 하는 화면이다 — 서버에 쓰는 동작은 없다.
import { useNavigate } from 'react-router';
import { ScreenLayout, NavBar, Card, StatusBadge, CameraStatusItem, SummaryRow, useAsync, ScreenError, ScreenLoading, cx } from '@ansimclean/ui';
import { getEquipmentDevice } from '../../api/manager.js';
import { EQUIPMENT_SETUP_COPY } from '../../api/mock.js';
import styles from './Equipment.module.css';

/** 촬영 장비(바디캠) 화면. 뒤로가기는 MY(/profile) 로 */
export default function Equipment() {
  const navigate = useNavigate();
  const { data: device, error, reload } = useAsync(() => getEquipmentDevice(), []);

  const navBar = <NavBar title="촬영 장비" onBack={() => navigate('/profile')} />;
  // 404 = 등록된 바디캠이 없다. 네트워크·서버 오류는 '미등록' 이 아니라 일반 오류 문구로(다시 시도)
  // 미등록 안내에는 error 를 넘기지 않는다 — 그래서 404 인데도 '다시 시도' 버튼이 보인다 (ErrorState 는 error 가 404 일 때만 버튼을 숨긴다)
  if (error?.status === 404) return <ScreenError onRetry={reload} navBar={navBar} title={EQUIPMENT_SETUP_COPY.missingTitle} description={EQUIPMENT_SETUP_COPY.missingDescription} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!device) return <ScreenLoading navBar={<NavBar title="촬영 장비" onBack={() => navigate('/profile')} />} />;

  return (
    <ScreenLayout navBar={<NavBar title="촬영 장비" onBack={() => navigate('/profile')} />} gap="md">
      <header className={styles.head}>
        <h1 className="text-heading-s">{device.name}</h1>
        {/* 배지 모양: 서버가 주는 online(참/거짓)을 먼저 보고, 없으면(목 데이터) 연결 문구가 '정상 연결' 인지로 정한다. 글자는 connection 문구 */}
        <div>
          <StatusBadge status={(device.online ?? device.connection === '정상 연결') ? 'complete' : 'paused'} label={device.connection} />
        </div>
      </header>

      <Card padding="list" gap="none" divided>
        {/* 항목별 상태 줄 (목 데이터: 배터리·네트워크·카메라·실시간 전송) */}
        {device.status.map((item) => (
          <CameraStatusItem key={item.label} status={item.status} label={item.label} value={item.value} />
        ))}
      </Card>

      <section className={styles.head}>
        <h2 className="text-title-card">{EQUIPMENT_SETUP_COPY.title}</h2>
        <Card padding="list" gap="none" divided>
          {/* 바디캠에 넣을 송출 주소·스트림 키. 키는 서버가 가린 값(keyMasked)만 준다. 값이 없으면 '없음' */}
          <SummaryRow label={EQUIPMENT_SETUP_COPY.url} value={device.stream?.url ?? EQUIPMENT_SETUP_COPY.none} emphasis="body" size="sm" />
          <SummaryRow label={EQUIPMENT_SETUP_COPY.key} value={device.stream?.keyMasked ?? EQUIPMENT_SETUP_COPY.none} emphasis="body" size="sm" />
          <SummaryRow label={EQUIPMENT_SETUP_COPY.lastOnline} value={device.lastOnline ?? EQUIPMENT_SETUP_COPY.none} emphasis="body" size="sm" />
        </Card>
        <p className={cx('text-body-s', styles.secondary)}>{EQUIPMENT_SETUP_COPY.guide}</p>
      </section>
    </ScreenLayout>
  );
}
