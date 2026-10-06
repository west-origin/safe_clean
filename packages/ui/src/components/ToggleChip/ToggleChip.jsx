// Figma(수리 예약): 상황 선택(1:342) — 알약 radius md(20), 패딩 10/13, Label/S.
//   Default: 흰 배경 + 1px border/default + text/secondary · Selected: brand/subtle 배경 + brand/primary 테두리·글자. 중복 선택
// 고객 앱 수리 예약의 현장 상황 화면(RepairNotes)에서 여러 개를 골라 켜고 끄는 칩. 누를 수 없는 표시용 Chip 과는 다른 컴포넌트다.
import { cx } from '../../utils/cx.js';
import styles from './ToggleChip.module.css';

/**
 * @param {object} props
 * @param {string} props.label
 * @param {boolean} props.selected
 * @param {(selected: boolean) => void} [props.onToggle]
 * @param {string} [props.className]
 * 선택 상태는 aria-pressed 로 보조기기에 알리고(data-state 에도 같은 상태를 적는다), 모양은 selected 클래스로 바꾼다.
 * 누르면 onToggle(반대값) 만 부른다 — 무엇이 골라졌는지는 부모가 들고 있다.
 */
export default function ToggleChip({ label, selected, onToggle, className }) {
  return (
    <button type="button" aria-pressed={selected} data-state={selected ? 'selected' : 'default'} className={cx('text-label-s', styles.chip, selected && styles.selected, className)} onClick={() => onToggle && onToggle(!selected)}>
      {label}
    </button>
  );
}
