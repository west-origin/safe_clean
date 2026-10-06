// Figma: Booking/Frequency › Banner/RecurringPromo (2169:2259) — 393×258, 그라디언트 배경, 패딩 16/18, 간격 12.
//   TopRow: Copy(Chip "정기청소" + Heading/S + Body/XS + Caption/L) + Illustration 132×132 (일러스트 에셋 필요 → 에셋이 올 때까지 그리지 않는다)
//   BenefitRow: 혜택 알약 3개 (114×46, 흰 배경, radius 20, 아이콘 16 + Caption/L 2줄)
// Office/Appliance/MoveIn 의 Frequency 는 Figma 에 "프로모션 배너 영역 / Office 전용 이미지 필요" 라는 자리표시 프레임뿐이다.
//   그 문구가 사용자에게 그대로 보이던 것을 고쳤다 (사용성 검토 2026-09-22) → variant="benefits": 카테고리와 무관한 정기청소 혜택 알약 3개만 보여 준다.
//   배너 이미지가 오면 variant 를 되돌리고 이미지를 넣는다.
// TODO(asset): 에셋 필요 — Illustration (2169:2267) 캘린더+청소도구 일러스트. 교체필요 — 카테고리별 프로모션 배너 이미지
//
// 정기청소 안내 배너. 고객 앱 예약의 방문 주기 화면(BookingFrequency)이 서버가 준 카테고리 정보의 promo 값을 보고
// 'recurring' 이면 기본 모양, 아니면 variant="benefits" 로 그린다 (예: 고객 앱 목 데이터 BOOKING_CATEGORIES 에서는 가정/원룸만 recurring).
// 문구는 전부 Figma 그대로 이 파일 안에 고정돼 있고, props 로 바꿀 수 없다.
import { BenefitCalendarIcon, BenefitTagIcon, BenefitHomeIcon } from '../../icons/benefitIcons.jsx';
import { cx } from '../../utils/cx.js';
import styles from './PromoBanner.module.css';

// 혜택 알약 3개 (아이콘 + 두 줄 문구). 두 variant 가 같이 쓴다. \n 은 CSS pre-line 으로 줄바꿈된다.
const BENEFITS = [
  { Icon: BenefitCalendarIcon, label: '원하는 주기\n자동 예약' },
  { Icon: BenefitTagIcon, label: '정기 이용\n할인 혜택' },
  { Icon: BenefitHomeIcon, label: '언제나\n깨끗한 공간' },
];

/**
 * @param {object} props
 * @param {'recurring'|'benefits'} [props.variant='recurring'] recurring=Banner/RecurringPromo(가정/원룸), benefits=혜택 알약만 (배너 이미지가 없는 카테고리)
 * @param {string} [props.className]
 */
export default function PromoBanner({ variant = 'recurring', className }) {
  // 혜택 알약 줄을 한 번 만들어 두고 아래 두 모양에서 똑같이 넣는다. 문구가 겹치지 않아 key 로 문구를 쓴다.
  const benefits = (
    <ul className={styles.benefits}>
      {BENEFITS.map(({ Icon, label }) => (
        <li key={label} className={styles.benefit}>
          <Icon className={styles.benefitIcon} />
          <span className={cx('text-caption-l', styles.benefitLabel)}>{label}</span>
        </li>
      ))}
    </ul>
  );

  // benefits: "정기청소" 칩 + 혜택 알약만. section + aria-label 이라 보조기기에는 "정기청소 안내" 영역으로 읽힌다.
  if (variant === 'benefits') {
    return (
      <section className={cx(styles.banner, styles.benefitsOnly, className)} aria-label="정기청소 안내">
        <span className={cx('text-title-xs', styles.chip)}>정기청소</span>
        {benefits}
      </section>
    );
  }

  // recurring(기본): 위 줄에 칩·제목·부제·설명, 아래 줄에 혜택 알약. Figma 의 오른쪽 일러스트 자리는 에셋이 올 때까지 그리지 않는다.
  return (
    <section className={cx(styles.banner, className)} aria-label="정기청소 안내">
      <div className={styles.topRow}>
        <div className={styles.copy}>
          <span className={cx('text-title-xs', styles.chip)}>정기청소</span>
          <h3 className={cx('text-heading-s', styles.title)}>{'청소 걱정 없이,\n늘 깨끗한 우리 집'}</h3>
          <p className={cx('text-body-xs', styles.subtitle)}>잊고 지내도, 매일 이어지는 깨끗함</p>
          <p className={cx('text-caption-l', styles.description)}>정기청소를 이용하면 매번 예약할 필요 없이 원하는 주기에 맞춰 꾸준히 관리받을 수 있어요. 정기 이용 할인으로 1회 청소보다 합리적이에요.</p>
        </div>
      </div>
      {benefits}
    </section>
  );
}
