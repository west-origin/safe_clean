// (코드에서 정함) 펼침 상자 꺾쇠 — 아래를 향한 V. 펼치면 화면이 CSS 로 뒤집는다. 2026-11-03 사용자 첨부 날짜/시간 시안. 색은 currentColor.
// 쓰는 곳: ScheduleAccordion.

/**
 * @param {object} props
 * @param {number} [props.size=24] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function AccordionChevronIcon({ size = 24, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="m5 9 7 7 7-7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
