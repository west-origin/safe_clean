// Figma: Home/Standard-A › HeroSlideCaption › SlideCounter (84:233) — "02" 20 Medium 검정 + "/02" 14 Medium 회색(원본색), 줄높이 22.
// 20/14 Medium 텍스트 스타일이 Figma 에 없어 Body/L(20 Regular)·Body/S(14 Regular) 클래스를 쓴다 (PROGRESS.md 판단 항목).
// 예: 고객 앱 Home 화면 히어로 캐러셀의 각 슬라이드 제목 위 ("02/02" 처럼 현재/전체 번호).
import { cx } from '../../utils/cx.js';
import styles from './SlideCounter.module.css';

/** 숫자를 두 자리 글자로 만든다. 예: 2 → "02" */
const pad2 = (n) => String(n).padStart(2, '0');

/**
 * 슬라이드 번호 표시. 보여 주기만 한다.
 * @param {object} props
 * @param {number} props.current 현재 슬라이드 (1부터)
 * @param {number} props.total
 * @param {string} [props.className]
 */
export default function SlideCounter({ current, total, className }) {
  // 화면에는 "02/02" 로 보이지만 화면 낭독기에는 aria-label 의 "2개 중 2번째" 로 읽힌다
  return (
    <p className={cx(styles.counter, className)} aria-label={`${total}개 중 ${current}번째`}>
      <span className={cx('text-body-l', styles.current)}>{pad2(current)}</span>
      <span className={cx('text-body-s', styles.total)}>/{pad2(total)}</span>
    </p>
  );
}
