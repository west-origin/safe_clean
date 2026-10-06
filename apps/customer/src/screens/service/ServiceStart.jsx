// Figma: Service/Start (92:772) — VideoPoster (placeholder · 코드에서 교체) 403×852 소개 이미지(문구 포함) + "실시간 영상 보기" 314×75
// 프로토타입 연결 없음 → 버튼 = /service/live (PROGRESS.md 판단 항목). TODO(asset): 교체필요 — 소개 이미지
// 예약 카드·푸시는 이 화면을 거치지 않고 바로 /service/live 로 간다(같은 문구의 버튼을 두 번 누르던 것, 검증 검토).
// 여기는 소개 화면으로 라우트만 남아 있고, 지금 앱 안에서 이 주소로 이동하는 코드는 없다(주소로 직접 열 때만). 예약 id 가 없으면 내 예약으로
import { useNavigate, useSearchParams } from 'react-router';
import { ScreenLayout, Button, ASSETS } from '@ansimclean/ui';
import styles from './ServiceStart.module.css';

/**
 * 실시간 영상 소개 화면 (/service/start). 전체를 덮는 소개 이미지 위에 '실시간 영상 보기' 버튼 하나.
 * 버튼: 주소에 ?booking 이 있으면 그 예약의 시청 화면(/service/live?booking=…), 없으면 내 예약(/reservations)으로 이동.
 */
export default function ServiceStart() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const booking = params.get('booking'); // 주소의 ?booking=예약 id (있으면 그 예약의 시청 화면으로 넘긴다)
  // statusBar={false}: 상태바 여백 없이 이미지가 화면 맨 위부터 깔린다. 문구가 이미지 안에 있어 alt 에 같은 문구를 적는다
  return (
    <ScreenLayout statusBar={false} noPadding gap="none" className={styles.screen}>
      <img className={styles.poster} src={ASSETS.serviceStart.src} srcSet={ASSETS.serviceStart.srcSet} width={ASSETS.serviceStart.width} height={ASSETS.serviceStart.height} alt="실시간 청소영상 확인 — 지금, 내 공간이 어떻게 청소되고 있는지 확인해보세요." />
      <div className={styles.cta}>
        <Button variant="primary" className={styles.button} onClick={() => navigate(booking ? `/service/live?booking=${booking}` : '/reservations')}>
          실시간 영상 보기
        </Button>
      </div>
    </ScreenLayout>
  );
}
