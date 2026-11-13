// Figma: Repair (84:254) — 2026-11-08 사용자 요청으로 히어로·카테고리 격자를 지우고, 예약 '기본 청소'(BookingFrequency) 화면과 같은 틀로 바꿨다:
//   NavBar(뒤로) → 제목 블록(titleBlock) → '수리 종류' 카드 라디오(section) → 띠 → 고른 종류의 견적 안내(PriceGuide) → 선물 배너.
// 제목·설명·섹션 제목은 시안에 없어 코드에서 정했다(mock.js REPAIR_SCREEN_COPY). 수리 예약 플로우 입구는 두지 않는다(사용자 결정).
// 경로 /repair (하단 탭 '수리').
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { ScreenLayout, NavBar, RadioOption, PriceGuide, GiftBanner, SectionDivider, cx } from '@ansimclean/ui';
import { REPAIR_PRICE_GUIDES, REPAIR_SCREEN_COPY as COPY } from '../../api/mock.js';
import CustomerFooter from '../CustomerFooter.jsx';
import common from '../booking/booking.module.css';
import frequency from '../booking/BookingFrequency.module.css'; // 기본 청소 화면과 모양을 똑같이 쓰려고 같은 스타일을 가져온다
import styles from './Repair.module.css';

/** Repair 화면: 수리 종류(대리석 코팅 / 크랙 수리)를 고르면 그 견적 안내를 보인다 */
export default function Repair() {
  const navigate = useNavigate();
  const [selected, setSelected] = useState(0); // 처음엔 첫 종류(대리석 코팅)
  const guide = REPAIR_PRICE_GUIDES[selected];

  return (
    <ScreenLayout noPadding gap="none" navBar={<NavBar onBack={() => navigate(-1)} />} tabBar={<CustomerFooter active="repair" />}>
      <div className={frequency.body}>
        <header className={frequency.titleBlock}>
          <h1 className={cx('text-heading-m', common.sectionTitle)}>{COPY.title}</h1>
          <p className={cx('text-body-default', frequency.description)}>{COPY.description}</p>
        </header>

        <section className={cx(frequency.section, frequency.sectionBeforeGuide)}>
          <h2 className={cx('text-heading-s', common.sectionTitle)}>{COPY.sectionTitle}</h2>
          <div className={frequency.options} role="radiogroup" aria-label={COPY.sectionTitle}>
            {REPAIR_PRICE_GUIDES.map((g, i) => (
              <RadioOption key={g.title} variant="card" label={g.title} selected={selected === i} onSelect={() => setSelected(i)} />
            ))}
          </div>
        </section>

        <SectionDivider />
        <div className={frequency.question}>
          <PriceGuide guide={guide} />
        </div>

        {/* "수리 선물하러 가기 >" → 선물하기 (연결 화면이 Figma 에 없어 코드에서 정함, 2026-11-13 친구·선물 5회차) */}
        <div className={styles.gift}>
          <GiftBanner variant="repair" onCta={() => navigate('/gifts/new')} />
        </div>
      </div>
    </ScreenLayout>
  );
}
