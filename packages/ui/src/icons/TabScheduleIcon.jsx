// Figma: Manager/TabBar › Icon/Schedule (2109:2217). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 모양: 달력. 쓰는 곳(예): 매니저 앱 ManagerTabBar 의 "일정" 탭 (ManagerTabBar/tabs.js 표).
// ManagerTabBar 가 활성 탭이면 strokeWidth 2, 아니면 1.5 를 넘긴다.

/**
 * @param {object} props
 * @param {number} [props.size=24] 한 변 크기(px)
 * @param {number} [props.strokeWidth=2] 활성 2 / 비활성 1.5 (Figma 탭바 기준)
 * @param {string} [props.className]
 */
export default function TabScheduleIcon({ size = 24, strokeWidth = 2, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M3 8H21M7 3V7M17 3V7M5 5H19C19.5304 5 20.0391 5.21071 20.4142 5.58579C20.7893 5.96086 21 6.46957 21 7V19C21 19.5304 20.7893 20.0391 20.4142 20.4142C20.0391 20.7893 19.5304 21 19 21H5C4.46957 21 3.96086 20.7893 3.58579 20.4142C3.21071 20.0391 3 19.5304 3 19V7C3 6.46957 3.21071 5.96086 3.58579 5.58579C3.96086 5.21071 4.46957 5 5 5Z" stroke="currentColor" strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
