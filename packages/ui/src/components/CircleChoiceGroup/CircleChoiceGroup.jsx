// 2026-11-01 사용자 첨부 '매니저님 방문 시 참고해야 할 정보' 시안의 동그라미 선택지 묶음 — Figma 에 없음 (코드에서 정함).
// 회색 원 안 선 아이콘 + 아래 이름, 한 줄에 4개. 하나만 고르는 라디오(role="radiogroup")이고, 고른 원은 브랜드 색 + 흰 아이콘.
// 아무것도 안 고른 상태(value 없음)도 있다 — 화면이 '선택해 주세요' 를 따로 알린다.
import { cx } from '../../utils/cx.js';
import styles from './CircleChoiceGroup.module.css';

/**
 * @param {object} props
 * @param {string} props.label 묶음 이름 (보조기기용, 예: '반려동물')
 * @param {{ key: string, label: string, icon: import('react').ReactNode }[]} props.options
 * @param {string} [props.value] 고른 key
 * @param {(key: string) => void} props.onChange
 * @param {string} [props.className]
 */
export default function CircleChoiceGroup({ label, options, value, onChange, className }) {
  return (
    <div className={cx(styles.group, className)} role="radiogroup" aria-label={label}>
      {options.map((o) => {
        const selected = o.key === value;
        return (
          <button key={o.key} type="button" role="radio" aria-checked={selected} className={cx(styles.choice, selected && styles.selected)} onClick={() => onChange(o.key)}>
            <span className={styles.circle} aria-hidden="true">{o.icon}</span>
            <span className={cx('text-body-default', styles.label)}>{o.label}</span>
          </button>
        );
      })}
    </div>
  );
}
