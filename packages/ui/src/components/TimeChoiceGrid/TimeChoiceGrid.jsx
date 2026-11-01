// 2026-10-31 사용자 첨부 '방문 시간을 선택해 주세요.' 시안 — Figma 에 없음 (코드에서 정함).
// 왼쪽에 묶음 이름(오전/오후, 회색), 오른쪽에 시간 두 칸씩. 시간은 앞의 0 을 떼고('08:00' → '8:00') 크게 굵게.
// 고른 시간은 브랜드 색 글자 + 연한 브랜드 배경, 고를 수 없는 시간은 회색으로 꺼진다.
import { cx } from '../../utils/cx.js';
import styles from './TimeChoiceGrid.module.css';

/** '08:00' → '8:00' */
const display = (t) => t.replace(/^0(\d)/, '$1');

/**
 * @param {object} props
 * @param {{ label: string, times: string[] }[]} props.groups 묶음 (예: [{ label: '오전', times: ['09:00'] }])
 * @param {string} [props.value] 고른 시간 'HH:mm'
 * @param {(time: string) => void} props.onChange
 * @param {(time: string) => boolean} [props.isDisabled]
 * @param {string} [props.className]
 */
export default function TimeChoiceGrid({ groups, value, onChange, isDisabled = () => false, className }) {
  return (
    <div className={cx(styles.groups, className)}>
      {groups
        .filter((g) => g.times.length > 0)
        .map((g) => (
          <div key={g.label} className={styles.group}>
            <h2 className={cx('text-body-default', styles.label)}>{g.label}</h2>
            <div className={styles.grid} role="radiogroup" aria-label={`${g.label} 시간`}>
              {g.times.map((t) => {
                const disabled = isDisabled(t);
                const selected = t === value && !disabled;
                return (
                  <button key={t} type="button" role="radio" aria-checked={selected} disabled={disabled} className={cx('text-heading-m', styles.time, selected && styles.selected)} onClick={() => onChange(t)}>
                    {display(t)}
                  </button>
                );
              })}
            </div>
          </div>
        ))}
    </div>
  );
}
