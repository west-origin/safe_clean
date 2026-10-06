// Figma: InfoIcon/Pin (2098:2186). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 모양: 위치 핀. 쓰는 곳(예): BookingInfoCard 의 icon="pin" 행, CallCard(size 18).

/**
 * @param {object} props
 * @param {number} [props.size=20] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function InfoPinIcon({ size = 20, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 20 20" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M10 17.6C10 17.6 15.6 12.9 15.6 9C15.6 8.2646 15.4552 7.5364 15.1737 6.85697C14.8923 6.17755 14.4798 5.56021 13.9598 5.0402C13.4398 4.52019 12.8225 4.1077 12.143 3.82627C11.4636 3.54485 10.7354 3.4 10 3.4C9.2646 3.4 8.5364 3.54485 7.85697 3.82627C7.17755 4.1077 6.56021 4.52019 6.0402 5.0402C5.52019 5.56021 5.1077 6.17755 4.82627 6.85697C4.54485 7.5364 4.4 8.2646 4.4 9C4.4 12.9 10 17.6 10 17.6Z" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round" />
      <path d="M10 11.2C11.215 11.2 12.2 10.215 12.2 9C12.2 7.78497 11.215 6.8 10 6.8C8.78497 6.8 7.8 7.78497 7.8 9C7.8 10.215 8.78497 11.2 10 11.2Z" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
