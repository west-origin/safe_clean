// Figma: Live/PillControl › Icon/Exit (2160:2224). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 모양: X 표. 쓰는 곳(예): LivePillControl 의 type="exit"("종료하기", 영상 위 알약 버튼). 하단바형은 LiveHangUpIcon.

/**
 * @param {object} props
 * @param {number} [props.size=18] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function LiveExitIcon({ size = 18, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 18 18" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M4.5 4.5L13.5 13.5M13.5 4.5L4.5 13.5" stroke="currentColor" strokeWidth="1.65" strokeLinecap="round" />
    </svg>
  );
}
