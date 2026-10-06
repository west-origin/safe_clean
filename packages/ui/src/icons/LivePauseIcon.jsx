// Figma: Live/CircleControl › Icon/Pause (2160:2208). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 모양: 세로 막대 두 개. 쓰는 곳(예): LiveCircleControl 의 type="pause"("일시정지").
// 녹화본이 멈춰 있으면(status 'paused') 화면이 대신 type="play"(LivePlayIcon)를 넘긴다.

/**
 * @param {object} props
 * @param {number} [props.size=35] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function LivePauseIcon({ size = 35, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 35 35" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M11.6667 7.29163H16.3333V27.7083H11.6667V7.29163ZM18.6667 7.29163H23.3333V27.7083H18.6667V7.29163Z" fill="currentColor" />
    </svg>
  );
}
