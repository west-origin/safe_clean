// 재생 (Figma 에 없음 — 코드에서 정함: 녹화본이 멈춰 있을 때 Live/CircleControl 의 Pause 자리에 들어간다). Pause 아이콘과 같은 35 캔버스, currentColor.
// 쓰는 곳(예): LiveCircleControl 의 type="play". 고객 앱 라이브 화면은 녹화본(mode 'replay')일 때만 이 컨트롤을 그리고,
// 멈춰 있으면(status 'paused') pause 대신 play 를 넘긴다.

/**
 * @param {object} props
 * @param {number} [props.size=35] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function LivePlayIcon({ size = 35, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 35 35" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M12.5 7.29163L26.25 17.5L12.5 27.7083V7.29163Z" fill="currentColor" />
    </svg>
  );
}
