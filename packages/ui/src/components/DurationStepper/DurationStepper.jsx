// 2026-10-31 사용자 첨부 서비스 시간 시트 시안 — Figma 에 없음 (코드에서 정함).
// [−] 'N시간 00분' / 금액 [+], 아래 회색 안내 한 줄. 한 번 누를 때 step 시간씩, min~max 밖이면 버튼이 꺼진다.
import MinusIcon from '../../icons/MinusIcon.jsx';
import PlusIcon from '../../icons/PlusIcon.jsx';
import { cx } from '../../utils/cx.js';
import styles from './DurationStepper.module.css';

/**
 * @param {object} props
 * @param {number} props.hours 지금 시간
 * @param {number} props.min 최소 시간 (− 가 꺼진다)
 * @param {number} props.max 최대 시간 (+ 가 꺼진다)
 * @param {number} [props.step=1] 한 번에 바뀌는 시간
 * @param {(hours: number) => void} props.onChange
 * @param {string} [props.priceLabel] 시간 아래 금액 (예: '41,600원')
 * @param {string} [props.note] 맨 아래 회색 안내 (예: '표준시간은 2시간 입니다.')
 * @param {string} [props.className]
 */
export default function DurationStepper({ hours, min, max, step = 1, onChange, priceLabel, note, className }) {
  return (
    <div className={cx(styles.stepper, className)}>
      <div className={styles.row}>
        <button type="button" className={styles.round} disabled={hours <= min} onClick={() => onChange(Math.max(min, hours - step))} aria-label="시간 줄이기">
          <MinusIcon />
        </button>
        <div className={styles.center} aria-live="polite">
          <span className={cx('text-heading-m', styles.hours)}>{`${hours}시간 00분`}</span>
          {priceLabel && <span className={cx('text-body-l', styles.price)}>{priceLabel}</span>}
        </div>
        <button type="button" className={styles.round} disabled={hours >= max} onClick={() => onChange(Math.min(max, hours + step))} aria-label="시간 늘리기">
          <PlusIcon />
        </button>
      </div>
      {note && <p className={cx('text-body-s', styles.note)}>{note}</p>}
    </div>
  );
}
