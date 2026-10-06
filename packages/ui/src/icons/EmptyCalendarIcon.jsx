// Figma: Manager/Schedule/Empty › Icon/Empty (2146:2963). 36px, stroke 2.7. 색은 currentColor.
// 모양: 달력. 쓰는 곳(예): EmptyState 에 icon="calendar" 를 줄 때 (빈 상태 안내 위 그림).

/**
 * @param {object} props
 * @param {number} [props.size=36]
 * @param {string} [props.className]
 */
export default function EmptyCalendarIcon({ size = 36, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 36 36" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M4.5 12H31.5M10.5 4.5V10.5M25.5 4.5V10.5M7.5 7.5H28.5C29.2956 7.5 30.0587 7.81607 30.6213 8.37868C31.1839 8.94129 31.5 9.70435 31.5 10.5V28.5C31.5 29.2956 31.1839 30.0587 30.6213 30.6213C30.0587 31.1839 29.2956 31.5 28.5 31.5H7.5C6.70435 31.5 5.94129 31.1839 5.37868 30.6213C4.81607 30.0587 4.5 29.2956 4.5 28.5V10.5C4.5 9.70435 4.81607 8.94129 5.37868 8.37868C5.94129 7.81607 6.70435 7.5 7.5 7.5Z" stroke="currentColor" strokeWidth="2.7" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
