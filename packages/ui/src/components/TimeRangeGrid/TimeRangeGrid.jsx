// (코드에서 정함) 청소 시작·종료 시간 고르기 — 2026-11-03 사용자 첨부 시안의 '시간 선택' 알약 칸(한 줄 3칸).
// 첫 번째로 누른 시간이 시작, 그 뒤 시간을 누르면 종료. 시작·종료 칸은 브랜드 색으로 채우고 그 사이 칸은 옅은 브랜드 색.
// 종료까지 고른 뒤 다시 누르거나, 시작보다 앞(같은) 시간을 누르면 그 시간이 새 시작이 된다.
// 시작을 고른 동안 종료 후보는 minHours~maxHours 안의 시간만 켜진다. 시작으로 못 고르는 시간(지난 시간·막힌 시간)은 isStartDisabled 로 끈다.
// 쓰는 곳: 고객 앱 BookingSchedule '방문 시간' 상자.
import { cx } from '../../utils/cx.js';
import { minutesOf, hoursBetween } from './timeRange.js';
import styles from './TimeRangeGrid.module.css';

/**
 * @param {object} props
 * @param {string[]} props.times 고를 수 있는 시각 목록 ('HH:MM', 이른 순)
 * @param {string} [props.start] 시작
 * @param {string} [props.end] 종료
 * @param {(range: { start: string, end: string | null }) => void} props.onChange
 * @param {(time: string) => boolean} [props.isStartDisabled] 시작으로 고를 수 없는 시각
 * @param {number} [props.minHours=1] 최소 청소 시간
 * @param {number} [props.maxHours=24] 최대 청소 시간
 * @param {string} [props.className]
 */
export default function TimeRangeGrid({ times, start, end, onChange, isStartDisabled = () => false, minHours = 1, maxHours = 24, className }) {
  const choosingEnd = Boolean(start) && !end; // 시작만 고른 상태 → 시작 뒤 칸은 종료 후보
  const isEndCandidate = (t) => choosingEnd && minutesOf(t) > minutesOf(start);
  const endOk = (t) => {
    const h = hoursBetween(start, t);
    return h >= minHours && h <= maxHours;
  };
  const pick = (t) => {
    if (isEndCandidate(t)) onChange({ start, end: t });
    else onChange({ start: t, end: null });
  };

  return (
    <div className={cx(styles.grid, className)}>
      {times.map((t) => {
        const disabled = isEndCandidate(t) ? !endOk(t) : isStartDisabled(t);
        const edge = t === start || t === end;
        const inside = start && end && minutesOf(t) > minutesOf(start) && minutesOf(t) < minutesOf(end);
        const role = t === start ? ' 시작' : t === end ? ' 종료' : '';
        return (
          <button
            key={t}
            type="button"
            className={cx('text-body-default', styles.chip, edge && styles.edge, inside && styles.inside)}
            disabled={disabled}
            aria-pressed={Boolean(edge || inside)}
            aria-label={`${t}${role}`}
            onClick={() => pick(t)}
          >
            {t}
          </button>
        );
      })}
    </div>
  );
}
