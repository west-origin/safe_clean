// (코드에서 정함) 좌우로 넘기는 한 달 달력 — 2026-11-03 사용자 요청: 날짜/시간 화면 '방문 일' 상자를 펼치면 나온다.
// 달마다 한 장(달 이름 + 요일 줄 + 날짜 칸)을 가로로 붙이고 CSS scroll-snap 으로 한 장씩 멈춘다 (손가락으로 좌우 스와이프).
// 고르는 기준·모양은 세로 달력(ScrollCalendar)과 같다: 끌 날은 isDisabled, 오늘은 '오늘', 고른 날은 브랜드 색 원, 일요일 빨강.
// 처음에는 고른 날이 든 달(없으면 첫 달)을 보여 준다. 높이는 지금 보이는 달에 맞춘다 (5주 달 아래에 6주 달만큼 빈칸이 생기지 않게).
import { useEffect, useRef, useState } from 'react';
import { toDateKey } from '../Calendar/dateKey.js';
import { cx } from '../../utils/cx.js';
import styles from './SwipeCalendar.module.css';

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
export default function SwipeCalendar({ months, value, onChange, isDisabled = () => false, todayKey, className }) {
  const track = useRef(null);
  const [height, setHeight] = useState(); // 지금 보이는 달의 높이 (px). 모르면 CSS 기본(가장 긴 달)
  // 지금 보이는 달(가로 위치 ÷ 폭)의 높이를 상자 높이로
  const fitHeight = () => {
    const el = track.current;
    if (!el || !el.clientWidth) return;
    const page = el.children[Math.round(el.scrollLeft / el.clientWidth)];
    if (page) setHeight(page.offsetHeight);
  };
  // 처음 그릴 때 고른 날이 든 달로 옮긴다 (부드럽게 넘기지 않고 바로)
  useEffect(() => {
    const el = track.current;
    if (!el) return;
    if (value) {
      const [y, m] = value.split('-').map(Number);
      const index = months.findIndex((mo) => mo.year === y && mo.month === m);
      if (index > 0) el.scrollLeft = index * el.clientWidth;
    }
    fitHeight();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 처음 한 번만 (고를 때마다 움직이면 넘기던 달이 튄다)
  }, []);

  return (
    <div ref={track} className={cx(styles.track, className)} style={height ? { height } : undefined} onScroll={fitHeight}>
      {months.map(({ year, month }) => {
        const first = new Date(year, month - 1, 1);
        const days = new Date(year, month, 0).getDate();
        return (
          <section key={`${year}-${month}`} className={styles.month} aria-label={`${year}년 ${month}월`}>
            <h3 className={cx('text-title-card', styles.monthTitle)}>{`${year}년 ${month}월`}</h3>
            <div className={styles.grid}>
              {WEEKDAYS.map((w, i) => (
                <span key={w} aria-hidden="true" className={cx('text-label-s', styles.weekday, i === 0 && styles.sunday)}>{w}</span>
              ))}
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
                    className={cx(today ? 'text-caption-l' : 'text-body-default', styles.day, day.getDay() === 0 && styles.sunday, selected && styles.selected)}
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
