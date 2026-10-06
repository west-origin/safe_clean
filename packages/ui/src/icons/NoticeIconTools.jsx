// Figma: NoticeIcon/Tools (2093:2088). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 공구. NoticeChip 에 icon="tools" 로 고른다 (noticeIcons.js 표). 고객 앱 api/mock.js 예: "리모델링 직후예요" 칩.

/**
 * @param {object} props
 * @param {number} [props.size=16] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function NoticeIconTools({ size = 16, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M3 12.9998L8.2 7.7998" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M9.4001 2.8999C9.17552 3.43146 9.11463 4.01792 9.22521 4.58428C9.33579 5.15063 9.61282 5.67111 10.0209 6.07915C10.4289 6.48718 10.9494 6.76421 11.5157 6.87479C12.0821 6.98537 12.6685 6.92448 13.2001 6.6999L11.2001 8.6999L9.4001 6.8999L7.6001 5.0999L9.4001 2.8999Z" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
