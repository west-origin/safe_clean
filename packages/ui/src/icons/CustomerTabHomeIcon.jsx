// Figma: TabBar › Icon/Home (2011:188). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 모양: 집. 쓰는 곳(예): 고객 앱 TabBar 의 "홈" 탭 (TabBar/tabs.js 표). 매니저 앱 탭은 TabHomeIcon 을 쓴다.

/**
 * @param {object} props
 * @param {number} [props.size=30] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function CustomerTabHomeIcon({ size = 30, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 30 30" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M7.5 23.75H11.25V16.25H18.75V23.75H22.5V12.5L15 6.875L7.5 12.5V23.75ZM5 26.25V11.25L15 3.75L25 11.25V26.25H16.25V18.75H13.75V26.25H5Z" fill="currentColor" />
    </svg>
  );
}
