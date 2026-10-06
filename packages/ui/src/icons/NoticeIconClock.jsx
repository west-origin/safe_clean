// Figma: NoticeIcon/Clock (2093:2116). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 시계. NoticeChip 에 icon="clock" 으로 고른다 (noticeIcons.js 표). 고객 앱 api/mock.js 예: "주말만 가능해요" 칩.

/**
 * @param {object} props
 * @param {number} [props.size=16] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function NoticeIconClock({ size = 16, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M8.0001 13.4001C10.9824 13.4001 13.4001 10.9824 13.4001 8.0001C13.4001 5.01776 10.9824 2.6001 8.0001 2.6001C5.01776 2.6001 2.6001 5.01776 2.6001 8.0001C2.6001 10.9824 5.01776 13.4001 8.0001 13.4001Z" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M8 4.7998V7.9998L10.2 9.3998" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
