// Figma: InfoIcon/Person (2098:2190). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 모양: 사람. 쓰는 곳(예): StackedInfoCard·BookingInfoCard 의 icon="person" 행.

/**
 * @param {object} props
 * @param {number} [props.size=20] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function InfoPersonIcon({ size = 20, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 20 20" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M9.9998 9.7999C11.7671 9.7999 13.1998 8.36721 13.1998 6.5999C13.1998 4.83259 11.7671 3.3999 9.9998 3.3999C8.23249 3.3999 6.7998 4.83259 6.7998 6.5999C6.7998 8.36721 8.23249 9.7999 9.9998 9.7999Z" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M3.7998 16.9998C3.7998 13.5998 6.5998 11.7998 9.9998 11.7998C13.3998 11.7998 16.1998 13.5998 16.1998 16.9998" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
