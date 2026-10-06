// Figma: Manager/Job/InProgress › ChecklistProgress › ProgressTrack/ProgressFill — 높이 8, surface/subtle 트랙 + brand/primary 채움, pill
//
// 가로 진행 막대. 회색 트랙 위에 파란 채움이 value/max 비율만큼 차오른다.
// 예: 매니저 앱 작업 진행 화면(JobInProgress)이 청소 체크리스트의 완료 개수 / 전체 개수를 보여 준다.
import { cx } from '../../utils/cx.js';
import styles from './ProgressBar.module.css';

/**
 * @param {object} props
 * @param {number} props.value 완료 수
 * @param {number} props.max 전체 수
 * @param {string} [props.label] 접근성 이름 (예: "청소 체크리스트")
 * @param {string} [props.className]
 */
export default function ProgressBar({ value, max, label, className }) {
  // 채움 비율(0~1). 전체가 0개면 0 으로 나누지 않게 0, 값이 범위를 벗어나도 막대 밖으로 넘치지 않게 0~1 로 자른다.
  const ratio = max > 0 ? Math.min(1, Math.max(0, value / max)) : 0;
  // role="progressbar" + aria-value*(최소·최대·현재 값)로 보조기기가 진행 정도를 읽는다.
  // 채움 폭만 인라인 style 로 준다 — 값에 따라 매번 바뀌는 숫자라서다. 색·모양은 CSS Module 에 있다.
  return (
    <div
      role="progressbar"
      aria-valuemin={0}
      aria-valuemax={max}
      aria-valuenow={value}
      aria-label={label}
      className={cx(styles.track, className)}
    >
      <div className={styles.fill} style={{ width: `${ratio * 100}%` }} />
    </div>
  );
}
