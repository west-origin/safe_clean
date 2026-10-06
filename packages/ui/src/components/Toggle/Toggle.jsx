// Figma: Settings › MenuRow › Toggle/On (2048:1748) · Toggle/Off (2048:1752) — 44×26, radius 13, 손잡이 20 흰색. On = brand/primary, Off = border/default.
// 켜고 끄는 스위치. MenuRow 의 토글 행이 쓴다 (예: 고객 앱 설정 화면, 매니저 앱 알림 설정 화면).
import { cx } from '../../utils/cx.js';
import styles from './Toggle.module.css';

/**
 * @param {object} props
 * @param {boolean} props.checked Toggle/On
 * @param {(checked: boolean) => void} [props.onChange]
 * @param {string} props.label 접근성 라벨 (옆 메뉴 문구)
 * @param {boolean} [props.disabled]
 * @param {string} [props.className]
 * role="switch" + aria-checked 로 켜짐/꺼짐을 보조기기에 알린다. 누르면 onChange(반대값) 만 부르고,
 * 화면에 보이는 값은 부모가 checked 로 다시 내려줘야 바뀐다.
 */
export default function Toggle({ checked, onChange, label, disabled = false, className }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      className={cx(styles.toggle, checked && styles.on, className)}
      onClick={() => onChange && onChange(!checked)}
    >
      <span className={styles.knob} aria-hidden="true" />
    </button>
  );
}
