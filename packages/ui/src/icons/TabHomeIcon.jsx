// Figma: Manager/TabBar › Icon/Home (2109:2209). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 모양: 집. 쓰는 곳(예): 매니저 앱 ManagerTabBar 의 "홈" 탭 (ManagerTabBar/tabs.js 표). 고객 앱 탭은 CustomerTabHomeIcon.
// ManagerTabBar 가 활성 탭이면 strokeWidth 2, 아니면 1.5 를 넘긴다.

/**
 * @param {object} props
 * @param {number} [props.size=24] 한 변 크기(px)
 * @param {number} [props.strokeWidth=2] 활성 2 / 비활성 1.5 (Figma 탭바 기준)
 * @param {string} [props.className]
 */
export default function TabHomeIcon({ size = 24, strokeWidth = 2, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M3 10.5L12 3L21 10.5M5 9.5V20C5 20.2652 5.10536 20.5196 5.29289 20.7071C5.48043 20.8946 5.73478 21 6 21H10V15H14V21H18C18.2652 21 18.5196 20.8946 18.7071 20.7071C18.8946 20.5196 19 20.2652 19 20V9.5" stroke="currentColor" strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
