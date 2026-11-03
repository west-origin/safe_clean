// 주의사항 화면의 카드형 선택지 묶음 — Figma 에 없음 (코드에서 정함).
// 2026-11-01 동그라미 시안(CircleChoiceGroup)을 2026-11-03 사용자 첨부 시안(흰 카드 안 아이콘 + 이름, 고른 카드는 브랜드 색 테두리)으로 바꿨다.
// 선택지 수만큼 한 줄에 같은 폭으로 놓는다. 하나만 고르는 라디오(role="radiogroup"). 아이콘 없이 이름만 있는 선택지도 된다(공동현관 비밀번호 있음/없음).
// 아무것도 안 고른 상태(value 없음)도 있다 — 화면이 '선택해 주세요' 를 따로 알린다.
import { cx } from '../../utils/cx.js';
import styles from './ChoiceCardGroup.module.css';

/**
 * @param {object} props
 * @param {string} props.label 묶음 이름 (보조기기용, 예: '반려동물')
 * @param {{ key: string, label: string, icon?: import('react').ReactNode }[]} props.options
 * @param {string} [props.value] 고른 key
 * @param {(key: string) => void} props.onChange
 * @param {string} [props.className]
 */
export default function ChoiceCardGroup({ label, options, value, onChange, className }) {
  return (
    <div className={cx(styles.group, className)} role="radiogroup" aria-label={label}>
      {options.map((o) => {
        const selected = o.key === value;
        return (
          <button
            key={o.key}
            type="button"
            role="radio"
            aria-checked={selected}
            data-state={selected ? 'selected' : 'default'}
            className={cx(styles.card, selected && styles.selected)}
            onClick={() => onChange(o.key)}
          >
            {o.icon && <span className={styles.icon} aria-hidden="true">{o.icon}</span>}
            <span className="text-body-m">{o.label}</span>
          </button>
        );
      })}
    </div>
  );
}
