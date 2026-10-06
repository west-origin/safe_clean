// Figma: NoticeIcon/Box (2093:2101). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 상자. NoticeChip 에 icon="box" 로 고른다 (noticeIcons.js 표). 고객 앱 api/mock.js 예: "이삿짐이 아직 있어요" 칩.

/**
 * @param {object} props
 * @param {number} [props.size=16] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function NoticeIconBox({ size = 16, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M2.6001 5.2001L8.0001 2.6001L13.4001 5.2001V10.8001L8.0001 13.4001L2.6001 10.8001V5.2001Z" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M2.6001 5.2002L8.0001 7.8002M8.0001 7.8002L13.4001 5.2002M8.0001 7.8002V13.4002" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
