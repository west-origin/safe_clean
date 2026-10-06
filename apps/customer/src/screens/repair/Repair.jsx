// Figma: Repair (84:254) — 히어로(수리 서비스, 점 1개) + RepairCategoryGrid 8종 + GiftBanner(수리 문구) + TabBar(Repair)
// 카테고리 8개 → 수리 예약 플로우(Figma rGCjx3zWMBk18fXgNZ9UYX, 2026-09-21). 가전수리는 가전 종류 → 증상 → …, 나머지 7개는 Figma 에 가전·증상 화면이 없어 증상 상세부터 (PROGRESS.md 판단 항목)
// 경로 /repair (하단 탭 '수리'). 히어로 문구·그림 키는 GET /customer/repair → { hero } 로 받는다.
import { useNavigate } from 'react-router';
import { ScreenLayout, TabBar, RepairCategoryList, GiftBanner, CarouselDots, ASSETS, cx, useAsync, ScreenError, ScreenLoading } from '@ansimclean/ui';
import { getRepair } from '../../api/customer.js';
import { useCustomerTabs } from '../useCustomerTabs.js';
import styles from './Repair.module.css';

// 예전 Figma 임시 연결(수리 칸 → 청소 예약 home·move-in·appliance·office 반복)은 수리 예약 플로우가 생기며 없앴다 — 이동은 아래 onSelect

/** Repair 화면: 히어로 + 수리 카테고리 격자 + 선물 배너. 칸을 누르면 그 카테고리의 수리 예약 플로우 첫 단계로 간다 */
export default function Repair() {
  const navigate = useNavigate();
  const selectTab = useCustomerTabs();
  const { data, error, reload } = useAsync(() => getRepair(), []);
  const hero = data?.hero; // 응답에 hero 가 없으면 히어로 영역을 그리지 않는다


  // 오류·로딩 중에도 하단 탭은 그대로 둔다
  if (error) return <ScreenError error={error} onRetry={reload} tabBar={<TabBar active="repair" onSelect={selectTab} />} />;
  if (!data) return <ScreenLoading tabBar={<TabBar active="repair" onSelect={selectTab} />} />;

  const onSelect = (key) => {
    navigate(key === 'appliance' ? `/repair/booking/${key}/appliance` : `/repair/booking/${key}/detail`); // 가전수리만 가전·증상 단계가 있다 (Figma 는 가전수리 8화면)
  };

  // 히어로는 Figma 대로 점 1개(장 하나)라 넘기는 동작이 없다. 그림은 장식(alt="")
  return (
    <ScreenLayout noPadding gap="none" className={styles.screen} tabBar={<TabBar active="repair" onSelect={selectTab} />}>
      <div className={styles.content}>
        {hero && (
          <section className={styles.hero} aria-label="수리 서비스 소개">
            <header className={styles.heroHeader}>
              <p className={cx('text-body-m', styles.eyebrow)}>{hero.eyebrow}</p>
              <h1 className={cx('text-heading-m', styles.title)}>{hero.headline}</h1>
            </header>
            {ASSETS[hero.image] && <img className={styles.heroArt} src={ASSETS[hero.image].src} srcSet={ASSETS[hero.image].srcSet} width={ASSETS[hero.image].width} height={ASSETS[hero.image].height} alt="" />}
            <div className={styles.slideInfo}>
              <div className={styles.caption}>
                <h2 className={cx('text-heading-xl', styles.slideTitle)}>{hero.title}</h2>
                <p className={cx('text-body-s', styles.slideDescription)}>{hero.description}</p>
              </div>
              <CarouselDots index={0} count={1} />
            </div>
          </section>
        )}
        <RepairCategoryList onSelect={onSelect} />
        {/* TODO(figma): "수리 선물하러 가기 >" 의 연결 화면이 Figma 에 없음 */}
        <GiftBanner variant="repair" />
      </div>
    </ScreenLayout>
  );
}
