// (코드에서 정함) 날짜/시간 화면의 펼침 상자 — 2026-11-03 사용자 첨부 시안: 흰 카드, 머리줄(아이콘 + 글자 + 꺾쇠), 누르면 아래 내용이 펼쳐진다.
// 열림 여부는 화면이 들고 있다(open/onToggle) — 날짜를 고르면 날짜 상자를 닫고 시간 상자를 여는 식으로 화면이 이어 준다.
// 쓰는 곳: 고객 앱 BookingSchedule 의 '방문 일'(icon="calendar") · '방문 시간'(icon="clock").
import { useId } from 'react';
import CalendarIcon from '../../icons/CalendarIcon.jsx';
import InfoClockIcon from '../../icons/InfoClockIcon.jsx';
import AccordionChevronIcon from '../../icons/AccordionChevronIcon.jsx';
import { cx } from '../../utils/cx.js';
import styles from './ScheduleAccordion.module.css';

const ICONS = { calendar: <CalendarIcon size={24} />, clock: <InfoClockIcon size={24} /> };

/**
 * @param {object} props
 * @param {'calendar'|'clock'} props.icon 머리줄 왼쪽 아이콘
 * @param {string} props.label 머리줄 글자 (예: '방문 일', 고른 뒤엔 '23일(금)')
 * @param {boolean} props.open 펼쳐졌는지
 * @param {() => void} props.onToggle 머리줄을 누를 때
 * @param {import('react').ReactNode} props.children 펼쳐질 내용
 * @param {string} [props.className]
 */
export default function ScheduleAccordion({ icon, label, open, onToggle, children, className }) {
  const panelId = `accordion-${useId()}`;
  return (
    <section className={cx(styles.box, className)}>
      <button type="button" className={styles.header} aria-expanded={open} aria-controls={panelId} onClick={onToggle}>
        <span className={styles.icon}>{ICONS[icon]}</span>
        <span className={cx('text-title-card', styles.label)}>{label}</span>
        <AccordionChevronIcon className={cx(styles.chevron, open && styles.chevronOpen)} />
      </button>
      {open && (
        <div id={panelId} className={styles.panel}>
          {children}
        </div>
      )}
    </section>
  );
}
