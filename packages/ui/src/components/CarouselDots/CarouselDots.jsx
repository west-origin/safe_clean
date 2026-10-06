// Figma: Home/Standard-A › CarouselDots (2103:2212) — 점 10px, 간격 10, 가운데. Dot/Inactive = text/secondary, Dot/Active = text/primary.
// 캐러셀(넘기는 배너)의 지금 위치를 점으로 보여 준다. 예: 고객 앱 홈(Home)·수리 탭(Repair)의 큰 배너.
import { cx } from '../../utils/cx.js';
import styles from './CarouselDots.module.css';

/**
 * @param {object} props
 * @param {number} props.index 현재 슬라이드 (0부터)
 * @param {number} props.count 슬라이드 수
 * @param {string} [props.className]
 */
export default function CarouselDots({ index, count, className }) {
  return (
    // 점을 눌러 넘기는 동작은 없다(Figma) — tab 패턴이 아니라 '현재 n/count' 를 알리는 표시로 둔다
    <div className={cx(styles.dots, className)} role="img" aria-label={`슬라이드 ${index + 1} / ${count}`}>
      {/* count 개의 점, 지금 장(index)만 active. 점마다 aria-hidden 이라 낭독기는 위 aria-label 만 읽는다 */}
      {Array.from({ length: count }, (_, i) => (
        <span key={i} aria-hidden="true" className={cx(styles.dot, i === index && styles.active)} />
      ))}
    </div>
  );
}
