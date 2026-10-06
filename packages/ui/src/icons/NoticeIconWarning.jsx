// Figma: NoticeIcon/Warning (2093:2092). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 삼각형 안 느낌표(주의). NoticeChip 에 icon="warning" 으로 고른다 (noticeIcons.js 표). 고객 앱 api/mock.js 예: "특별히 주의할 공간이 있어요" 칩.

/**
 * @param {object} props
 * @param {number} [props.size=16] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function NoticeIconWarning({ size = 16, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M8 2.7998L14 12.5998H2L8 2.7998Z" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M8 6.6001V9.6001M8 11.2001H8.01" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
