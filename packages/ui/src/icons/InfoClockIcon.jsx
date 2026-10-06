// Figma: InfoIcon/Clock (2098:2182). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 모양: 시계. 쓰는 곳(예): BookingInfoCard 의 icon="clock" 행, CallCard(size 18).

/**
 * @param {object} props
 * @param {number} [props.size=20] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function InfoClockIcon({ size = 20, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 20 20" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M10 17C13.866 17 17 13.866 17 10C17 6.13401 13.866 3 10 3C6.13401 3 3 6.13401 3 10C3 13.866 6.13401 17 10 17Z" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round" />
      <path d="M10 5.8V10L12.8 11.8" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
