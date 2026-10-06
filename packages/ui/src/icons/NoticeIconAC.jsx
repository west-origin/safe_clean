// Figma: NoticeIcon/AC (2093:2133). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 에어컨. NoticeChip 에 icon="ac" 로 고른다 (noticeIcons.js 표). 고객 앱 api/mock.js 예: "벽걸이형이에요" 칩.

/**
 * @param {object} props
 * @param {number} [props.size=16] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function NoticeIconAC({ size = 16, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M11.9999 3.3999H3.9999C3.11625 3.3999 2.3999 4.11625 2.3999 4.9999V6.3999C2.3999 7.28356 3.11625 7.9999 3.9999 7.9999H11.9999C12.8836 7.9999 13.5999 7.28356 13.5999 6.3999V4.9999C13.5999 4.11625 12.8836 3.3999 11.9999 3.3999Z" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M4.80005 10.3999C5.70005 10.3999 5.70005 11.7999 6.60005 11.7999M9.40005 10.3999C10.3 10.3999 10.3 11.7999 11.2 11.7999" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
