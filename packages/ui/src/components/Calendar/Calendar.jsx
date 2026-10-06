// Figma: Booking/DateTime › CalendarPanel (2058:1743) — 393×350, 패딩 27/22/5/14.
//   TitleWrap(좌 10) "방문 날짜 선택" Heading/S → MonthNav(상 38, 간격 44, 가운데: ‹ 2025년 9월 ›) → WeekdayRow(상 21, 51×16 Label/S) → DateGrid(상 10, 행 39, 셀 51×39 Label/Default)
//   DateCell/Selected = brand/primary 배경 radius 12 + 흰 글자. 다른 달 날짜와 토·일은 border/default 색(선택 불가로 봄 — PROGRESS.md 판단 항목)
//   오늘 이전 날짜도 선택 불가 (예약은 미래만 — 코드에서 정함). 규칙을 바꾸려면 isDisabled 를 넘긴다
//
// 예: 고객 앱 예약 일시 선택(BookingDateTime — notBookableWithin 으로 예약 가능 기간까지 막는다),
//   매니저 앱 일정(Schedule 의 '달력' 보기 — isDisabled 로 모든 날짜를 열어 둔다).
// 날짜는 'YYYY-MM-DD' 문자열(dateKey)로 주고받고, 칸은 로컬 자정 Date 로 그린다.
import { useState } from 'react';
import MonthChevronIcon from '../../icons/MonthChevronIcon.jsx';
import { toDateKey, fromDateKey, startOfToday } from './dateKey.js';
import { cx } from '../../utils/cx.js';
import styles from './Calendar.module.css';

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']; // 요일 머리줄. buildGrid 와 같은 일요일 시작

/** 달력에 그릴 날짜 배열 (일요일 시작, 앞뒤 달 채움, 6주 이내) */
function buildGrid(year, month) {
  const first = new Date(year, month, 1);
  const start = new Date(year, month, 1 - first.getDay()); // 1일이 든 주의 일요일 (앞 달로 넘어가면 Date 가 알아서 맞춘다)
  const last = new Date(year, month + 1, 0); // 다음 달 0일 = 이번 달 마지막 날
  const rows = Math.ceil((first.getDay() + last.getDate()) / 7); // 앞 빈칸 + 이번 달 날수 → 4~6주
  // 줄(주)마다 7칸. 날짜는 start 에서 하루씩 더해 만들고, 월말을 넘으면 Date 가 다음 달로 넘겨 준다
  return Array.from({ length: rows }, (_, r) => Array.from({ length: 7 }, (_, c) => new Date(start.getFullYear(), start.getMonth(), start.getDate() + r * 7 + c)));
}

/**
 * @param {object} props
 * @param {string} [props.value] 선택한 날짜 'YYYY-MM-DD'
 * @param {(dateKey: string) => void} [props.onChange]
 * @param {string} [props.title='방문 날짜 선택'] (Figma 문구)
 * @param {(date: Date) => boolean} [props.isDisabled] 선택 불가 판정. 기본: 오늘 이전 또는 토·일
 * @param {Date} [props.initialMonth] 처음 보여줄 달. 기본: value 의 달, 없으면 오늘
 * @param {string} [props.className]
 * 주의: 이 블록은 아래 Calendar 의 props 설명이다. 사이에 defaultDisabled 선언이 있어 에디터 힌트는
 * Calendar 가 아니라 defaultDisabled 에 붙는다 (옮기려면 코드 순서를 바꿔야 한다).
 */
const defaultDisabled = (d) => d < startOfToday() || d.getDay() === 0 || d.getDay() === 6; // 기본 규칙: 지난 날짜·토·일. 오늘은 열린다 (dateKey.js 의 notBookable 은 오늘도 막는다)

/**
 * 한 달 달력 패널 (props 는 위 JSDoc 블록 참고). 날짜 칸을 누르면 onChange('YYYY-MM-DD') 를 부른다.
 * 이전·다음 달 버튼은 보이는 달만 바꾸고 선택값(value)은 그대로 둔다. 선택값은 부모가 들고 있다.
 */
export default function Calendar({ value, onChange, title = '방문 날짜 선택', isDisabled = defaultDisabled, initialMonth, className }) {
  // 처음 보여 줄 달. useState 초기값이라 첫 렌더에서만 쓰인다 — 나중에 value 가 바뀌어도 보이는 달은 따라가지 않는다.
  // initialMonth·value 가 둘 다 없으면 기기 날짜(new Date())의 달이다. 막는 날의 '오늘'(startOfToday)은 서울 날짜라, 시간대가 다른 기기에서는
  // 월말·월초에 처음 보이는 달이 서울의 이번 달과 다를 수 있다 (WeekCalendar 의 처음 주도 같다)
  const base = initialMonth ?? fromDateKey(value) ?? new Date();
  const [view, setView] = useState({ year: base.getFullYear(), month: base.getMonth() });
  const rows = buildGrid(view.year, view.month);
  // 달 이동. month 는 0~11 이라 12 를 넘거나 0 밑으로 가면 연도를 올리고 내린다.
  // JS 의 % 는 음수에서 음수를 돌려주므로 (x % 12 + 12) % 12 로 다시 0~11 로 맞춘다.
  const moveMonth = (delta) => setView((v) => ({ year: v.year + Math.floor((v.month + delta) / 12), month: (((v.month + delta) % 12) + 12) % 12 }));

  // aria-live="polite": 달이 바뀌면 화면 낭독기가 새 'YYYY년 M월' 을 읽어 준다.
  // 날짜 칸은 role=grid/row/gridcell 표 구조로, 선택된 칸은 aria-selected 로 알린다.
  return (
    <section className={cx(styles.panel, className)} aria-label={title}>
      <h2 className={cx('text-heading-s', styles.title)}>{title}</h2>
      <div className={styles.monthNav}>
        <button type="button" className={styles.navButton} onClick={() => moveMonth(-1)} aria-label="이전 달">
          <MonthChevronIcon direction="prev" />
        </button>
        <span className={cx('text-title-card', styles.month)} aria-live="polite">
          {view.year}년 {view.month + 1}월
        </span>
        <button type="button" className={styles.navButton} onClick={() => moveMonth(1)} aria-label="다음 달">
          <MonthChevronIcon direction="next" />
        </button>
      </div>
      <div className={styles.weekdays} aria-hidden="true">
        {WEEKDAYS.map((w) => (
          <span key={w} className={cx('text-label-s', styles.weekday)}>
            {w}
          </span>
        ))}
      </div>
      <div className={styles.grid} role="grid" aria-label={`${view.year}년 ${view.month + 1}월`}>
        {rows.map((row, r) => (
          <div key={r} className={styles.row} role="row">
            {row.map((d) => {
              const key = toDateKey(d);
              const outside = d.getMonth() !== view.month; // 앞뒤 달에서 채운 칸
              const disabled = outside || isDisabled(d); // 앞뒤 달 칸은 규칙과 상관없이 누를 수 없다
              const selected = key === value && !outside; // 앞뒤 달 칸은 같은 날짜여도 선택 표시를 하지 않는다
              return (
                <button
                  key={key}
                  type="button"
                  role="gridcell"
                  aria-selected={selected}
                  aria-label={`${d.getMonth() + 1}월 ${d.getDate()}일`}
                  disabled={disabled}
                  className={cx('text-label-default', styles.cell, selected && styles.selected, disabled && styles.disabled)}
                  onClick={() => onChange && onChange(key)}
                >
                  {d.getDate()}
                </button>
              );
            })}
          </div>
        ))}
      </div>
    </section>
  );
}
