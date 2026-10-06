// Figma: NoticeIcon/Baby (2093:2069). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 아기 얼굴. NoticeChip 에 icon="baby" 로 고른다 (noticeIcons.js 표). 고객 앱 api/mock.js 예: "영유아가 있어요" 칩.

/**
 * @param {object} props
 * @param {number} [props.size=16] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function NoticeIconBaby({ size = 16, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M8.0001 13.4001C10.9824 13.4001 13.4001 10.9824 13.4001 8.0001C13.4001 5.01776 10.9824 2.6001 8.0001 2.6001C5.01776 2.6001 2.6001 5.01776 2.6001 8.0001C2.6001 10.9824 5.01776 13.4001 8.0001 13.4001Z" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M6 7.2002H6.01M10 7.2002H10.01" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M6.19995 10C6.69995 10.6 7.29995 10.9 7.99995 10.9C8.69995 10.9 9.29995 10.6 9.79995 10" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
