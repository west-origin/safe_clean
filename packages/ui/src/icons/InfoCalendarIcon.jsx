// Figma: InfoIcon/Calendar (2098:2178). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 모양: 달력. 쓰는 곳(예): BookingInfoCard 의 icon="calendar" 행, 고객 앱 수리 예약 RepairConfirm 의 일정 행.

/**
 * @param {object} props
 * @param {number} [props.size=20] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function InfoCalendarIcon({ size = 20, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 20 20" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M14.4 4.6001H5.6C4.16406 4.6001 3 5.76416 3 7.2001V14.4001C3 15.836 4.16406 17.0001 5.6 17.0001H14.4C15.8359 17.0001 17 15.836 17 14.4001V7.2001C17 5.76416 15.8359 4.6001 14.4 4.6001Z" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M3 8.6001H17M6.8 2.6001V6.0001M13.2 2.6001V6.0001" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
