// Figma: Manager/TabBar › Icon/MY (2109:2225). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 모양: 사람. 쓰는 곳(예): 매니저 앱 ManagerTabBar 의 "MY" 탭 (ManagerTabBar/tabs.js 표).
// ManagerTabBar 가 활성 탭이면 strokeWidth 2, 아니면 1.5 를 넘긴다.

/**
 * @param {object} props
 * @param {number} [props.size=24] 한 변 크기(px)
 * @param {number} [props.strokeWidth=2] 활성 2 / 비활성 1.5 (Figma 탭바 기준)
 * @param {string} [props.className]
 */
export default function TabMyIcon({ size = 24, strokeWidth = 2, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M4 21C4 17 7.5 14 12 14C16.5 14 20 17 20 21M12 12C13.0609 12 14.0783 11.5786 14.8284 10.8284C15.5786 10.0783 16 9.06087 16 8C16 6.93913 15.5786 5.92172 14.8284 5.17157C14.0783 4.42143 13.0609 4 12 4C10.9391 4 9.92172 4.42143 9.17157 5.17157C8.42143 5.92172 8 6.93913 8 8C8 9.06087 8.42143 10.0783 9.17157 10.8284C9.92172 11.5786 10.9391 12 12 12Z" stroke="currentColor" strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
