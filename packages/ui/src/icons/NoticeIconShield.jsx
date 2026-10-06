// Figma: NoticeIcon/Shield (2093:2120). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 방패 + 체크. NoticeChip 에 icon="shield" 로 고른다 (noticeIcons.js 표). 고객 앱 api/mock.js 예: "보증기간 중이에요" 칩.

/**
 * @param {object} props
 * @param {number} [props.size=16] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function NoticeIconShield({ size = 16, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M8 2.3999L13 4.1999V7.7999C13 10.7999 10.9 12.7999 8 13.5999C5.1 12.7999 3 10.7999 3 7.7999V4.1999L8 2.3999Z" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M5.8999 8.1002L7.3999 9.6002L10.1999 6.7002" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
