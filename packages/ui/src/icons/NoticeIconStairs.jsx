// Figma: NoticeIcon/Stairs (2093:2080). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 계단. NoticeChip 에 icon="stairs" 로 고른다 (noticeIcons.js 표). 고객 앱 api/mock.js 예: "계단 이용이 필요해요" 칩.

/**
 * @param {object} props
 * @param {number} [props.size=16] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function NoticeIconStairs({ size = 16, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M2.3999 12.9999H5.5999V9.7999H8.7999V6.5999H11.9999V3.3999H13.5999" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
