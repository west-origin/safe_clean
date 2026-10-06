// Figma(수리 예약): 달력(1:370) — 한 주만 보이는 띠: [‹ "2026년 9월" ›](Title/Card) → 요일 월~일(Label/S secondary, 40 폭) → 날짜 7칸(40×40, radius 12 원본값, Label/Default; Selected = brand/primary 배경 + 흰 글자)
// Figma 의 화살표 이름은 "이전 달/다음 달" 이지만 한 주 띠에서 달 단위로 건너뛰면 그 사이 날짜를 고를 수 없으므로 한 주씩 움직인다 (코드에서 정함, PROGRESS.md 판단 항목). 제목은 보이는 주의 월요일이 속한 달
// 고객 앱 수리 예약의 날짜 화면(RepairDateTime)이 쓴다. 고를 수 없는 날은 isDisabled 로 받는다 (그 화면은 notBookableWithin 결과를 넘긴다).
import { useState } from 'react';
import MonthChevronIcon from '../../icons/MonthChevronIcon.jsx';
import { toDateKey, fromDateKey, startOfToday } from '../Calendar/dateKey.js';
import { cx } from '../../utils/cx.js';
import styles from './WeekCalendar.module.css';

const WEEKDAYS = ['월', '화', '수', '목', '금', '토', '일']; // 요일 머리글 (월요일부터)

/**
 * 날짜 d 의 n 일 뒤(음수면 앞) 로컬 자정. 7칸 만들기와 주 이동(‹ ›)에 쓴다.
 * 밀리초(24시간)를 더하지 않고 달력의 '일' 을 더하는 이유: 서머타임이 있는 시간대에서는 하루가 23·25시간인 날이 있어
 * 24시간씩 더하면 자정이 전날 23시로 밀리고, 그 뒤로 칸마다 하루 앞 날짜가 들어간다(월 칸에 일요일). Calendar 의 buildGrid 와 같은 방식이다
 */
const addDays = (d, n) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);

/** 그 날짜가 속한 주의 월요일 */
function mondayOf(d) {
  // 시·분을 버린 로컬 자정으로 복사한다 (넘겨받은 Date 는 건드리지 않는다)
  const m = new Date(d.getFullYear(), d.getMonth(), d.getDate());
  // getDay() 는 일=0 … 토=6. (getDay() + 6) % 7 로 월=0 … 일=6 으로 바꾼 값만큼 날짜를 빼면 그 주 월요일이다
  m.setDate(m.getDate() - ((m.getDay() + 6) % 7));
  return m;
}

// 기본 판정: 오늘(서울 날짜 기준 startOfToday)보다 앞선 날이거나 토·일이면 고를 수 없다. 오늘은 고를 수 있다.
// 주의: isDisabled 를 넘기지 않을 때만 쓴다. 예약 화면이 넘기는 notBookable(dateKey.js)은 오늘도 막는다.
const defaultDisabled = (d) => d < startOfToday() || d.getDay() === 0 || d.getDay() === 6;

/**
 * @param {object} props
 * @param {string} [props.value] 선택한 날짜 'YYYY-MM-DD'
 * @param {(dateKey: string) => void} [props.onChange]
 * @param {(date: Date) => boolean} [props.isDisabled] 기본: 오늘 이전 또는 토·일
 * @param {Date} [props.initialDate] 처음 보여줄 주 — 기본: value, 없으면 오늘
 * @param {string} [props.className]
 * 한 주(월~일) 7칸을 radio 버튼으로 그린다. 날짜를 누르면 onChange('YYYY-MM-DD') 를 부르고, 고른 값은 부모가 value 로 내려준다.
 * ‹ › 는 이 컴포넌트 안의 상태(보이는 주의 월요일)만 바꾼다.
 */
export default function WeekCalendar({ value, onChange, isDisabled = defaultDisabled, initialDate, className }) {
  // 처음 보여줄 주: initialDate → value 의 날짜 → 오늘(기기 날짜) 순. useState 에 함수를 넘겨 첫 렌더에서 한 번만 계산한다
  //   (그래서 나중에 value 가 바뀌어도 보이는 주가 저절로 따라가지는 않는다)
  const [monday, setMonday] = useState(() => mondayOf(initialDate ?? fromDateKey(value) ?? new Date()));
  const days = Array.from({ length: 7 }, (_, i) => addDays(monday, i)); // 월요일 + 0~6일 (달력 날짜로 더해 서머타임에도 밀리지 않는다)
  const label = `${monday.getFullYear()}년 ${monday.getMonth() + 1}월`; // 제목이자 날짜 묶음(radiogroup)의 이름
  // 제목 span 은 aria-live 라, 주를 옮겨 달이 바뀌면 새 제목을 보조기기가 읽어 준다.
  // 요일 머리글은 aria-hidden 이라 보조기기에는 읽히지 않고, 날짜 버튼은 aria-label 로 "9월 21일" 처럼 읽힌다
  return (
    <section className={cx(styles.calendar, className)} aria-label="방문 날짜 선택">
      <div className={styles.nav}>
        <button type="button" className={styles.navButton} onClick={() => setMonday(addDays(monday, -7))} aria-label="이전 주">
          <MonthChevronIcon direction="prev" />
        </button>
        <span className={cx('text-title-card', styles.month)} aria-live="polite">{label}</span>
        <button type="button" className={styles.navButton} onClick={() => setMonday(addDays(monday, 7))} aria-label="다음 주">
          <MonthChevronIcon direction="next" />
        </button>
      </div>
      <div className={styles.weekdays} aria-hidden="true">
        {WEEKDAYS.map((d) => (
          <span key={d} className={cx('text-label-s', styles.weekday)}>{d}</span>
        ))}
      </div>
      <div className={styles.days} role="radiogroup" aria-label={label}>
        {days.map((d) => {
          const key = toDateKey(d);
          const disabled = isDisabled(d);
          const selected = key === value;
          return (
            <button key={key} type="button" role="radio" aria-checked={selected} aria-label={`${d.getMonth() + 1}월 ${d.getDate()}일`} disabled={disabled}
              className={cx('text-label-default', styles.day, selected && styles.selected, disabled && styles.disabled)} onClick={() => onChange && onChange(key)}>
              {d.getDate()}
            </button>
          );
        })}
      </div>
    </section>
  );
}
