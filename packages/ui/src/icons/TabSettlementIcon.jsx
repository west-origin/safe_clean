// Figma: Manager/TabBar › Icon/Settlement (2109:2221). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 모양: 영수증(줄 세 개 + 아래 톱니 모양 끝). 쓰는 곳(예): 매니저 앱 ManagerTabBar 의 "정산" 탭 (ManagerTabBar/tabs.js 표).
// ManagerTabBar 가 활성 탭이면 strokeWidth 2, 아니면 1.5 를 넘긴다.

/**
 * @param {object} props
 * @param {number} [props.size=24] 한 변 크기(px)
 * @param {number} [props.strokeWidth=2] 활성 2 / 비활성 1.5 (Figma 탭바 기준)
 * @param {string} [props.className]
 */
export default function TabSettlementIcon({ size = 24, strokeWidth = 2, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M8 8H16M8 12H16M8 16H13M6 3H18C18.2652 3 18.5196 3.10536 18.7071 3.29289C18.8946 3.48043 19 3.73478 19 4V20L16 18L13 20L10 18L7 20V4C7 3.73478 7.10536 3.48043 7.29289 3.29289C7.48043 3.10536 7.73478 3 8 3H6Z" stroke="currentColor" strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
