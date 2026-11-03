// (코드에서 정함) 청소 시작·종료 시간 고르기 — 2026-11-03 사용자 첨부 시안의 '시간 선택'(한 줄 3칸).
// 같은 날 사용자 요청으로 달력 기간 선택처럼 바꿨다: 칸마다 테두리 없이 글자만, 시작·종료 칸만 파란 상자('다음' 버튼과 같은 둥글기),
// 시작부터 종료까지는 옅은 파란 띠로 이어진다 (칸 사이 틈 없이, 곧은 선). 둥근 곳은 시작 상자 왼쪽·종료 상자 오른쪽뿐.
// 띠는 칸을 감싼 <div>(cell)가, 파란 상자는 안의 <button> 이 그린다.
// 첫 번째로 누른 시간이 시작, 그 뒤 시간을 누르면 종료.
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
        const ranged = Boolean(start && end); // 종료까지 골랐으면 시작·종료 상자가 띠와 이어진다
        return (
          <div key={t} className={cx(styles.cell, inside && styles.bandFull)}>
            <button
              type="button"
              className={cx('text-body-m', styles.chip, edge && styles.edge, inside && styles.inside, ranged && t === start && styles.rangeStart, ranged && t === end && styles.rangeEnd)}
              disabled={disabled}
              aria-pressed={Boolean(edge || inside)}
              aria-label={`${t}${role}`}
              onClick={() => pick(t)}
            >
              {t}
            </button>
          </div>
        );
      })}
    </div>
  );
}
