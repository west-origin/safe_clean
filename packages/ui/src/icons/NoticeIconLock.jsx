// Figma: NoticeIcon/Lock (2093:2077). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 자물쇠. NoticeChip 에 icon="lock" 으로 고른다 (noticeIcons.js 표). 고객 앱 api/mock.js 예: "귀중품이 있어요" 칩.

/**
 * @param {object} props
 * @param {number} [props.size=16] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function NoticeIconLock({ size = 16, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M10.9999 7H4.9999C4.11625 7 3.3999 7.71634 3.3999 8.6V11.6C3.3999 12.4837 4.11625 13.2 4.9999 13.2H10.9999C11.8836 13.2 12.5999 12.4837 12.5999 11.6V8.6C12.5999 7.71634 11.8836 7 10.9999 7Z" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M5.6001 7V5.4C5.6001 4.76348 5.85295 4.15303 6.30304 3.70294C6.75313 3.25286 7.36358 3 8.0001 3C8.63662 3 9.24707 3.25286 9.69715 3.70294C10.1472 4.15303 10.4001 4.76348 10.4001 5.4V7" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
