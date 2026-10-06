// Figma: Auth/TermsConsent › ProgressWrap (2063:1800) — ProgressBar(점 4개 10px, 간격 10) + StepCounter("01/04")
// 현재 점은 brand/primary, 나머지는 흰 배경 + text/secondary 1px 테두리. 카운터는 "01/" 14 Bold 검정 + "04" 11 Bold 회색(원본색).
// 예약 플로우의 StepProgress(점 8개 8px, 카운터 없음)와 다른 컴포넌트다.
// 예: 고객 앱 가입 단계 TermsConsent(4단계), 매니저 앱 가입 단계 SignUpTerms(total=3).
import { cx } from '../../utils/cx.js';
import styles from './StepDots.module.css';

/** 숫자를 두 자리 글자로 만든다. 예: 4 → "04" */
const pad2 = (n) => String(n).padStart(2, '0');

/**
 * 가입 단계 표시: 점 줄 + "01/04" 카운터. 현재 단계의 점 하나만 채운다(지난 단계도 빈 점).
 * @param {object} props
 * @param {number} props.step 현재 단계 (1부터)
 * @param {number} [props.total=4]
 * @param {string} [props.className]
 */
export default function StepDots({ step, total = 4, className }) {
  // role="progressbar" + aria-value*: 화면 낭독기가 "4단계 중 2단계" 진행 표시로 읽는다.
  // 점과 숫자는 같은 내용을 그림으로 보여 줄 뿐이라 aria-hidden 으로 숨겨 두 번 읽히지 않게 한다.
  return (
    <div className={cx(styles.wrap, className)} role="progressbar" aria-valuemin={1} aria-valuemax={total} aria-valuenow={step} aria-label={`${total}단계 중 ${step}단계`}>
      <div className={styles.dots} aria-hidden="true">
        {Array.from({ length: total }, (_, i) => (
          <span key={i} className={cx(styles.dot, i + 1 === step && styles.active)} />
        ))}
      </div>
      <p className={styles.counter} aria-hidden="true">
        <span className={cx('text-title-small', styles.current)}>{pad2(step)}/</span>
        <span className={cx('text-caption-bold', styles.total)}>{pad2(total)}</span>
      </p>
    </div>
  );
}
