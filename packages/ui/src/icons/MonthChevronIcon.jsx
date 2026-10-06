// Figma: Booking/DateTime › MonthNav › Button/PrevMonth (2101:2210). 7×12, stroke 1.8. NextMonth 는 좌우 반전. 색은 currentColor.
// 쓰는 곳(예): Calendar·WeekCalendar 의 이전/다음 버튼. 크기는 7×12 고정이라 size prop 이 없다.
// direction="next" 이면 같은 path 를 inline style 의 scaleX(-1) 로 좌우만 뒤집는다 (왼쪽 꺾쇠 → 오른쪽 꺾쇠).

/**
 * @param {object} props
 * @param {'prev'|'next'} [props.direction='prev']
 * @param {string} [props.className]
 */
export default function MonthChevronIcon({ direction = 'prev', className }) {
  return (
    <svg width={7} height={12} viewBox="0 0 7 12" fill="none" aria-hidden="true" focusable="false" className={className} style={direction === 'next' ? { transform: 'scaleX(-1)' } : undefined}>
      <path d="M6 1L1 6L6 11" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
