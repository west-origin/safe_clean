// Figma: NoticeIcon/Building (2093:2105). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 건물. NoticeChip 에 icon="building" 으로 고른다 (noticeIcons.js 표). 고객 앱 api/mock.js 예: "신축 건물이에요" 칩.

/**
 * @param {object} props
 * @param {number} [props.size=16] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function NoticeIconBuilding({ size = 16, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M11.3999 2.6001H4.5999C3.93716 2.6001 3.3999 3.13736 3.3999 3.8001V12.2001C3.3999 12.8628 3.93716 13.4001 4.5999 13.4001H11.3999C12.0626 13.4001 12.5999 12.8628 12.5999 12.2001V3.8001C12.5999 3.13736 12.0626 2.6001 11.3999 2.6001Z" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M6 5.3999H6.01M10 5.3999H10.01M6 7.9999H6.01M10 7.9999H10.01M8 13.3999V10.7999" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
