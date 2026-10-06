// Figma: StepProgress (2081:1907) — 예약 플로우 진행 표시. 점 8개(8px, 간격 6), Step=1~8 이 brand/primary.
// 점 total 개 중 step 번째 하나만 색을 칠한다(지나온 단계를 채우는 방식이 아니다).
// 쓰는 곳: 공용 UI StepNavBar 안 (화면은 StepNavBar 를 통해 쓴다). 가입 단계의 StepDots(점 + "01/04")와는 다른 컴포넌트다.
import { cx } from '../../utils/cx.js';
import styles from './StepProgress.module.css';

/**
 * @param {object} props
 * @param {number} props.step 현재 단계 1~8 (Figma "Step")
 * @param {number} [props.total=8]
 * @param {string} [props.className]
 */
// 단계 점 줄. 스크린리더에는 progressbar 역할 + "8단계 중 3단계" 같은 이름으로 읽힌다(점 자체는 이름이 없다).
export default function StepProgress({ step, total = 8, className }) {
  return (
    <div
      role="progressbar"
      aria-valuemin={1}
      aria-valuemax={total}
      aria-valuenow={step}
      aria-label={`${total}단계 중 ${step}단계`}
      className={cx(styles.progress, className)}
    >
      {/* 점 total 개를 만든다. i 는 0부터라 i + 1 이 단계 번호다. 순서가 바뀌지 않는 목록이라 key 로 i 를 쓴다 */}
      {Array.from({ length: total }, (_, i) => (
        <span key={i} className={cx(styles.dot, i + 1 === step && styles.active)} />
      ))}
    </div>
  );
}
