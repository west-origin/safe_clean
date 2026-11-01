// 2026-10-31 사용자 첨부 '방문일을 선택해주세요.' 시안 — Figma 에 없음 (코드에서 정함).
// 위에 고정된 요일 줄(일요일 빨강) + 달을 위아래로 이어 붙인 달력. 지난 날·고를 수 없는 날은 연한 회색으로 꺼지고,
// 오늘은 숫자 대신 '오늘'. 고른 날은 브랜드 색 원. 몇 달을 그릴지(months)와 끌 날(isDisabled)은 화면이 정한다.
import { toDateKey } from '../Calendar/dateKey.js';
import { cx } from '../../utils/cx.js';
import styles from './ScrollCalendar.module.css';

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

/**
 * @param {object} props
 * @param {{ year: number, month: number }[]} props.months 그릴 달 목록 (month 는 1~12)
 * @param {string} [props.value] 고른 날 'YYYY-MM-DD'
 * @param {(key: string) => void} props.onChange
 * @param {(day: Date) => boolean} [props.isDisabled] 고를 수 없는 날
 * @param {string} [props.todayKey] 오늘 'YYYY-MM-DD' — 그 칸은 '오늘' 로 쓴다
 * @param {string} [props.className]
 */
export default function ScrollCalendar({ months, value, onChange, isDisabled = () => false, todayKey, className }) {
  return (
    <div className={cx(styles.calendar, className)}>
      <div className={styles.weekdays} aria-hidden="true">
        {WEEKDAYS.map((w, i) => (
          <span key={w} className={cx('text-body-m', styles.weekday, i === 0 && styles.sunday)}>{w}</span>
        ))}
      </div>
      {months.map(({ year, month }) => {
        const first = new Date(year, month - 1, 1);
        const days = new Date(year, month, 0).getDate();
        const label = `${year}.${month}`;
        return (
          <section key={label} className={styles.month} aria-label={`${year}년 ${month}월`}>
            <h2 className={cx('text-heading-m', styles.monthTitle)}>{label}</h2>
            <div className={styles.grid}>
              {/* 1일 앞의 빈칸 (그 달 1일의 요일만큼) */}
              {Array.from({ length: first.getDay() }, (_, i) => <span key={`blank-${i}`} />)}
              {Array.from({ length: days }, (_, i) => {
                const day = new Date(year, month - 1, i + 1);
                const key = toDateKey(day);
                const disabled = isDisabled(day);
                const selected = key === value && !disabled;
                const today = key === todayKey;
                return (
                  <button
                    key={key}
                    type="button"
                    className={cx(today ? 'text-body-s' : 'text-body-l', styles.day, day.getDay() === 0 && styles.sunday, selected && styles.selected)}
                    disabled={disabled}
                    aria-pressed={selected}
                    aria-label={`${month}월 ${i + 1}일${today ? ' 오늘' : ''}`}
                    onClick={() => onChange(key)}
                  >
                    {today ? '오늘' : i + 1}
                  </button>
                );
              })}
            </div>
          </section>
        );
      })}
    </div>
  );
}
