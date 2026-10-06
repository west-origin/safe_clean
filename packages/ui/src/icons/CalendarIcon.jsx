// Figma: Booking/Frequency › DateTimeField › Icon/Calendar (2075:1819). 29×29, stroke 1.8. path 원본 그대로, 색은 currentColor.
// 모양: 달력. 쓰는 곳(예): SelectField 에 icon="calendar" 를 주면 기본 꺾쇠(ChevronRightTinyIcon) 대신 이것을 그린다.

/**
 * @param {object} props
 * @param {number} [props.size=29]
 * @param {string} [props.className]
 */
export default function CalendarIcon({ size = 29, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 29 29" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M21.5 6H7.5C5.01472 6 3 8.01472 3 10.5V21.5C3 23.9853 5.01472 26 7.5 26H21.5C23.9853 26 26 23.9853 26 21.5V10.5C26 8.01472 23.9853 6 21.5 6Z" stroke="currentColor" strokeWidth={1.8} />
      <path d="M3 12.2002H26" stroke="currentColor" strokeWidth={1.8} />
      <path d="M9.5 3.2002V7.4002M19.5 3.2002V7.4002" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" />
    </svg>
  );
}
