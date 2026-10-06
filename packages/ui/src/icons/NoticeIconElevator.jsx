// Figma: NoticeIcon/Elevator (2093:2084). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 엘리베이터(문 + 위아래 화살표). NoticeChip 에 icon="elevator" 로 고른다 (noticeIcons.js 표). 고객 앱 api/mock.js 예: "엘리베이터 없음" 칩.

/**
 * @param {object} props
 * @param {number} [props.size=16] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function NoticeIconElevator({ size = 16, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M11.4 2.6001H4.59995C3.82675 2.6001 3.19995 3.2269 3.19995 4.0001V12.0001C3.19995 12.7733 3.82675 13.4001 4.59995 13.4001H11.4C12.1732 13.4001 12.8 12.7733 12.8 12.0001V4.0001C12.8 3.2269 12.1732 2.6001 11.4 2.6001Z" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M8.0001 2.6001V13.4001M5.6001 6.4001L4.6001 5.2001L3.6001 6.4001M10.4001 9.6001L11.4001 10.8001L12.4001 9.6001" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
