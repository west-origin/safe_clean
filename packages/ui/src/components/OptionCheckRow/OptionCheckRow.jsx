// 2026-11-01 사용자 첨부 '옵션을 선택해 주세요.' 시안의 무료 옵션 한 줄 — Figma 에 없음 (코드에서 정함).
// 흰 카드: 왼쪽 아이콘 + 이름, 오른쪽 원형 표시(RadioOption card 와 같은 모양). 여러 개 고를 수 있어 role="checkbox".
import { cx } from '../../utils/cx.js';
import styles from './OptionCheckRow.module.css';

/**
 * @param {object} props
 * @param {import('react').ReactNode} props.icon 왼쪽 그림 (아이콘 컴포넌트 또는 <img>)
 * @param {string} props.label 옵션 이름 (줄바꿈 '\n' 은 공백으로 한 줄)
 * @param {boolean} props.selected
 * @param {boolean} [props.disabled] 한도까지 골라 더 못 고를 때
 * @param {() => void} [props.onToggle]
 * @param {string} [props.className]
 */
export default function OptionCheckRow({ icon, label, selected, disabled = false, onToggle, className }) {
  const name = label.replace(/\s*\n\s*/g, ' ').trim();
  return (
    <button type="button" role="checkbox" aria-checked={selected} aria-label={name} disabled={disabled} className={cx(styles.row, selected && styles.selected, className)} onClick={onToggle}>
      <span className={styles.icon} aria-hidden="true">{icon}</span>
      <span className={cx('text-body-default', styles.label)}>{name}</span>
      <span className={styles.ring} aria-hidden="true" />
    </button>
  );
}
