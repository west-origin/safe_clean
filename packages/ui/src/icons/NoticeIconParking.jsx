// Figma: NoticeIcon/Parking (2093:2109). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 네모 안 P(주차). NoticeChip 에 icon="parking" 으로 고른다 (noticeIcons.js 표). 고객 앱 api/mock.js 예: "주차 공간이 없어요" 칩.

/**
 * @param {object} props
 * @param {number} [props.size=16] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function NoticeIconParking({ size = 16, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M11.2001 2.6001H4.8001C3.58507 2.6001 2.6001 3.58507 2.6001 4.8001V11.2001C2.6001 12.4151 3.58507 13.4001 4.8001 13.4001H11.2001C12.4151 13.4001 13.4001 12.4151 13.4001 11.2001V4.8001C13.4001 3.58507 12.4151 2.6001 11.2001 2.6001Z" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M6.3999 10.9999V5.3999H8.5999C9.10381 5.3999 9.58709 5.60008 9.9434 5.9564C10.2997 6.31272 10.4999 6.79599 10.4999 7.2999C10.4999 7.80381 10.2997 8.28709 9.9434 8.64341C9.58709 8.99972 9.10381 9.1999 8.5999 9.1999H6.3999" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
