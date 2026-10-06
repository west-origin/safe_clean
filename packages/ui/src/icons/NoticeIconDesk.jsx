// Figma: NoticeIcon/Desk (2093:2124). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 책상. NoticeChip 에 icon="desk" 로 고른다 (noticeIcons.js 표). 고객 앱 api/mock.js 예: "개인 자리는 제외해주세요" 칩.

/**
 * @param {object} props
 * @param {number} [props.size=16] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function NoticeIconDesk({ size = 16, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M2.3999 6.6001H13.5999M3.5999 6.6001V13.0001M12.3999 6.6001V13.0001" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M10.2 2.7998H5.80005C5.24776 2.7998 4.80005 3.24752 4.80005 3.7998V5.5998C4.80005 6.15209 5.24776 6.5998 5.80005 6.5998H10.2C10.7523 6.5998 11.2 6.15209 11.2 5.5998V3.7998C11.2 3.24752 10.7523 2.7998 10.2 2.7998Z" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
